package com.github.mcmodderanchor.simplebedrockmodel.v2.client.compat.sodium;

import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedGeometryChunk;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.ICube;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.PolyMesh;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class SodiumCompat {
    private SodiumCompat() {
    }

    public static boolean writeQuads(BakedGeometryChunk chunk, VertexConsumer consumer, int lightmap, int overlay,
                                     float red, float green, float blue, float alpha, Matrix4f finalPose, Matrix3f finalNormal) {
        return writeQuads(chunk, consumer, lightmap, overlay, red, green, blue, alpha, finalPose, finalNormal, false);
    }

    public static boolean writeQuads(BakedGeometryChunk chunk, VertexConsumer consumer, int lightmap, int overlay,
                                     float red, float green, float blue, float alpha, Matrix4f finalPose, Matrix3f finalNormal,
                                     boolean skipNormalVisibilityCull) {
        return com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.sodium.SodiumCompat.isSodiumInstalled()
                && BackendHolder.WRITER.writeQuads(chunk, consumer, lightmap, overlay, red, green, blue, alpha, finalPose, finalNormal, skipNormalVisibilityCull);
    }

    public static boolean writeVertices(BakedGeometryChunk chunk, VertexConsumer consumer, int lightmap, int overlay,
                                        float red, float green, float blue, float alpha, Matrix4f finalPose, Matrix3f finalNormal) {
        return com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.sodium.SodiumCompat.isSodiumInstalled()
                && BackendHolder.WRITER.writeVertices(chunk, consumer, lightmap, overlay, red, green, blue, alpha, finalPose, finalNormal);
    }

    public static boolean writeCubes(ICube[] cubes, VertexConsumer consumer, int lightmap, int overlay,
                                     float red, float green, float blue, float alpha, Matrix4f finalPose, Matrix3f finalNormal) {
        return writeCubes(cubes, consumer, lightmap, overlay, red, green, blue, alpha, finalPose, finalNormal, false);
    }

    public static boolean writeCubes(ICube[] cubes, VertexConsumer consumer, int lightmap, int overlay,
                                     float red, float green, float blue, float alpha, Matrix4f finalPose, Matrix3f finalNormal,
                                     boolean skipNormalVisibilityCull) {
        return com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.sodium.SodiumCompat.isSodiumInstalled()
                && BackendHolder.TREE_WRITER.writeCubes(cubes, consumer, lightmap, overlay, red, green, blue, alpha, finalPose, finalNormal, skipNormalVisibilityCull);
    }

    public static boolean writePolyMeshes(PolyMesh[] polyMeshes, VertexConsumer consumer, int lightmap, int overlay,
                                          float red, float green, float blue, float alpha, Matrix4f finalPose, Matrix3f finalNormal) {
        return com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.sodium.SodiumCompat.isSodiumInstalled()
                && BackendHolder.TREE_WRITER.writePolyMeshes(polyMeshes, consumer, lightmap, overlay, red, green, blue, alpha, finalPose, finalNormal);
    }

    private static final class BackendHolder {
        private static final SodiumBakedChunkWriter WRITER = new SodiumBakedChunkWriter();
        private static final SodiumTreeGeometryWriter TREE_WRITER = new SodiumTreeGeometryWriter();
    }
}
