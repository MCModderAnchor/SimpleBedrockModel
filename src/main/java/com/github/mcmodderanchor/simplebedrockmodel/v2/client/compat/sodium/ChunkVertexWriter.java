package com.github.mcmodderanchor.simplebedrockmodel.v2.client.compat.sodium;

import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedGeometryChunk;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public interface ChunkVertexWriter {
    ChunkVertexWriter NOOP = new ChunkVertexWriter() {
        @Override
        public boolean writeQuads(BakedGeometryChunk chunk, VertexConsumer consumer, int lightmap, int overlay,
                                  float red, float green, float blue, float alpha, Matrix4f finalPose, Matrix3f finalNormal) {
            return false;
        }

        @Override
        public boolean writeVertices(BakedGeometryChunk chunk, VertexConsumer consumer, int lightmap, int overlay,
                                     float red, float green, float blue, float alpha, Matrix4f finalPose, Matrix3f finalNormal) {
            return false;
        }
    };

    boolean writeQuads(BakedGeometryChunk chunk, VertexConsumer consumer, int lightmap, int overlay,
                       float red, float green, float blue, float alpha, Matrix4f finalPose, Matrix3f finalNormal);

    boolean writeVertices(BakedGeometryChunk chunk, VertexConsumer consumer, int lightmap, int overlay,
                          float red, float green, float blue, float alpha, Matrix4f finalPose, Matrix3f finalNormal);
}
