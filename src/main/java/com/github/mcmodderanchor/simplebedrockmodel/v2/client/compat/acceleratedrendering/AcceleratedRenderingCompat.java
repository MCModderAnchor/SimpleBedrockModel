package com.github.mcmodderanchor.simplebedrockmodel.v2.client.compat.acceleratedrendering;

import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedGeometryChunk;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.TreeBoneDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;



@OnlyIn(Dist.CLIENT)
public final class AcceleratedRenderingCompat {
    private AcceleratedRenderingCompat() {
    }

    public static boolean renderQuads(BakedGeometryChunk chunk, VertexConsumer consumer, PoseStack.Pose pose,
                                      int lightmap, int overlay, float red, float green, float blue, float alpha) {
        return com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.acceleratedrendering.AcceleratedRenderingCompat.isLoaded()
                && BackendHolder.RENDERER.renderQuads(chunk, consumer, pose, lightmap, overlay, red, green, blue, alpha);
    }

    public static boolean renderVertices(BakedGeometryChunk chunk, VertexConsumer consumer, PoseStack.Pose pose,
                                         int lightmap, int overlay, float red, float green, float blue, float alpha) {
        return com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.acceleratedrendering.AcceleratedRenderingCompat.isLoaded()
                && BackendHolder.RENDERER.renderVertices(chunk, consumer, pose, lightmap, overlay, red, green, blue, alpha);
    }

    public static boolean renderCubes(TreeBoneDefinition bone, VertexConsumer consumer, PoseStack.Pose pose,
                                      int lightmap, int overlay, float red, float green, float blue, float alpha) {
        return com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.acceleratedrendering.AcceleratedRenderingCompat.isLoaded()
                && BackendHolder.RENDERER.renderCubes(bone, consumer, pose, lightmap, overlay, red, green, blue, alpha);
    }

    public static boolean renderPolyMeshes(TreeBoneDefinition bone, VertexConsumer consumer, PoseStack.Pose pose,
                                           int lightmap, int overlay, float red, float green, float blue, float alpha) {
        return com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.acceleratedrendering.AcceleratedRenderingCompat.isLoaded()
                && BackendHolder.RENDERER.renderPolyMeshes(bone, consumer, pose, lightmap, overlay, red, green, blue, alpha);
    }

    private static final class BackendHolder {
        private static final AcceleratedRenderer RENDERER = new AcceleratedRenderer();
    }
}
