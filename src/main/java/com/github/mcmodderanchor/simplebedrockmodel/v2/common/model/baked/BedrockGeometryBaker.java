package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockCube;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BonesItem;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.CubesItem;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.FaceItem;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.FaceUVsItem;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.PolyMeshItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BedrockGeometryBaker {
    private static final int[][] UV_ORDER_NO_MIRROR = new int[][]{
            {2, 3, 7, 6}, {1, 2, 6, 7}, {1, 2, 7, 8}, {4, 5, 7, 8}, {2, 4, 7, 8}, {0, 1, 7, 8},
    };
    private static final int[][] UV_ORDER_MIRRORED = new int[][]{
            {3, 2, 7, 6}, {2, 1, 6, 7}, {2, 1, 7, 8}, {5, 4, 7, 8}, {1, 0, 7, 8}, {4, 2, 7, 8},
    };
    private static final Vector3f[] FACE_NORMALS = new Vector3f[]{
            new Vector3f(0, -1, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, -1),
            new Vector3f(0, 0, 1), new Vector3f(-1, 0, 0), new Vector3f(1, 0, 0)
    };

    private BedrockGeometryBaker() {
    }

    static BakeResult bake(BonesItem[] bones, Map<String, BedrockModelBaker.CompileBone> compileBones,
                           BedrockModelBaker.RuntimeIndex runtimeIndex, int texWidth, int texHeight, BakerOptions options) {
        Map<Integer, BakedGeometryChunkBuilder> chunks = new LinkedHashMap<>();
        Map<Integer, RetainedCubeGeometryBuilder> retainedCubeGeometry = options.retainCubeGeometry() ? new LinkedHashMap<>() : null;
        for (BonesItem item : bones) {
            BedrockModelBaker.CompileBone sourceBone = compileBones.get(item.getName());
            int attachIndex = options.bakeStaticGeometry() ? BedrockModelBaker.nearestRuntimeAncestorOrSelf(sourceBone) : sourceBone.runtimeIndex;
            Matrix4f sourceGlobal = BedrockModelBaker.globalBindMatrix(sourceBone);
            Matrix4f attachInverse = attachIndex >= 0 ? BedrockModelBaker.globalBindMatrix(runtimeIndex.bones().get(attachIndex)).invert(new Matrix4f()) : new Matrix4f();
            Matrix4f toAttach = attachIndex >= 0 ? attachInverse.mul(sourceGlobal, new Matrix4f()) : new Matrix4f(sourceGlobal);
            Matrix3f normalToAttach = new Matrix3f(toAttach);
            BakedGeometryChunkBuilder builder = chunks.computeIfAbsent(attachIndex, ignored -> new BakedGeometryChunkBuilder());
            if (item.getCubes() != null) {
                builder.addSourceBone(sourceBone.name);
                RetainedCubeGeometryBuilder retainedBuilder = retainedCubeGeometry == null ? null
                        : retainedCubeGeometry.computeIfAbsent(attachIndex, ignored -> new RetainedCubeGeometryBuilder());
                for (CubesItem cube : item.getCubes()) {
                    bakeCube(cube, item, sourceBone, texWidth, texHeight, toAttach, normalToAttach, builder, retainedBuilder);
                }
            }
            if (item.getPolyMesh() != null) {
                builder.addSourceBone(sourceBone.name);
                bakePolyMesh(item.getPolyMesh(), sourceBone, texWidth, texHeight, toAttach, normalToAttach, builder);
            }
        }
        return new BakeResult(toChunks(chunks), retainedCubeGeometry == null ? new BakedCubeGeometry[0] : toCubeGeometry(retainedCubeGeometry));
    }

    private static void bakeCube(CubesItem cube, BonesItem boneItem, BedrockModelBaker.CompileBone part, int texWidth, int texHeight,
                                 Matrix4f toAttach, Matrix3f normalToAttach, BakedGeometryChunkBuilder out,
                                 RetainedCubeGeometryBuilder retainedCubeGeometry) {
        float[] size = cube.getSize();
        float[] origin = java.util.Arrays.copyOf(cube.getOrigin(), 3);
        float inflate = cube.getInflate();
        float x0 = -(origin[0] + size[0] + inflate) / 16.0f;
        float x1 = -(origin[0] - inflate) / 16.0f;
        float y0 = (origin[1] - inflate) / 16.0f;
        float y1 = (origin[1] + size[1] + inflate) / 16.0f;
        float z0 = (origin[2] - inflate) / 16.0f;
        float z1 = (origin[2] + size[2] + inflate) / 16.0f;
        Matrix4f cubeTransform = new Matrix4f();
        float[] cubeRotation = cube.getRotation() != null ? java.util.Arrays.copyOf(cube.getRotation(), 3) : null;
        float[] cubePivot = cube.getPivot() != null ? java.util.Arrays.copyOf(cube.getPivot(), 3) : null;
        if (cubePivot != null) {
            cubePivot[0] = -cubePivot[0];
            cubeTransform.translate(cubePivot[0] / 16.0f, cubePivot[1] / 16.0f, cubePivot[2] / 16.0f);
        }
        if (cubeRotation != null) {
            cubeRotation[0] = (float) -Math.toRadians(cubeRotation[0]);
            cubeRotation[1] = (float) -Math.toRadians(cubeRotation[1]);
            cubeRotation[2] = (float) Math.toRadians(cubeRotation[2]);
            cubeTransform.rotate(new Quaternionf().rotateZYX(cubeRotation[2], cubeRotation[1], cubeRotation[0]));
        }
        if (cubePivot != null) {
            cubeTransform.translate(-cubePivot[0] / 16.0f, -cubePivot[1] / 16.0f, -cubePivot[2] / 16.0f);
        }
        Matrix4f matrix = new Matrix4f(toAttach).mul(cubeTransform);
        if (retainedCubeGeometry != null) {
            retainedCubeGeometry.addCube(x0, y0, z0, x1 - x0, y1 - y0, z1 - z0, matrix);
        }
        Matrix3f normalMatrix = new Matrix3f(normalToAttach).mul(new Matrix3f(cubeTransform));
        boolean mirror = cube.isHasMirror() ? cube.isMirror() : boneItem.isMirror();
        if (cube.getFaceUv() == null) {
            bakeBoxUvCube(cube, x0, y0, z0, x1, y1, z1, mirror, texWidth, texHeight, matrix, normalMatrix, out);
        } else {
            bakePerFaceUvCube(cube, x0, y0, z0, x1, y1, z1, texWidth, texHeight, matrix, normalMatrix, out);
        }
    }

    private static void bakeBoxUvCube(CubesItem cube, float x0, float y0, float z0, float x1, float y1, float z1, boolean mirror,
                                      int texWidth, int texHeight, Matrix4f matrix, Matrix3f normalMatrix, BakedGeometryChunkBuilder out) {
        Vector3f[] vertices = cubeVertices(x0, y0, z0, x1, y1, z1, matrix);
        float[] uv = cube.getUv();
        float dx = Mth.floor(cube.getSize()[0]);
        float dy = Mth.floor(cube.getSize()[1]);
        float dz = Mth.floor(cube.getSize()[2]);
        float scaleU = texWidth == 0 ? 0 : 1.0f / texWidth;
        float scaleV = texHeight == 0 ? 0 : 1.0f / texHeight;
        float[] uvs = new float[9];
        float texOffX = uv[0];
        float texOffY = uv[1];
        uvs[0] = scaleU * texOffX;
        uvs[1] = scaleU * (texOffX + dz);
        uvs[2] = scaleU * (texOffX + dz + dx);
        uvs[3] = scaleU * (texOffX + dz + dx + dx);
        uvs[4] = scaleU * (texOffX + dz + dx + dz);
        uvs[5] = scaleU * (texOffX + dz + dx + dz + dx);
        uvs[6] = scaleV * texOffY;
        uvs[7] = scaleV * (texOffY + dz);
        uvs[8] = scaleV * (texOffY + dz + dy);
        int[][] uvOrder = mirror ? UV_ORDER_MIRRORED : UV_ORDER_NO_MIRROR;
        for (int face = 0; face < BedrockCube.NUM_CUBE_FACES; face++) {
            emitQuad(vertices, face, normalMatrix, out,
                    uvs[uvOrder[face][1]], uvs[uvOrder[face][2]],
                    uvs[uvOrder[face][0]], uvs[uvOrder[face][2]],
                    uvs[uvOrder[face][0]], uvs[uvOrder[face][3]],
                    uvs[uvOrder[face][1]], uvs[uvOrder[face][3]]);
        }
    }

    private static void bakePerFaceUvCube(CubesItem cube, float x0, float y0, float z0, float x1, float y1, float z1,
                                          int texWidth, int texHeight, Matrix4f matrix, Matrix3f normalMatrix, BakedGeometryChunkBuilder out) {
        Vector3f[] vertices = cubeVertices(x0, y0, z0, x1, y1, z1, matrix);
        FaceUVsItem faces = cube.getFaceUv();
        if (faces == null) {
            return;
        }
        for (int face = 0; face < BedrockCube.NUM_CUBE_FACES; face++) {
            FaceItem faceItem = faces.getFace(Direction.values()[face]);
            if (faceItem == null) continue;
            if (isEmptyFace(faceItem)) continue;
            float[] uvs = faceItem.getRotatedUVs(texWidth, texHeight);
            emitQuad(vertices, face, normalMatrix, out, uvs[0], uvs[1], uvs[2], uvs[3], uvs[4], uvs[5], uvs[6], uvs[7]);
        }
    }

    private static boolean isEmptyFace(FaceItem faceItem) {
        float[] uv = faceItem.getUv();
        float[] uvSize = faceItem.getUvSize();
        if (uv == null || uv.length < 2 || uvSize == null || uvSize.length < 2) {
            return true;
        }
        return Math.abs(uvSize[0]) < 1.0E-9f || Math.abs(uvSize[1]) < 1.0E-9f;
    }

    private static Vector3f[] cubeVertices(float x0, float y0, float z0, float x1, float y1, float z1, Matrix4f matrix) {
        Vector3f[] vertices = new Vector3f[8];
        vertices[0] = new Vector3f(x0, y0, z0).mulPosition(matrix);
        vertices[1] = new Vector3f(x1, y0, z0).mulPosition(matrix);
        vertices[2] = new Vector3f(x1, y1, z0).mulPosition(matrix);
        vertices[3] = new Vector3f(x0, y1, z0).mulPosition(matrix);
        vertices[4] = new Vector3f(x0, y0, z1).mulPosition(matrix);
        vertices[5] = new Vector3f(x1, y0, z1).mulPosition(matrix);
        vertices[6] = new Vector3f(x1, y1, z1).mulPosition(matrix);
        vertices[7] = new Vector3f(x0, y1, z1).mulPosition(matrix);
        return vertices;
    }

    private static void emitQuad(Vector3f[] vertices, int face, Matrix3f normalMatrix, BakedGeometryChunkBuilder out,
                                 float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3) {
        Vector3f normal = new Vector3f(FACE_NORMALS[face]).mul(normalMatrix);
        if (normal.lengthSquared() > 1.0E-12f) normal.normalize();
        int[] order = BedrockCube.VERTEX_ORDER[face];
        out.addQuad(vertices[order[0]], vertices[order[1]], vertices[order[2]], vertices[order[3]], normal, u0, v0, u1, v1, u2, v2, u3, v3);
    }

    private static void bakePolyMesh(PolyMeshItem polyMesh, BedrockModelBaker.CompileBone part, float texWidth, float texHeight, Matrix4f toAttach,
                                     Matrix3f normalToAttach, BakedGeometryChunkBuilder out) {
        float[][] positions = polyMesh.getPositions();
        JsonElement polys = polyMesh.getPolys();
        if (positions == null || polys == null || polys.isJsonNull()) {
            return;
        }

        float[][] normals = polyMesh.getNormals();
        float[][] uvs = polyMesh.getUvs();
        if (polys.isJsonPrimitive()) {
            var primitive = polys.getAsJsonPrimitive();
            if (!primitive.isString()) {
                return;
            }
            String mode = primitive.getAsString();
            if ("tri_list".equals(mode)) {
                bakePolyMeshListMode(polyMesh, part, texWidth, texHeight, toAttach, normalToAttach, out, positions, normals, uvs, 3);
            } else if ("quad_list".equals(mode)) {
                bakePolyMeshListMode(polyMesh, part, texWidth, texHeight, toAttach, normalToAttach, out, positions, normals, uvs, 4);
            }
        } else if (polys.isJsonArray()) {
            for (JsonElement polyElement : polys.getAsJsonArray()) {
                if (polyElement != null && polyElement.isJsonArray()) {
                    bakeIndexedPolyMeshPolygon(polyMesh, part, texWidth, texHeight, toAttach, normalToAttach, out, positions, normals, uvs, polyElement.getAsJsonArray());
                }
            }
        }
    }

    private static void bakePolyMeshListMode(PolyMeshItem polyMesh, BedrockModelBaker.CompileBone part, float texWidth, float texHeight, Matrix4f toAttach,
                                             Matrix3f normalToAttach, BakedGeometryChunkBuilder out, float[][] positions, float[][] normals,
                                             float[][] uvs, int stride) {
        for (int i = 0; i + stride - 1 < positions.length; i += stride) {
            ArrayList<PolyMeshVertex> polygon = new ArrayList<>(stride);
            for (int j = 0; j < stride; j++) {
                polygon.add(createPolyMeshVertex(polyMesh, part, texWidth, texHeight, toAttach, normalToAttach, positions, normals, uvs, i + j, i + j, i + j));
            }
            bakePolyMeshPolygon(polygon, out);
        }
    }

    private static void bakeIndexedPolyMeshPolygon(PolyMeshItem polyMesh, BedrockModelBaker.CompileBone part, float texWidth, float texHeight, Matrix4f toAttach,
                                                   Matrix3f normalToAttach, BakedGeometryChunkBuilder out, float[][] positions, float[][] normals,
                                                   float[][] uvs, JsonArray poly) {
        ArrayList<PolyMeshVertex> polygon = new ArrayList<>(poly.size());
        for (JsonElement vertexElement : poly) {
            if (vertexElement == null || !vertexElement.isJsonArray()) {
                continue;
            }
            JsonArray indices = vertexElement.getAsJsonArray();
            if (indices.isEmpty()) {
                continue;
            }
            int positionIndex = getPolyMeshIndex(indices, 0);
            int normalIndex = indices.size() > 1 ? getPolyMeshIndex(indices, 1) : positionIndex;
            int uvIndex = indices.size() > 2 ? getPolyMeshIndex(indices, 2) : positionIndex;
            polygon.add(createPolyMeshVertex(polyMesh, part, texWidth, texHeight, toAttach, normalToAttach, positions, normals, uvs, positionIndex, normalIndex, uvIndex));
        }
        if (polygon.size() > 1 && samePolyMeshPosition(polygon.get(0), polygon.get(polygon.size() - 1))) {
            polygon.remove(polygon.size() - 1);
        }
        bakePolyMeshPolygon(polygon, out);
    }

    private static int getPolyMeshIndex(JsonArray indices, int index) {
        return indices.get(index).getAsInt();
    }

    private static PolyMeshVertex createPolyMeshVertex(PolyMeshItem polyMesh, BedrockModelBaker.CompileBone part, float texWidth, float texHeight,
                                                       Matrix4f toAttach, Matrix3f normalToAttach, float[][] positions, float[][] normals,
                                                       float[][] uvs, int positionIndex, int normalIndex, int uvIndex) {
        float[] position = getPolyMeshArray(positions, positionIndex);
        Vector3f bakedPosition = new Vector3f(
                -getPolyMeshValue(position, 0) / 16.0f,
                getPolyMeshValue(position, 1) / 16.0f,
                getPolyMeshValue(position, 2) / 16.0f
        ).mulPosition(toAttach);

        Vector3f bakedNormal = new Vector3f();
        if (normals != null && normalIndex >= 0 && normalIndex < normals.length) {
            float[] normal = normals[normalIndex];
            bakedNormal.set(-getPolyMeshValue(normal, 0), getPolyMeshValue(normal, 1), getPolyMeshValue(normal, 2));
            if (bakedNormal.lengthSquared() > 1.0E-12f) {
                bakedNormal.normalize().mul(normalToAttach);
                if (bakedNormal.lengthSquared() > 1.0E-12f) {
                    bakedNormal.normalize();
                }
            }
        }

        float u = 0;
        float v = 0;
        if (uvs != null && uvIndex >= 0 && uvIndex < uvs.length) {
            float[] uv = uvs[uvIndex];
            u = getPolyMeshValue(uv, 0);
            v = getPolyMeshValue(uv, 1);
            if (!polyMesh.isNormalizedUvs()) {
                u = texWidth == 0 ? 0 : u / texWidth;
                v = texHeight == 0 ? 0 : v / texHeight;
            }
            v = 1.0f - v;
        }

        return new PolyMeshVertex(bakedPosition, bakedNormal, u, v);
    }

    private static float[] getPolyMeshArray(float[][] values, int index) {
        if (index < 0 || index >= values.length || values[index] == null) {
            return new float[0];
        }
        return values[index];
    }

    private static float getPolyMeshValue(float[] values, int index) {
        return values != null && index >= 0 && index < values.length ? values[index] : 0;
    }

    private static void bakePolyMeshPolygon(List<PolyMeshVertex> polygon, BakedGeometryChunkBuilder out) {
        if (polygon.size() < 3) {
            return;
        }
        if (polygon.size() == 4) {
            emitPolyMeshTriangle(polygon.get(0), polygon.get(1), polygon.get(2), out);
            emitPolyMeshTriangle(polygon.get(2), polygon.get(3), polygon.get(0), out);
            return;
        }
        for (int i = 1; i + 1 < polygon.size(); i++) {
            emitPolyMeshTriangle(polygon.get(0), polygon.get(i), polygon.get(i + 1), out);
        }
    }

    private static void emitPolyMeshTriangle(PolyMeshVertex a, PolyMeshVertex b, PolyMeshVertex c, BakedGeometryChunkBuilder out) {
        if (samePolyMeshPosition(a, b) || samePolyMeshPosition(b, c) || samePolyMeshPosition(c, a)) {
            return;
        }
        Vector3f fallbackNormal = computePolyMeshNormal(a, b, c);
        out.addVertex(a.position(), normalOrFallback(a.normal(), fallbackNormal), a.u(), a.v());
        out.addVertex(b.position(), normalOrFallback(b.normal(), fallbackNormal), b.u(), b.v());
        out.addVertex(c.position(), normalOrFallback(c.normal(), fallbackNormal), c.u(), c.v());
    }

    private static boolean samePolyMeshPosition(PolyMeshVertex first, PolyMeshVertex second) {
        return Math.abs(first.position().x() - second.position().x()) < 1.0E-6f
                && Math.abs(first.position().y() - second.position().y()) < 1.0E-6f
                && Math.abs(first.position().z() - second.position().z()) < 1.0E-6f;
    }

    private static Vector3f computePolyMeshNormal(PolyMeshVertex a, PolyMeshVertex b, PolyMeshVertex c) {
        Vector3f edge1 = new Vector3f(b.position()).sub(a.position());
        Vector3f edge2 = new Vector3f(c.position()).sub(a.position());
        Vector3f normal = edge1.cross(edge2, new Vector3f());
        if (normal.lengthSquared() < 1.0E-12f) {
            normal.set(0, 1, 0);
        } else {
            normal.normalize();
        }
        return normal;
    }

    private static Vector3fc normalOrFallback(Vector3fc normal, Vector3fc fallback) {
        return normal.lengthSquared() > 1.0E-12f ? normal : fallback;
    }

    private static BakedGeometryChunk[] toChunks(Map<Integer, BakedGeometryChunkBuilder> builders) {
        ArrayList<BakedGeometryChunk> chunks = new ArrayList<>();
        for (Map.Entry<Integer, BakedGeometryChunkBuilder> entry : builders.entrySet()) {
            BakedGeometryChunkBuilder builder = entry.getValue();
            if (builder.isEmpty()) continue;
            chunks.add(builder.toChunk(entry.getKey()));
        }
        return chunks.toArray(BakedGeometryChunk[]::new);
    }

    private static BakedCubeGeometry[] toCubeGeometry(Map<Integer, RetainedCubeGeometryBuilder> builders) {
        ArrayList<BakedCubeGeometry> geometry = new ArrayList<>();
        for (Map.Entry<Integer, RetainedCubeGeometryBuilder> entry : builders.entrySet()) {
            RetainedCubeGeometryBuilder builder = entry.getValue();
            if (!builder.hasCubes()) continue;
            geometry.add(builder.toGeometry(entry.getKey()));
        }
        return geometry.toArray(BakedCubeGeometry[]::new);
    }

    record BakeResult(BakedGeometryChunk[] chunks, BakedCubeGeometry[] cubeGeometry) {}

    private static final class RetainedCubeGeometryBuilder {
        private final ArrayList<BakedCube> cubes = new ArrayList<>();
        private final LocalCubeBounds.Builder bounds = LocalCubeBounds.builder();

        void addCube(float x, float y, float z, float width, float height, float depth, Matrix4f localTransform) {
            cubes.add(new BakedCube(x, y, z, width, height, depth, localTransform));
            bounds.includeCube(x, y, z, width, height, depth, localTransform);
        }

        boolean hasCubes() {
            return !cubes.isEmpty();
        }

        BakedCubeGeometry toGeometry(int attachBoneIndex) {
            LocalCubeBounds localBounds = bounds.build();
            if (localBounds == null) {
                throw new IllegalStateException("retained cube geometry has no bounds");
            }
            return new BakedCubeGeometry(attachBoneIndex, localBounds, cubes.toArray(BakedCube[]::new));
        }
    }

    private record PolyMeshVertex(Vector3f position, Vector3f normal, float u, float v) {}
}
