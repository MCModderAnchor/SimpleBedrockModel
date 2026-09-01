package com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.acceleratedrendering;

import com.github.argon4w.acceleratedrendering.core.CoreFeature;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.IAcceleratedVertexConsumer;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.VertexConsumerExtension;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.renderers.IAcceleratedRenderer;
import com.github.argon4w.acceleratedrendering.features.entities.AcceleratedEntityRenderingFeature;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 第一人称粒子的 AR 加速渲染：把单个 billboard quad 以「局部坐标 + beginTransform」提交成 mesh，
 * 从而与枪体进入同一加速层 / 模板窗口（让粒子被镜内剔除）。
 * <p>
 * 参考 TACZ 的 {@code AcceleratedBeamRenderer}：几何以局部坐标写入，视图变换由 beginTransform 的
 * transform 完成。粒子的 RenderType（entityCutout/entityTranslucent/additive）与枪体同为 NEW_ENTITY
 * format，可直接进入同一加速 buffer。
 */
@OnlyIn(Dist.CLIENT)
final class AcceleratedParticleRenderer {

    private final IAcceleratedRenderer<ParticleInstance> billboardRenderer = this::renderBillboardAccelerated;

    /**
     * 尝试用 AR 加速渲染一个粒子 billboard。返回 true 表示已加速渲染，调用方应停止原路径绘制。
     */
    boolean renderBillboard(VertexConsumer consumer, ParticleInstance particle, Matrix4f effectivePose,
                            Matrix3f normal, int light) {
        IAcceleratedVertexConsumer extension = getExtension(consumer);
        if (!canRender(extension)) {
            return false;
        }
        extension.doRender(billboardRenderer, particle, effectivePose, normal, light, OverlayTexture.NO_OVERLAY, packColor(particle));
        return true;
    }

    private void renderBillboardAccelerated(VertexConsumer vertexConsumer, ParticleInstance particle,
                                            Matrix4f transform, Matrix3f normal, int light, int overlay, int color) {
        IAcceleratedVertexConsumer extension = VertexConsumerExtension.getAccelerated(vertexConsumer);

        float hw = particle.width * particle.spawnScale;
        float hh = particle.height * particle.spawnScale;
        float cx = particle.x, cy = particle.y, cz = particle.z;

        // 局部空间的轴（屏幕对齐 quad），经 beginTransform 的 transform 变换到视图空间即得正确朝向。
        float ax = 1f, ay = 0f, az = 0f;
        float bx = 0f, by = 1f, bz = 0f;

        // 粒子自旋（绕局部前轴）
        if (particle.rotation != 0f) {
            float rad = (float) Math.toRadians(particle.rotation);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            float tax = ax, tay = ay, taz = az;
            ax = tax * cos + bx * sin;
            ay = tay * cos + by * sin;
            az = taz * cos + bz * sin;
            bx = -tax * sin + bx * cos;
            by = -tay * sin + by * cos;
            bz = -taz * sin + bz * cos;
        }

        extension.beginTransform(transform, normal);

        vertex(vertexConsumer, cx - ax * hw - bx * hh, cy - ay * hw - by * hh, cz - az * hw - bz * hh,
                particle.u0, particle.v1, particle, light, overlay);
        vertex(vertexConsumer, cx - ax * hw + bx * hh, cy - ay * hw + by * hh, cz - az * hw + bz * hh,
                particle.u0, particle.v0, particle, light, overlay);
        vertex(vertexConsumer, cx + ax * hw + bx * hh, cy + ay * hw + by * hh, cz + az * hw + bz * hh,
                particle.u1, particle.v0, particle, light, overlay);
        vertex(vertexConsumer, cx + ax * hw - bx * hh, cy + ay * hw - by * hh, cz + az * hw - bz * hh,
                particle.u1, particle.v1, particle, light, overlay);

        extension.endTransform();
    }

    private void vertex(VertexConsumer consumer, float x, float y, float z,
                        float u, float v, ParticleInstance particle, int light, int overlay) {
        consumer.addVertex(x, y, z)
                .setColor(particle.r, particle.g, particle.b, particle.a)
                .setUv(u, v)
                .setOverlay(overlay)
                // 光照由调方按 particle_appearance_lighting 组件计算：无组件时传入 FULL_BRIGHT（自发光）
                .setLight(light)
                .setNormal(0f, 1f, 0f);
    }

    private IAcceleratedVertexConsumer getExtension(VertexConsumer consumer) {
        return VertexConsumerExtension.getAccelerated(consumer);
    }

    private boolean canRender(IAcceleratedVertexConsumer extension) {
        return AcceleratedEntityRenderingFeature.isEnabled()
                && AcceleratedEntityRenderingFeature.shouldUseAcceleratedPipeline()
                && (CoreFeature.isRenderingLevel()
                || (CoreFeature.isRenderingGui() && AcceleratedEntityRenderingFeature.shouldAccelerateInGui())
                || CoreFeature.isRenderingHand())
                && extension.isAccelerated();
    }

    private int packColor(ParticleInstance particle) {
        return ((int) (particle.a * 255.0f) << 24)
                | ((int) (particle.r * 255.0f) << 16)
                | ((int) (particle.g * 255.0f) << 8)
                | (int) (particle.b * 255.0f);
    }
}
