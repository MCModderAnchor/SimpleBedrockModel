package com.github.mcmodderanchor.simplebedrockmodel.v2.client.compat.embeddium;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.embeddium.IEmbeddiumVertexWriter;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockCube;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.CubeBox;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.CubePerFace;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.ICube;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.PolyMesh;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.embeddedt.embeddium.api.vertex.buffer.VertexBufferWriter;
import org.embeddedt.embeddium.api.vertex.format.common.ModelVertex;
import org.embeddedt.embeddium.impl.render.vertex.VertexConsumerUtils;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public final class EmbeddiumTreeGeometryWriter implements IEmbeddiumVertexWriter {
    private static final int MAX_VERTICES_PER_BATCH = 256;
    private static final long SCRATCH = MemoryUtil.nmemAlignedAlloc(64, (long) MAX_VERTICES_PER_BATCH * STRIDE);
    private static final float[][] FACE_NORMALS = new float[][]{
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };
    private static final float[] CUBE_VERTICES = new float[24];
    private static final Matrix4f CUBE_POSE = new Matrix4f();
    private static final Matrix3f CUBE_NORMAL_POSE = new Matrix3f();

    public boolean writeCubes(ICube[] cubes, VertexConsumer consumer, int lightmap, int overlay, float red, float green, float blue, float alpha,
                              Matrix4f finalPose, Matrix3f finalNormal) {
        return writeCubes(cubes, consumer, lightmap, overlay, red, green, blue, alpha, finalPose, finalNormal, false);
    }

    public boolean writeCubes(ICube[] cubes, VertexConsumer consumer, int lightmap, int overlay, float red, float green, float blue, float alpha,
                              Matrix4f finalPose, Matrix3f finalNormal, boolean skipNormalVisibilityCull) {
        VertexBufferWriter writer = VertexConsumerUtils.convertOrLog(consumer);
        if (writer == null) return false;
        return writeCubes(cubes, writer, lightmap, overlay, red, green, blue, alpha, finalPose, finalNormal, skipNormalVisibilityCull);
    }

    public boolean writePolyMeshes(PolyMesh[] polyMeshes, VertexConsumer consumer, int lightmap, int overlay, float red, float green, float blue, float alpha,
                                   Matrix4f finalPose, Matrix3f finalNormal) {
        VertexBufferWriter writer = VertexConsumerUtils.convertOrLog(consumer);
        if (writer == null) return false;
        return writePolyMeshes(polyMeshes, writer, lightmap, overlay, red, green, blue, alpha, finalPose, finalNormal);
    }

    private synchronized boolean writeCubes(ICube[] cubes, VertexBufferWriter writer, int lightmap, int overlay, float red, float green, float blue, float alpha,
                                            Matrix4f finalPose, Matrix3f finalNormal, boolean skipNormalVisibilityCull) {
        int color = color(red, green, blue, alpha);
        boolean cull = !skipNormalVisibilityCull && RenderSystem.getModelViewMatrix().m32() == 0;
        int emitted = 0;
        long ptr = SCRATCH;
        for (ICube cube : cubes) {
            CUBE_POSE.identity();
            CUBE_NORMAL_POSE.identity();
            applyCubeRotation(cube, CUBE_POSE, CUBE_NORMAL_POSE);
            prepareCubeVertices(cube, CUBE_POSE);
            for (int face = 0; face < BedrockCube.NUM_CUBE_FACES; face++) {
                if (cube instanceof CubePerFace perFace && perFace.isEmptyFace(face)) continue;
                float nx = FACE_NORMALS[face][0];
                float ny = FACE_NORMALS[face][1];
                float nz = FACE_NORMALS[face][2];

                float lnx = CUBE_NORMAL_POSE.m00() * nx + CUBE_NORMAL_POSE.m10() * ny + CUBE_NORMAL_POSE.m20() * nz;
                float lny = CUBE_NORMAL_POSE.m01() * nx + CUBE_NORMAL_POSE.m11() * ny + CUBE_NORMAL_POSE.m21() * nz;
                float lnz = CUBE_NORMAL_POSE.m02() * nx + CUBE_NORMAL_POSE.m12() * ny + CUBE_NORMAL_POSE.m22() * nz;
                float localLength = lnx * lnx + lny * lny + lnz * lnz;
                if (localLength > 1.0E-12f) {
                    float invLength = (float) (1.0 / Math.sqrt(localLength));
                    lnx *= invLength;
                    lny *= invLength;
                    lnz *= invLength;
                }

                float tnx = finalNormal.m00() * lnx + finalNormal.m10() * lny + finalNormal.m20() * lnz;
                float tny = finalNormal.m01() * lnx + finalNormal.m11() * lny + finalNormal.m21() * lnz;
                float tnz = finalNormal.m02() * lnx + finalNormal.m12() * lny + finalNormal.m22() * lnz;
                float normalLength = tnx * tnx + tny * tny + tnz * tnz;
                if (normalLength > 1.0E-12f) {
                    float invLength = (float) (1.0 / Math.sqrt(normalLength));
                    tnx *= invLength;
                    tny *= invLength;
                    tnz *= invLength;
                }
                if (cull && shouldCullFace(face, finalPose, tnx, tny, tnz)) continue;
                int packedNormal = packNormal(tnx, tny, tnz);
                int[] order = BedrockCube.VERTEX_ORDER[face];

                if (cube instanceof CubeBox box) {
                    ptr = emitCubeVertex(ptr, order[0], finalPose, color, box.uv(box.uvOrder(face, 1)), box.uv(box.uvOrder(face, 2)), overlay, lightmap, packedNormal);
                    ptr = emitCubeVertex(ptr, order[1], finalPose, color, box.uv(box.uvOrder(face, 0)), box.uv(box.uvOrder(face, 2)), overlay, lightmap, packedNormal);
                    ptr = emitCubeVertex(ptr, order[2], finalPose, color, box.uv(box.uvOrder(face, 0)), box.uv(box.uvOrder(face, 3)), overlay, lightmap, packedNormal);
                    ptr = emitCubeVertex(ptr, order[3], finalPose, color, box.uv(box.uvOrder(face, 1)), box.uv(box.uvOrder(face, 3)), overlay, lightmap, packedNormal);
                } else if (cube instanceof CubePerFace perFace) {
                    float[] uvs = perFace.faceUv(face);
                    ptr = emitCubeVertex(ptr, order[0], finalPose, color, uvs[0], uvs[1], overlay, lightmap, packedNormal);
                    ptr = emitCubeVertex(ptr, order[1], finalPose, color, uvs[2], uvs[3], overlay, lightmap, packedNormal);
                    ptr = emitCubeVertex(ptr, order[2], finalPose, color, uvs[4], uvs[5], overlay, lightmap, packedNormal);
                    ptr = emitCubeVertex(ptr, order[3], finalPose, color, uvs[6], uvs[7], overlay, lightmap, packedNormal);
                }
                emitted += 4;
                if (emitted >= MAX_VERTICES_PER_BATCH) {
                    flush(writer, emitted);
                    emitted = 0;
                    ptr = SCRATCH;
                }
            }
        }
        if (emitted > 0) flush(writer, emitted);
        return true;
    }

    private boolean writePolyMeshes(PolyMesh[] polyMeshes, VertexBufferWriter writer, int lightmap, int overlay, float red, float green, float blue, float alpha,
                                    Matrix4f finalPose, Matrix3f finalNormal) {
        int color = color(red, green, blue, alpha);
        int emitted = 0;
        long ptr = SCRATCH;
        for (PolyMesh polyMesh : polyMeshes) {
            for (PolyMesh.Triangle triangle : polyMesh.triangles()) {
                ptr = emitMeshVertex(ptr, triangle.a(), finalPose, finalNormal, color, overlay, lightmap);
                if (++emitted >= MAX_VERTICES_PER_BATCH) {
                    flush(writer, emitted);
                    emitted = 0;
                    ptr = SCRATCH;
                }
                ptr = emitMeshVertex(ptr, triangle.b(), finalPose, finalNormal, color, overlay, lightmap);
                if (++emitted >= MAX_VERTICES_PER_BATCH) {
                    flush(writer, emitted);
                    emitted = 0;
                    ptr = SCRATCH;
                }
                ptr = emitMeshVertex(ptr, triangle.c(), finalPose, finalNormal, color, overlay, lightmap);
                if (++emitted >= MAX_VERTICES_PER_BATCH) {
                    flush(writer, emitted);
                    emitted = 0;
                    ptr = SCRATCH;
                }
            }
        }
        if (emitted > 0) flush(writer, emitted);
        return true;
    }

    private static void prepareCubeVertices(ICube cube, Matrix4f pose) {
        float x = cube.x();
        float y = cube.y();
        float z = cube.z();
        float width = cube.width();
        float height = cube.height();
        float depth = cube.depth();

        float exx = pose.m00() * width;
        float exy = pose.m01() * width;
        float exz = pose.m02() * width;
        float eyx = pose.m10() * height;
        float eyy = pose.m11() * height;
        float eyz = pose.m12() * height;
        float ezx = pose.m20() * depth;
        float ezy = pose.m21() * depth;
        float ezz = pose.m22() * depth;
        setVertex(BedrockCube.VERTEX_X1_Y1_Z1,
                pose.m00() * x + pose.m10() * y + pose.m20() * z + pose.m30(),
                pose.m01() * x + pose.m11() * y + pose.m21() * z + pose.m31(),
                pose.m02() * x + pose.m12() * y + pose.m22() * z + pose.m32());
        setVertex(BedrockCube.VERTEX_X2_Y1_Z1, vx(0) + exx, vy(0) + exy, vz(0) + exz);
        setVertex(BedrockCube.VERTEX_X2_Y2_Z1, vx(1) + eyx, vy(1) + eyy, vz(1) + eyz);
        setVertex(BedrockCube.VERTEX_X1_Y2_Z1, vx(0) + eyx, vy(0) + eyy, vz(0) + eyz);
        setVertex(BedrockCube.VERTEX_X1_Y1_Z2, vx(0) + ezx, vy(0) + ezy, vz(0) + ezz);
        setVertex(BedrockCube.VERTEX_X2_Y1_Z2, vx(1) + ezx, vy(1) + ezy, vz(1) + ezz);
        setVertex(BedrockCube.VERTEX_X2_Y2_Z2, vx(2) + ezx, vy(2) + ezy, vz(2) + ezz);
        setVertex(BedrockCube.VERTEX_X1_Y2_Z2, vx(3) + ezx, vy(3) + ezy, vz(3) + ezz);
    }

    private static void applyCubeRotation(ICube cube, Matrix4f pose, Matrix3f normal) {
        if (!cube.hasRotation()) return;
        float[] pivot = cube.pivot();
        Quaternionf rotation = cube.rotation();
        pose.translate(pivot[0], pivot[1], pivot[2]);
        pose.rotate(rotation);
        pose.translate(-pivot[0], -pivot[1], -pivot[2]);
        normal.rotate(rotation);
    }

    private static long emitCubeVertex(long ptr, int vertexIndex, Matrix4f pose, int color, float u, float v, int overlay, int lightmap, int normal) {
        float x = vx(vertexIndex);
        float y = vy(vertexIndex);
        float z = vz(vertexIndex);
        ModelVertex.write(ptr,
                pose.m00() * x + pose.m10() * y + pose.m20() * z + pose.m30(),
                pose.m01() * x + pose.m11() * y + pose.m21() * z + pose.m31(),
                pose.m02() * x + pose.m12() * y + pose.m22() * z + pose.m32(),
                color, u, v, overlay, lightmap, normal);
        return ptr + STRIDE;
    }

    private long emitMeshVertex(long ptr, PolyMesh.Vertex vertex, Matrix4f pose, Matrix3f normalMatrix, int color, int overlay, int lightmap) {
        float x = vertex.x();
        float y = vertex.y();
        float z = vertex.z();
        float nx = normalMatrix.m00() * vertex.nx() + normalMatrix.m10() * vertex.ny() + normalMatrix.m20() * vertex.nz();
        float ny = normalMatrix.m01() * vertex.nx() + normalMatrix.m11() * vertex.ny() + normalMatrix.m21() * vertex.nz();
        float nz = normalMatrix.m02() * vertex.nx() + normalMatrix.m12() * vertex.ny() + normalMatrix.m22() * vertex.nz();
        float normalLength = nx * nx + ny * ny + nz * nz;
        if (normalLength > 1.0E-12f) {
            float invLength = (float) (1.0 / Math.sqrt(normalLength));
            nx *= invLength;
            ny *= invLength;
            nz *= invLength;
        }
        ModelVertex.write(ptr,
                pose.m00() * x + pose.m10() * y + pose.m20() * z + pose.m30(),
                pose.m01() * x + pose.m11() * y + pose.m21() * z + pose.m31(),
                pose.m02() * x + pose.m12() * y + pose.m22() * z + pose.m32(),
                color, vertex.u(), vertex.v(), overlay, lightmap, packNormal(nx, ny, nz));
        return ptr + STRIDE;
    }

    private static boolean shouldCullFace(int face, Matrix4f pose, float nx, float ny, float nz) {
        int[] verts = BedrockCube.VERTEX_ORDER[face];
        float cx = vx(verts[0]) + vx(verts[2]);
        float cy = vy(verts[0]) + vy(verts[2]);
        float cz = vz(verts[0]) + vz(verts[2]);
        float tx = pose.m00() * cx + pose.m10() * cy + pose.m20() * cz + pose.m30() + pose.m30();
        float ty = pose.m01() * cx + pose.m11() * cy + pose.m21() * cz + pose.m31() + pose.m31();
        float tz = pose.m02() * cx + pose.m12() * cy + pose.m22() * cz + pose.m32() + pose.m32();
        return tx * nx + ty * ny + tz * nz > 0;
    }

    private static void setVertex(int index, float x, float y, float z) {
        int base = index * 3;
        CUBE_VERTICES[base] = x;
        CUBE_VERTICES[base + 1] = y;
        CUBE_VERTICES[base + 2] = z;
    }

    private static float vx(int index) { return CUBE_VERTICES[index * 3]; }
    private static float vy(int index) { return CUBE_VERTICES[index * 3 + 1]; }
    private static float vz(int index) { return CUBE_VERTICES[index * 3 + 2]; }

    private static int color(float red, float green, float blue, float alpha) {
        return (int) (alpha * 255.0f) << 24 | (int) (blue * 255.0f) << 16 | (int) (green * 255.0f) << 8 | (int) (red * 255.0f);
    }

    @Override
    public void flush(VertexBufferWriter writer, int vertexCount) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            writer.push(stack, SCRATCH, vertexCount, ModelVertex.FORMAT);
        }
    }
}
