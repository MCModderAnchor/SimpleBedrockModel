package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.render;

import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleDescription;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.ParticleAppearanceBillboard;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import org.jetbrains.annotations.Nullable;
import java.util.List;

/**
 * 粒子渲染器。将发射器中的局部空间粒子渲染到 PoseStack 局部坐标系中。
 * <p>
 * 世界空间粒子由原版 {@code ParticleEngine} 渲染，不经过此渲染器。
 */
@OnlyIn(Dist.CLIENT)
public final class ParticleRenderer {

    private ParticleRenderer() {}

    /**
     * 渲染一个发射器的所有局部空间粒子。
     * <p>
     * 发射器变换矩阵从 emitter 实例上读取。粒子坐标在发射器局部空间中，
     * 渲染时叠加发射器变换。
     *
     * @param cameraPitch    摄像机 pitch（弧度）
     * @param cameraRoll     摄像机 roll（弧度）
     * @param cameraRotation 摄像机旋转矩阵（世界对齐空间 → 视图空间），
     *                       用于 fpDetached 粒子。可为 null。
     */
    public static void render(ParticleEmitterInstance emitter, PoseStack poseStack,
                               MultiBufferSource bufferSource, int light, float partialTick,
                               float cameraPitch, float cameraRoll,
                               @Nullable Matrix4f cameraRotation) {
        List<ParticleInstance> particles = emitter.getParticles();
        if (particles.isEmpty()) return;

        var definition = emitter.getDefinition();
        ParticleDescription desc = definition.getDescription();
        ResourceLocation texture = desc.getTexture();

        // 选择 RenderType
        RenderType renderType = getRenderType(desc.getMaterial(), texture);
        VertexConsumer consumer = bufferSource.getBuffer(renderType);

        // 确定朝向模式
        ParticleAppearanceBillboard billboard = definition.findComponent(ParticleAppearanceBillboard.class);
        ParticleAppearanceBillboard.FaceCameraMode mode = billboard != null
                ? billboard.faceCameraMode()
                : ParticleAppearanceBillboard.FaceCameraMode.ROTATE_XYZ;

        Matrix4f emitterTransform = emitter.getEmitterTransform();
        boolean localPos = emitter.isLocalPosition();
        boolean localRot = emitter.isLocalRotation();

        for (ParticleInstance particle : particles) {
            BillboardHelper.renderBillboard(particle, poseStack, consumer, light, mode,
                    emitterTransform, localPos, localRot, cameraPitch, cameraRoll, cameraRotation);
        }
    }

    /**
     * 根据材质类型选择 RenderType。
     */
    private static RenderType getRenderType(ParticleDescription.Material material, ResourceLocation texture) {
        return switch (material) {
            case PARTICLES_OPAQUE -> RenderType.entityCutout(texture);
            case PARTICLES_ALPHA -> RenderType.entityTranslucent(texture);
            case PARTICLES_BLEND -> RenderType.entityTranslucent(texture);
            case PARTICLES_ADD -> ParticleRenderType.additiveParticle(texture);
        };
    }
}
