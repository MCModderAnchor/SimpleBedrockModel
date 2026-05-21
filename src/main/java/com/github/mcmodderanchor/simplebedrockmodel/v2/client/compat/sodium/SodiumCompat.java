package com.github.mcmodderanchor.simplebedrockmodel.v2.client.compat.sodium;

import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.bake.BakedGeometryChunk;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class SodiumCompat {
    private SodiumCompat() {
    }

    public static boolean writeQuads(BakedGeometryChunk chunk, VertexConsumer consumer, int lightmap, int overlay,
                                     float red, float green, float blue, float alpha, Matrix4f finalPose, Matrix3f finalNormal) {
        return com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.sodium.SodiumCompat.isSodiumInstalled()
                && BackendHolder.WRITER.writeQuads(chunk, consumer, lightmap, overlay, red, green, blue, alpha, finalPose, finalNormal);
    }

    public static boolean writeVertices(BakedGeometryChunk chunk, VertexConsumer consumer, int lightmap, int overlay,
                                        float red, float green, float blue, float alpha, Matrix4f finalPose, Matrix3f finalNormal) {
        return com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.sodium.SodiumCompat.isSodiumInstalled()
                && BackendHolder.WRITER.writeVertices(chunk, consumer, lightmap, overlay, red, green, blue, alpha, finalPose, finalNormal);
    }

    private static final class BackendHolder {
        private static final SodiumBakedChunkWriter WRITER = new SodiumBakedChunkWriter();
    }
}
