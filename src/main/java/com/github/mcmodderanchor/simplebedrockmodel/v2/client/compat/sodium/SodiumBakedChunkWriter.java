package com.github.mcmodderanchor.simplebedrockmodel.v2.client.compat.sodium;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.sodium.ISodiumVertexWriter;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedGeometryChunk;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedQuadData;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedVertexData;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.EntityVertex;
import net.caffeinemc.mods.sodium.client.render.vertex.VertexConsumerUtils;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public class SodiumBakedChunkWriter implements ISodiumVertexWriter, ChunkVertexWriter {
    private static final int MAX_VERTICES_PER_BATCH = 256;
    private static final long SCRATCH = MemoryUtil.nmemAlignedAlloc(64, (long) MAX_VERTICES_PER_BATCH * STRIDE);

    public boolean writeQuads(BakedGeometryChunk chunk, VertexConsumer consumer, int lightmap, int overlay, float red, float green, float blue, float alpha,
                              Matrix4f finalPose, Matrix3f finalNormal) {
        return writeQuads(chunk, consumer, lightmap, overlay, red, green, blue, alpha, finalPose, finalNormal, false);
    }

    public boolean writeQuads(BakedGeometryChunk chunk, VertexConsumer consumer, int lightmap, int overlay, float red, float green, float blue, float alpha,
                              Matrix4f finalPose, Matrix3f finalNormal, boolean skipNormalVisibilityCull) {
        VertexBufferWriter writer = VertexConsumerUtils.convertOrLog(consumer);
        if (writer == null) {
            return false;
        }
        return writeQuads(chunk, writer, lightmap, overlay, red, green, blue, alpha, finalPose, finalNormal, skipNormalVisibilityCull);
    }

    public boolean writeVertices(BakedGeometryChunk chunk, VertexConsumer consumer, int lightmap, int overlay, float red, float green, float blue, float alpha,
                                 Matrix4f finalPose, Matrix3f finalNormal) {
        VertexBufferWriter writer = VertexConsumerUtils.convertOrLog(consumer);
        if (writer == null) {
            return false;
        }
        return writeVertices(chunk, writer, lightmap, overlay, red, green, blue, alpha, finalPose, finalNormal);
    }

    private boolean writeQuads(BakedGeometryChunk chunk, VertexBufferWriter writer, int lightmap, int overlay, float red, float green, float blue, float alpha,
                               Matrix4f finalPose, Matrix3f finalNormal, boolean skipNormalVisibilityCull) {
        int color = (int) (alpha * 255.0f) << 24 | (int) (blue * 255.0f) << 16 | (int) (green * 255.0f) << 8 | (int) (red * 255.0f);
        float p00 = finalPose.m00(), p01 = finalPose.m01(), p02 = finalPose.m02();
        float p10 = finalPose.m10(), p11 = finalPose.m11(), p12 = finalPose.m12();
        float p20 = finalPose.m20(), p21 = finalPose.m21(), p22 = finalPose.m22();
        float p30 = finalPose.m30(), p31 = finalPose.m31(), p32 = finalPose.m32();
        float n00 = finalNormal.m00(), n01 = finalNormal.m01(), n02 = finalNormal.m02();
        float n10 = finalNormal.m10(), n11 = finalNormal.m11(), n12 = finalNormal.m12();
        float n20 = finalNormal.m20(), n21 = finalNormal.m21(), n22 = finalNormal.m22();
        BakedQuadData quads = chunk.quads();
        float[] positions = quads.positions();
        float[] normals = quads.normals();
        float[] uvs = quads.uvs();
        boolean cull = !skipNormalVisibilityCull && RenderSystem.getModelViewMatrix().m32() == 0;
        int emitted = 0;
        long ptr = SCRATCH;
        for (int i = 0; i < quads.quadCount(); i++) {
            int pb = i * BakedQuadData.POSITION_STRIDE;
            int nb = i * BakedQuadData.NORMAL_STRIDE;
            int ub = i * BakedQuadData.UV_STRIDE;
            float nx = normals[nb];
            float ny = normals[nb + 1];
            float nz = normals[nb + 2];
            float tnx = n00 * nx + n10 * ny + n20 * nz;
            float tny = n01 * nx + n11 * ny + n21 * nz;
            float tnz = n02 * nx + n12 * ny + n22 * nz;
            if (cull && shouldCullQuad(positions, pb, p00, p01, p02, p10, p11, p12, p20, p21, p22, p30, p31, p32, tnx, tny, tnz)) {
                continue;
            }
            int packedNormal = packNormal(tnx, tny, tnz);
            emitVertex(ptr, transformX(positions[pb], positions[pb + 1], positions[pb + 2], p00, p10, p20, p30), transformY(positions[pb], positions[pb + 1], positions[pb + 2], p01, p11, p21, p31), transformZ(positions[pb], positions[pb + 1], positions[pb + 2], p02, p12, p22, p32), color, uvs[ub], uvs[ub + 1], overlay, lightmap, packedNormal);
            ptr += STRIDE;
            emitVertex(ptr, transformX(positions[pb + 3], positions[pb + 4], positions[pb + 5], p00, p10, p20, p30), transformY(positions[pb + 3], positions[pb + 4], positions[pb + 5], p01, p11, p21, p31), transformZ(positions[pb + 3], positions[pb + 4], positions[pb + 5], p02, p12, p22, p32), color, uvs[ub + 2], uvs[ub + 3], overlay, lightmap, packedNormal);
            ptr += STRIDE;
            emitVertex(ptr, transformX(positions[pb + 6], positions[pb + 7], positions[pb + 8], p00, p10, p20, p30), transformY(positions[pb + 6], positions[pb + 7], positions[pb + 8], p01, p11, p21, p31), transformZ(positions[pb + 6], positions[pb + 7], positions[pb + 8], p02, p12, p22, p32), color, uvs[ub + 4], uvs[ub + 5], overlay, lightmap, packedNormal);
            ptr += STRIDE;
            emitVertex(ptr, transformX(positions[pb + 9], positions[pb + 10], positions[pb + 11], p00, p10, p20, p30), transformY(positions[pb + 9], positions[pb + 10], positions[pb + 11], p01, p11, p21, p31), transformZ(positions[pb + 9], positions[pb + 10], positions[pb + 11], p02, p12, p22, p32), color, uvs[ub + 6], uvs[ub + 7], overlay, lightmap, packedNormal);
            ptr += STRIDE;
            emitted += 4;
            if (emitted >= MAX_VERTICES_PER_BATCH) {
                flush(writer, emitted);
                emitted = 0;
                ptr = SCRATCH;
            }
        }
        if (emitted > 0) {
            flush(writer, emitted);
        }
        return true;
    }

    private boolean writeVertices(BakedGeometryChunk chunk, VertexBufferWriter writer, int lightmap, int overlay, float red, float green, float blue, float alpha,
                                  Matrix4f finalPose, Matrix3f finalNormal) {
        int color = (int) (alpha * 255.0f) << 24 | (int) (blue * 255.0f) << 16 | (int) (green * 255.0f) << 8 | (int) (red * 255.0f);
        BakedVertexData vertices = chunk.vertices();
        float[] positions = vertices.positions();
        float[] normals = vertices.normals();
        float[] uvs = vertices.uvs();
        int emitted = 0;
        long ptr = SCRATCH;
        for (int i = 0; i < vertices.vertexCount(); i++) {
            int pb = i * BakedVertexData.POSITION_STRIDE;
            int nb = i * BakedVertexData.NORMAL_STRIDE;
            int ub = i * BakedVertexData.UV_STRIDE;
            float tx = transformX(positions[pb], positions[pb + 1], positions[pb + 2], finalPose.m00(), finalPose.m10(), finalPose.m20(), finalPose.m30());
            float ty = transformY(positions[pb], positions[pb + 1], positions[pb + 2], finalPose.m01(), finalPose.m11(), finalPose.m21(), finalPose.m31());
            float tz = transformZ(positions[pb], positions[pb + 1], positions[pb + 2], finalPose.m02(), finalPose.m12(), finalPose.m22(), finalPose.m32());
            float tnx = finalNormal.m00() * normals[nb] + finalNormal.m10() * normals[nb + 1] + finalNormal.m20() * normals[nb + 2];
            float tny = finalNormal.m01() * normals[nb] + finalNormal.m11() * normals[nb + 1] + finalNormal.m21() * normals[nb + 2];
            float tnz = finalNormal.m02() * normals[nb] + finalNormal.m12() * normals[nb + 1] + finalNormal.m22() * normals[nb + 2];
            emitVertex(ptr, tx, ty, tz, color, uvs[ub], uvs[ub + 1], overlay, lightmap, packNormal(tnx, tny, tnz));
            ptr += STRIDE;
            emitted++;
            if (emitted >= MAX_VERTICES_PER_BATCH) {
                flush(writer, emitted);
                emitted = 0;
                ptr = SCRATCH;
            }
        }
        if (emitted > 0) {
            flush(writer, emitted);
        }
        return true;
    }

    private static float transformX(float x, float y, float z, float m00, float m10, float m20, float m30) {
        return m00 * x + m10 * y + m20 * z + m30;
    }

    private static float transformY(float x, float y, float z, float m01, float m11, float m21, float m31) {
        return m01 * x + m11 * y + m21 * z + m31;
    }

    private static float transformZ(float x, float y, float z, float m02, float m12, float m22, float m32) {
        return m02 * x + m12 * y + m22 * z + m32;
    }

    private static boolean shouldCullQuad(float[] positions, int positionBase,
                                          float p00, float p01, float p02,
                                          float p10, float p11, float p12,
                                          float p20, float p21, float p22,
                                          float p30, float p31, float p32,
                                          float nx, float ny, float nz) {
        float cx = positions[positionBase] + positions[positionBase + 6];
        float cy = positions[positionBase + 1] + positions[positionBase + 7];
        float cz = positions[positionBase + 2] + positions[positionBase + 8];
        float tx = p00 * cx + p10 * cy + p20 * cz + p30 + p30;
        float ty = p01 * cx + p11 * cy + p21 * cz + p31 + p31;
        float tz = p02 * cx + p12 * cy + p22 * cz + p32 + p32;
        return tx * nx + ty * ny + tz * nz > 0;
    }

    @Override
    public void flush(VertexBufferWriter writer, int vertexCount) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            writer.push(stack, SCRATCH, vertexCount, EntityVertex.FORMAT);
        }
    }
}
