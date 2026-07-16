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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
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
    // 发射器按手分组：主副手各一份。tick/render/getParticleCount 遍历两者（已生成粒子无条件渲染，
    // 基准 pose 在同一 pass 内一致），仅 stopEmitters 需按手定位。
    private final List<ParticleEmitterInstance> mainEmitters = new ArrayList<>();
    private final List<ParticleEmitterInstance> offEmitters = new ArrayList<>();
    private final ParticleMolangEnvironment molang = new ParticleMolangEnvironment();

    // 摄像机速度追踪（用于 velocity=true 时给世界空间粒子叠加摄像机速度）
    private double prevCamX, prevCamY, prevCamZ;
    private float cameraVx, cameraVy, cameraVz; // blocks/second
    private boolean hasPrevCam = false;

    private List<ParticleEmitterInstance> emittersFor(InteractionHand hand) {
        return hand == InteractionHand.OFF_HAND ? offEmitters : mainEmitters;
    }

    /**
     * 添加一个主手粒子效果发射器（兼容旧调用方，单手物品默认归主手）。
     *
     * @return 创建的发射器实例，可用于后续控制
     */
    public ParticleEmitterInstance addEmitter(ParticleEffectDefinition definition) {
        return addEmitter(definition, InteractionHand.MAIN_HAND);
    }

    /**
     * 添加一个粒子效果发射器并标记其归属手。
     * <p>
     * 发射器产出的世界空间粒子会自动投递到原版 ParticleEngine，
     * 局部空间粒子留在内部列表由本系统管理。归属手仅用于 {@link #stopEmitters(InteractionHand)}
     * 按手停止产出，不影响 tick / render（两手统一处理）。
     *
     * @return 创建的发射器实例，可用于后续控制
     */
    public ParticleEmitterInstance addEmitter(ParticleEffectDefinition definition, InteractionHand hand) {
        ParticleEmitterInstance emitter = new ParticleEmitterInstance(definition, molang);

        // 启用第一人称模式（检查 sbm:fp_emitter_local_space 组件）
        emitter.enableFPMode();

        // 设置世界空间粒子分流：worldSpace=true 的粒子投递到 ParticleEngine
        emitter.setWorldSpaceParticleCallback(particle ->
                deliverWorldSpaceParticle(particle, definition, emitter));

        emittersFor(hand).add(emitter);
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
            applyViewerOffset(mainEmitters, dx, dy, dz);
            applyViewerOffset(offEmitters, dx, dy, dz);
        }

        tickEmitters(mainEmitters, dt);
        tickEmitters(offEmitters, dt);
    }

    private static void applyViewerOffset(List<ParticleEmitterInstance> emitters, float dx, float dy, float dz) {
        for (ParticleEmitterInstance emitter : emitters) {
            emitter.applyViewerOffset(dx, dy, dz);
        }
    }

    private static void tickEmitters(List<ParticleEmitterInstance> emitters, float dt) {
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
    /**
     * 渲染指定手的局部空间粒子。在该手 pass 的 PoseStack 坐标系中绘制。
     * <p>
     * 必须按手渲染：每只手的 pass poseStack 基准不同（副手含镜像平移），而发射器的
     * {@code emitterTransform} 已烘入「该手基准 pose 的逆」，跨手渲染会导致基准不匹配而错位。
     * 世界空间粒子由原版 ParticleEngine 渲染，不经过此方法。
     *
     * @param cameraPitch      摄像机 pitch 角度（弧度）
     * @param cameraRoll       摄像机 roll 角度（弧度）
     * @param cameraRotation   摄像机旋转矩阵（世界对齐空间 → 视图空间），
     *                         用于 fpDetached 粒子的渲染。可为 null（无 fpDetached 粒子时）。
     */
    public void render(InteractionHand hand, PoseStack poseStack, MultiBufferSource bufferSource, int light, float partialTick,
                       float cameraPitch, float cameraRoll, @Nullable Matrix4f cameraRotation) {
        renderEmitters(emittersFor(hand), poseStack, bufferSource, light, partialTick, cameraPitch, cameraRoll, cameraRotation);
    }

    private static void renderEmitters(List<ParticleEmitterInstance> emitters, PoseStack poseStack,
                                       MultiBufferSource bufferSource, int light, float partialTick,
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
        mainEmitters.clear();
        offEmitters.clear();
        hasPrevCam = false;
    }

    /**
     * 停止指定手的所有发射器（标记 removed，不再产出新粒子）。
     * 已生成的粒子继续按各自生命周期 tick 直到自然消亡。
     */
    public void stopEmitters(InteractionHand hand) {
        for (ParticleEmitterInstance emitter : emittersFor(hand)) {
            emitter.setRemoved(true);
        }
    }

    /**
     * 获取当前活跃的局部空间粒子总数（不含已投递到 ParticleEngine 的世界空间粒子）。
     */
    public int getParticleCount() {
        int count = 0;
        for (ParticleEmitterInstance emitter : mainEmitters) {
            count += emitter.getParticles().size();
        }
        for (ParticleEmitterInstance emitter : offEmitters) {
            count += emitter.getParticles().size();
        }
        return count;
    }

    /**
     * 获取所有发射器（主副手合并）。供单手物品遍历使用。
     */
    public List<ParticleEmitterInstance> getEmitters() {
        List<ParticleEmitterInstance> all = new ArrayList<>(mainEmitters.size() + offEmitters.size());
        all.addAll(mainEmitters);
        all.addAll(offEmitters);
        return all;
    }

    public ParticleMolangEnvironment getMolangEnvironment() {
        return molang;
    }
}
