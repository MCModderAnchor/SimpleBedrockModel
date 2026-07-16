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

    private static final class BackendHolder {
        private static final AcceleratedRenderer RENDERED = new AcceleratedRenderer();
    }
}
