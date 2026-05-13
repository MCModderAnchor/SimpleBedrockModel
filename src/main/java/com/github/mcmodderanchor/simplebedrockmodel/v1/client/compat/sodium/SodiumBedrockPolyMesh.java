package com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.sodium;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockBone;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockPolyMesh;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.PolyMeshItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.client.render.vertex.VertexConsumerUtils;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class SodiumBedrockPolyMesh extends BedrockPolyMesh implements ISodiumVertexWriter {
    public SodiumBedrockPolyMesh(PolyMeshItem polyMesh, BedrockBone part, float texWidth, float texHeight) {
        super(polyMesh, part, texWidth, texHeight);
    }

    @Override
    public void compileTriangles(PoseStack.Pose pose, VertexConsumer consumer, int lightmap, int overlay, float red, float green, float blue, float alpha) {
        VertexBufferWriter writer = VertexConsumerUtils.convertOrLog(consumer);
        if (writer == null) {
            super.compileTriangles(pose, consumer, lightmap, overlay, red, green, blue, alpha);
            return;
        }

        Matrix4f positionMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        int color = (int) (alpha * 255.0f) << 24 | (int) (blue * 255.0f) << 16 | (int) (green * 255.0f) << 8 | (int) (red * 255.0f);

        int vertexCount = 0;
        long ptr = SCRATCH_BUFFER;
        for (Triangle triangle : triangles) {
            for (Vertex vertex : triangle.vertices()) {
                if (vertexCount == SIZE) {
                    flush(writer, vertexCount);
                    vertexCount = 0;
                    ptr = SCRATCH_BUFFER;
                }

                float x = positionMatrix.m00() * vertex.x() + positionMatrix.m10() * vertex.y() + positionMatrix.m20() * vertex.z() + positionMatrix.m30();
                float y = positionMatrix.m01() * vertex.x() + positionMatrix.m11() * vertex.y() + positionMatrix.m21() * vertex.z() + positionMatrix.m31();
                float z = positionMatrix.m02() * vertex.x() + positionMatrix.m12() * vertex.y() + positionMatrix.m22() * vertex.z() + positionMatrix.m32();

                float nx = normalMatrix.m00() * vertex.nx() + normalMatrix.m10() * vertex.ny() + normalMatrix.m20() * vertex.nz();
                float ny = normalMatrix.m01() * vertex.nx() + normalMatrix.m11() * vertex.ny() + normalMatrix.m21() * vertex.nz();
                float nz = normalMatrix.m02() * vertex.nx() + normalMatrix.m12() * vertex.ny() + normalMatrix.m22() * vertex.nz();
                float normalLengthSquared = nx * nx + ny * ny + nz * nz;
                if (normalLengthSquared > 1.0E-12f) {
                    float invLength = 1.0f / (float) Math.sqrt(normalLengthSquared);
                    nx *= invLength;
                    ny *= invLength;
                    nz *= invLength;
                }

                emitVertex(ptr, x, y, z, color, vertex.u(), vertex.v(), overlay, lightmap, packNormal(nx, ny, nz));
                ptr += STRIDE;
                vertexCount++;
            }
        }

        if (vertexCount > 0) {
            flush(writer, vertexCount);
        }
    }
}
