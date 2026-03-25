package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.firstperson;

import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleEffectDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.EmitterLocalSpace;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.render.ParticleRenderer;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleMolangEnvironment;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world.SnowStormParticle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 第一人称粒子系统管理器。持有一个或多个发射器实例，统一管理 tick 和渲染。
 * <p>
 * 局部空间粒子（{@code emitter_local_space.position=true}）留在内部列表，
 * 跟随模型空间渲染。世界空间粒子（{@code worldSpace=true}）在生成时立即投递到
 * 原版 {@code ParticleEngine}，由原版管线管理生命周期、渲染和碰撞。
 */
@OnlyIn(Dist.CLIENT)
public class FirstPersonParticleSystem {
    private final List<ParticleEmitterInstance> emitters = new ArrayList<>();
    private final ParticleMolangEnvironment molang = new ParticleMolangEnvironment();

    // 摄像机速度追踪（用于 velocity=true 时给世界空间粒子叠加摄像机速度）
    private double prevCamX, prevCamY, prevCamZ;
    private float cameraVx, cameraVy, cameraVz; // blocks/second
    private boolean hasPrevCam = false;

    /**
     * 添加一个粒子效果发射器。
     * <p>
     * 发射器产出的世界空间粒子会自动投递到原版 ParticleEngine，
     * 局部空间粒子留在内部列表由本系统管理。
     *
     * @return 创建的发射器实例，可用于后续控制
     */
    public ParticleEmitterInstance addEmitter(ParticleEffectDefinition definition) {
        ParticleEmitterInstance emitter = new ParticleEmitterInstance(definition, molang);

        // 启用第一人称模式（检查 sbm:fp_emitter_local_space 组件）
        emitter.enableFPMode();

        // 设置世界空间粒子分流：worldSpace=true 的粒子投递到 ParticleEngine
        emitter.setWorldSpaceParticleCallback(particle ->
                deliverWorldSpaceParticle(particle, definition, emitter));

        emitters.add(emitter);
        return emitter;
    }

    /**
     * 将世界空间粒子投递到原版 ParticleEngine。
     * <p>
     * 第一人称管线中，世界空间粒子的坐标在"以摄像机为原点的世界对齐空间"中，
     * 需要加上摄像机世界位置转换为真正的世界坐标。
     * 当 {@code emitter_local_space.velocity=true} 时，还需要叠加摄像机速度。
     */
    private void deliverWorldSpaceParticle(ParticleInstance particle,
                                            ParticleEffectDefinition definition,
                                            ParticleEmitterInstance emitter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.particleEngine == null) return;

        // 将坐标从"以摄像机为原点的世界对齐空间"转换为世界坐标
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        particle.x += (float) camPos.x;
        particle.y += (float) camPos.y;
        particle.z += (float) camPos.z;

        // velocity=true 时叠加摄像机（发射器）速度
        if (emitter.isLocalVelocity()) {
            particle.vx += cameraVx;
            particle.vy += cameraVy;
            particle.vz += cameraVz;
        }

        SnowStormParticle worldParticle = new SnowStormParticle(
                mc.level, particle, definition, emitter);
        mc.particleEngine.add(worldParticle);
    }

    /**
     * 更新所有发射器和粒子。
     * <p>
     * 仅更新局部空间粒子。世界空间粒子由原版 ParticleEngine 管理。
     *
     * @param dt 时间步长（秒）
     */
    public void tick(float dt) {
        // 追踪摄像机速度和位移
        updateCameraVelocity(dt);

        // 补偿摄像机位移，使 fpDetached 和 worldSpace 粒子在摄像机空间中保持世界固定
        if (hasPrevCam && dt > 0) {
            float dx = cameraVx * dt;
            float dy = cameraVy * dt;
            float dz = cameraVz * dt;
            for (ParticleEmitterInstance emitter : emitters) {
                emitter.applyViewerOffset(dx, dy, dz);
            }
        }

        for (int i = emitters.size() - 1; i >= 0; i--) {
            ParticleEmitterInstance emitter = emitters.get(i);
            emitter.tick(dt);
            if (emitter.isFinished()) {
                emitters.remove(i);
            }
        }
    }

    private void updateCameraVelocity(float dt) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();

        if (hasPrevCam && dt > 0) {
            cameraVx = (float) ((camPos.x - prevCamX) / dt);
            cameraVy = (float) ((camPos.y - prevCamY) / dt);
            cameraVz = (float) ((camPos.z - prevCamZ) / dt);
        } else {
            cameraVx = cameraVy = cameraVz = 0;
        }

        prevCamX = camPos.x;
        prevCamY = camPos.y;
        prevCamZ = camPos.z;
        hasPrevCam = true;
    }

    /**
     * 渲染所有局部空间粒子。在目标 PoseStack 坐标系中绘制。
     * <p>
     * 世界空间粒子由原版 ParticleEngine 渲染，不经过此方法。
     *
     * @param cameraPitch      摄像机 pitch 角度（弧度）
     * @param cameraRoll       摄像机 roll 角度（弧度）
     * @param cameraRotation   摄像机旋转矩阵（世界对齐空间 → 视图空间），
     *                         用于 fpDetached 粒子的渲染。可为 null（无 fpDetached 粒子时）。
     */
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int light, float partialTick,
                       float cameraPitch, float cameraRoll, @Nullable Matrix4f cameraRotation) {
        for (ParticleEmitterInstance emitter : emitters) {
            ParticleRenderer.render(emitter, poseStack, bufferSource, light, partialTick,
                    cameraPitch, cameraRoll, cameraRotation);
        }
    }

    /**
     * 移除所有发射器和粒子。
     */
    public void clear() {
        emitters.clear();
        hasPrevCam = false;
    }

    /**
     * 获取当前活跃的局部空间粒子总数（不含已投递到 ParticleEngine 的世界空间粒子）。
     */
    public int getParticleCount() {
        int count = 0;
        for (ParticleEmitterInstance emitter : emitters) {
            count += emitter.getParticles().size();
        }
        return count;
    }

    public List<ParticleEmitterInstance> getEmitters() {
        return emitters;
    }

    public ParticleMolangEnvironment getMolangEnvironment() {
        return molang;
    }
}
