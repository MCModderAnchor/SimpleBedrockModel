package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockCube;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.compat.sodium.SodiumCompat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.FastColor;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class TreeGeometryWriter {
    private static final Vector3f[] FACE_NORMALS = new Vector3f[]{
            new Vector3f(0, -1, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, -1),
            new Vector3f(0, 0, 1), new Vector3f(-1, 0, 0), new Vector3f(1, 0, 0)
    };
    private static final Vector3f[] CUBE_VERTICES = new Vector3f[8];
    private static final Vector3f EDGE_X = new Vector3f();
    private static final Vector3f EDGE_Y = new Vector3f();
    private static final Vector3f EDGE_Z = new Vector3f();
    private static final Vector3f CUBE_NORMAL = new Vector3f();
    private static final Matrix4f CUBE_POSE = new Matrix4f();
    private static final Matrix3f CUBE_NORMAL_POSE = new Matrix3f();

    static {
        for (int i = 0; i < CUBE_VERTICES.length; i++) {
            CUBE_VERTICES[i] = new Vector3f();
        }
    }

    private TreeGeometryWriter() {
    }

    public static boolean writeCubesSodium(ICube[] cubes, VertexConsumer consumer, Matrix4f poseMatrix, Matrix3f normalMatrix,
                                           int light, int overlay, float red, float green, float blue, float alpha) {
        return writeCubesSodium(cubes, consumer, poseMatrix, normalMatrix, light, overlay, red, green, blue, alpha, false);
    }

    public static boolean writeCubesSodium(ICube[] cubes, VertexConsumer consumer, Matrix4f poseMatrix, Matrix3f normalMatrix,
                                           int light, int overlay, float red, float green, float blue, float alpha,
                                           boolean skipNormalVisibilityCull) {
        return SodiumCompat.writeCubes(cubes, consumer, light, overlay, red, green, blue, alpha, poseMatrix, normalMatrix, skipNormalVisibilityCull);
    }

    public static void writeCubes(ICube[] cubes, VertexConsumer consumer, Matrix4f poseMatrix, Matrix3f normalMatrix,
                                  int light, int overlay, float red, float green, float blue, float alpha) {
        writeCubes(cubes, consumer, poseMatrix, normalMatrix, light, overlay, red, green, blue, alpha, false);
    }

    public static void writeCubes(ICube[] cubes, VertexConsumer consumer, Matrix4f poseMatrix, Matrix3f normalMatrix,
                                  int light, int overlay, float red, float green, float blue, float alpha,
                                  boolean skipNormalVisibilityCull) {
        if (writeCubesSodium(cubes, consumer, poseMatrix, normalMatrix, light, overlay, red, green, blue, alpha, skipNormalVisibilityCull)) return;
        writeCubesFallback(cubes, consumer, poseMatrix, normalMatrix, light, overlay, red, green, blue, alpha, skipNormalVisibilityCull);
    }

    public static synchronized void writeCubesFallback(ICube[] cubes, VertexConsumer consumer, Matrix4f poseMatrix, Matrix3f normalMatrix,
                                                       int light, int overlay, float red, float green, float blue, float alpha) {
        writeCubesFallback(cubes, consumer, poseMatrix, normalMatrix, light, overlay, red, green, blue, alpha, false);
    }

    public static synchronized void writeCubesFallback(ICube[] cubes, VertexConsumer consumer, Matrix4f poseMatrix, Matrix3f normalMatrix,
                                                       int light, int overlay, float red, float green, float blue, float alpha,
                                                       boolean skipNormalVisibilityCull) {
        for (ICube cube : cubes) {
            CUBE_POSE.identity();
            CUBE_NORMAL_POSE.identity();
            applyCubeRotation(cube, CUBE_POSE, CUBE_NORMAL_POSE);
            prepareCubeVertices(cube, CUBE_POSE);
            if (cube instanceof CubeBox box) {
                writeBox(box, consumer, poseMatrix, normalMatrix, light, overlay, red, green, blue, alpha);
            } else if (cube instanceof CubePerFace perFace) {
                writePerFace(perFace, consumer, poseMatrix, normalMatrix, light, overlay, red, green, blue, alpha);
            }
        }
    }

    public static boolean writePolyMeshesSodium(PolyMesh[] polyMeshes, VertexConsumer consumer, Matrix4f poseMatrix, Matrix3f normalMatrix,
                                                int light, int overlay, float red, float green, float blue, float alpha) {
        return SodiumCompat.writePolyMeshes(polyMeshes, consumer, light, overlay, red, green, blue, alpha, poseMatrix, normalMatrix);
    }

    public static void writePolyMeshes(PolyMesh[] polyMeshes, VertexConsumer consumer, Matrix4f poseMatrix, Matrix3f normalMatrix,
                                       int light, int overlay, float red, float green, float blue, float alpha) {
        if (writePolyMeshesSodium(polyMeshes, consumer, poseMatrix, normalMatrix, light, overlay, red, green, blue, alpha)) return;
        writePolyMeshesFallback(polyMeshes, consumer, poseMatrix, normalMatrix, light, overlay, red, green, blue, alpha);
    }

    public static void writePolyMeshesFallback(PolyMesh[] polyMeshes, VertexConsumer consumer, Matrix4f poseMatrix, Matrix3f normalMatrix,
                                               int light, int overlay, float red, float green, float blue, float alpha) {
        for (PolyMesh polyMesh : polyMeshes) {
            for (PolyMesh.Triangle triangle : polyMesh.triangles()) {
                emitVertex(consumer, triangle.a(), poseMatrix, normalMatrix, light, overlay, red, green, blue, alpha);
                emitVertex(consumer, triangle.b(), poseMatrix, normalMatrix, light, overlay, red, green, blue, alpha);
                emitVertex(consumer, triangle.c(), poseMatrix, normalMatrix, light, overlay, red, green, blue, alpha);
            }
        }
    }

    public static boolean hasTriangles(PolyMesh[] polyMeshes) {
        for (PolyMesh polyMesh : polyMeshes) {
            if (polyMesh.hasTriangles()) return true;
        }
        return false;
    }

    private static void writeBox(CubeBox box, VertexConsumer consumer, Matrix4f poseMatrix, Matrix3f normalMatrix,
                                 int light, int overlay, float red, float green, float blue, float alpha) {
        for (int face = 0; face < BedrockCube.NUM_CUBE_FACES; face++) {
            prepareNormal(face, normalMatrix);
            int[] order = BedrockCube.VERTEX_ORDER[face];
            emitCubeVertex(consumer, CUBE_VERTICES[order[0]], poseMatrix, red, green, blue, alpha,
                    box.uv(box.uvOrder(face, 1)), box.uv(box.uvOrder(face, 2)), overlay, light);
            emitCubeVertex(consumer, CUBE_VERTICES[order[1]], poseMatrix, red, green, blue, alpha,
                    box.uv(box.uvOrder(face, 0)), box.uv(box.uvOrder(face, 2)), overlay, light);
            emitCubeVertex(consumer, CUBE_VERTICES[order[2]], poseMatrix, red, green, blue, alpha,
                    box.uv(box.uvOrder(face, 0)), box.uv(box.uvOrder(face, 3)), overlay, light);
            emitCubeVertex(consumer, CUBE_VERTICES[order[3]], poseMatrix, red, green, blue, alpha,
                    box.uv(box.uvOrder(face, 1)), box.uv(box.uvOrder(face, 3)), overlay, light);
        }
    }

    private static void writePerFace(CubePerFace cube, VertexConsumer consumer, Matrix4f poseMatrix, Matrix3f normalMatrix,
                                     int light, int overlay, float red, float green, float blue, float alpha) {
        for (int face = 0; face < BedrockCube.NUM_CUBE_FACES; face++) {
            if (cube.isEmptyFace(face)) continue;
            prepareNormal(face, normalMatrix);
            int[] order = BedrockCube.VERTEX_ORDER[face];
            float[] uvs = cube.faceUv(face);
            emitCubeVertex(consumer, CUBE_VERTICES[order[0]], poseMatrix, red, green, blue, alpha, uvs[0], uvs[1], overlay, light);
            emitCubeVertex(consumer, CUBE_VERTICES[order[1]], poseMatrix, red, green, blue, alpha, uvs[2], uvs[3], overlay, light);
            emitCubeVertex(consumer, CUBE_VERTICES[order[2]], poseMatrix, red, green, blue, alpha, uvs[4], uvs[5], overlay, light);
            emitCubeVertex(consumer, CUBE_VERTICES[order[3]], poseMatrix, red, green, blue, alpha, uvs[6], uvs[7], overlay, light);
        }
    }

    private static void prepareCubeVertices(ICube cube, Matrix4f pose) {
        float x = cube.x();
        float y = cube.y();
        float z = cube.z();
        float width = cube.width();
        float height = cube.height();
        float depth = cube.depth();

        EDGE_X.set(pose.m00(), pose.m01(), pose.m02()).mul(width);
        EDGE_Y.set(pose.m10(), pose.m11(), pose.m12()).mul(height);
        EDGE_Z.set(pose.m20(), pose.m21(), pose.m22()).mul(depth);
        CUBE_VERTICES[BedrockCube.VERTEX_X1_Y1_Z1].set(x, y, z).mulPosition(pose);
        CUBE_VERTICES[BedrockCube.VERTEX_X1_Y1_Z1].add(EDGE_X, CUBE_VERTICES[BedrockCube.VERTEX_X2_Y1_Z1]);
        CUBE_VERTICES[BedrockCube.VERTEX_X2_Y1_Z1].add(EDGE_Y, CUBE_VERTICES[BedrockCube.VERTEX_X2_Y2_Z1]);
        CUBE_VERTICES[BedrockCube.VERTEX_X1_Y1_Z1].add(EDGE_Y, CUBE_VERTICES[BedrockCube.VERTEX_X1_Y2_Z1]);
        CUBE_VERTICES[BedrockCube.VERTEX_X1_Y1_Z1].add(EDGE_Z, CUBE_VERTICES[BedrockCube.VERTEX_X1_Y1_Z2]);
        CUBE_VERTICES[BedrockCube.VERTEX_X2_Y1_Z1].add(EDGE_Z, CUBE_VERTICES[BedrockCube.VERTEX_X2_Y1_Z2]);
        CUBE_VERTICES[BedrockCube.VERTEX_X2_Y2_Z1].add(EDGE_Z, CUBE_VERTICES[BedrockCube.VERTEX_X2_Y2_Z2]);
        CUBE_VERTICES[BedrockCube.VERTEX_X1_Y2_Z1].add(EDGE_Z, CUBE_VERTICES[BedrockCube.VERTEX_X1_Y2_Z2]);
    }

    private static void prepareNormal(int face, Matrix3f normalMatrix) {
        CUBE_NORMAL.set(FACE_NORMALS[face]).mul(CUBE_NORMAL_POSE);
        if (CUBE_NORMAL.lengthSquared() > 1.0E-12f) CUBE_NORMAL.normalize();
        CUBE_NORMAL.mul(normalMatrix);
        if (CUBE_NORMAL.lengthSquared() > 1.0E-12f) CUBE_NORMAL.normalize();
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

    private static void emitCubeVertex(VertexConsumer consumer, Vector3f position, Matrix4f poseMatrix,
                                       float red, float green, float blue, float alpha,
                                       float u, float v, int overlay, int light) {
        float x = position.x;
        float y = position.y;
        float z = position.z;
        int color = FastColor.ARGB32.color(
                (int) (alpha * 255.0F),
                (int) (red * 255.0F),
                (int) (green * 255.0F),
                (int) (blue * 255.0F)
        );
        consumer.addVertex(
                poseMatrix.m00() * x + poseMatrix.m10() * y + poseMatrix.m20() * z + poseMatrix.m30(),
                poseMatrix.m01() * x + poseMatrix.m11() * y + poseMatrix.m21() * z + poseMatrix.m31(),
                poseMatrix.m02() * x + poseMatrix.m12() * y + poseMatrix.m22() * z + poseMatrix.m32(),
                color, u, v, overlay, light, CUBE_NORMAL.x, CUBE_NORMAL.y, CUBE_NORMAL.z
        );
    }

    private static void emitVertex(VertexConsumer consumer, PolyMesh.Vertex vertex, Matrix4f poseMatrix, Matrix3f normalMatrix,
                                   int light, int overlay, float red, float green, float blue, float alpha) {
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
        int color = FastColor.ARGB32.color(
                (int) (alpha * 255.0F),
                (int) (red * 255.0F),
                (int) (green * 255.0F),
                (int) (blue * 255.0F)
        );
        consumer.addVertex(
                poseMatrix.m00() * x + poseMatrix.m10() * y + poseMatrix.m20() * z + poseMatrix.m30(),
                poseMatrix.m01() * x + poseMatrix.m11() * y + poseMatrix.m21() * z + poseMatrix.m31(),
                poseMatrix.m02() * x + poseMatrix.m12() * y + poseMatrix.m22() * z + poseMatrix.m32(),
                color, vertex.u(), vertex.v(), overlay, light, nx, ny, nz
        );
    }
}
