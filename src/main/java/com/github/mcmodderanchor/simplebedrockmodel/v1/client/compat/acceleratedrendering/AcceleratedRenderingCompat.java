package com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.acceleratedrendering;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockBone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;

@OnlyIn(Dist.CLIENT)
public final class AcceleratedRenderingCompat {
    private static final boolean LOADED = ModList.get().isLoaded("acceleratedrendering");

    private AcceleratedRenderingCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static boolean renderCubes(BedrockBone bone, PoseStack.Pose pose, VertexConsumer consumer,
                                      int lightmap, int overlay, float red, float green, float blue, float alpha) {
        return LOADED && BackendHolder.RENDERED.renderCubes(bone, pose, consumer, lightmap, overlay, red, green, blue, alpha);
    }

    public static boolean renderMeshes(BedrockBone bone, PoseStack.Pose pose, VertexConsumer consumer,
                                       int lightmap, int overlay, float red, float green, float blue, float alpha) {
        return LOADED && BackendHolder.RENDERED.renderMeshes(bone, pose, consumer, lightmap, overlay, red, green, blue, alpha);
    }

    /**
     * 用 AR 加速渲染一个第一人称粒子 billboard（使粒子与枪体同加速层、被模板剔除）。
     * 返回 true 表示已走加速管线，调用方应停止原路径绘制。
     */
    public static boolean renderParticleBillboard(VertexConsumer consumer,
                                                  com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleInstance particle,
                                                  org.joml.Matrix4f effectivePose, org.joml.Matrix3f normal,
                                                  int light) {
        return LOADED && BackendHolder.PARTICLE_RENDER.renderBillboard(consumer, particle, effectivePose, normal, light);
    }

    private static final class BackendHolder {
        private static final AcceleratedRenderer RENDERED = new AcceleratedRenderer();
        private static final AcceleratedParticleRenderer PARTICLE_RENDER = new AcceleratedParticleRenderer();
    }
}
