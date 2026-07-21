package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.LocatorData;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.*;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.LocalCubeBounds;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.maydaymemory.mae.basic.ArrayPoseBuilder;
import com.maydaymemory.mae.basic.BoneTransform;
import com.maydaymemory.mae.basic.Pose;
import com.maydaymemory.mae.basic.PoseBuilder;
import com.maydaymemory.mae.basic.RotationView;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.*;

public class TreeBedrockModelBaker {
    private static final int[][] UV_ORDER_NO_MIRROR = new int[][]{
            {2, 3, 7, 6}, {1, 2, 6, 7}, {1, 2, 7, 8}, {4, 5, 7, 8}, {2, 4, 7, 8}, {0, 1, 7, 8},
    };
    private static final int[][] UV_ORDER_MIRRORED = new int[][]{
            {3, 2, 7, 6}, {2, 1, 6, 7}, {2, 1, 7, 8}, {5, 4, 7, 8}, {1, 0, 7, 8}, {4, 2, 7, 8},
    };

    public static TreeBedrockModel bake(BedrockModelPOJO pojo) {
        ModelSource source = modelSource(pojo);
        if (source.bones == null || source.bones.length == 0) {
            return new TreeBedrockModel(new TreeBoneDefinition[0], Map.of(), Map.of(), new ArrayPoseBuilder().toPose(), source.renderBoundingBox);
        }
        CompileBone[] compileBones = createCompileBones(source.bones);
        Map<String, CompileBone> boneByName = indexCompileBones(compileBones);
        linkCompileBones(source.bones, boneByName);
        TreeBoneDefinition[] definitions = createDefinitions(source.bones, compileBones, source.texWidth, source.texHeight);
        definitions = applySubtreeGeometryFlags(definitions);
        Map<String, Integer> indexByName = new LinkedHashMap<>();
        Map<String, Map.Entry<Integer, LocatorData>> locatorByName = new LinkedHashMap<>();
        for (TreeBoneDefinition definition : definitions) {
            indexByName.put(definition.name(), definition.index());
            for (Map.Entry<String, LocatorData> locator : definition.locators().entrySet()) {
                locatorByName.put(locator.getKey(), TreeBedrockModel.locatorEntry(definition.index(), locator.getValue()));
            }
        }
        return new TreeBedrockModel(definitions, indexByName, locatorByName, createBindPose(definitions), source.renderBoundingBox);
    }

    private static TreeBoneDefinition[] createDefinitions(BonesItem[] sourceBones, CompileBone[] compileBones, int texWidth, int texHeight) {
        ArrayList<List<Integer>> children = new ArrayList<>(compileBones.length);
        for (int i = 0; i < compileBones.length; i++) children.add(new ArrayList<>());
        for (CompileBone bone : compileBones) {
            if (bone.parent != null) children.get(bone.parent.index).add(bone.index);
        }
        TreeBoneDefinition[] result = new TreeBoneDefinition[compileBones.length];
        for (int i = 0; i < compileBones.length; i++) {
            CompileBone bone = compileBones[i];
            BonesItem sourceBone = sourceBones[i];
            int parentIndex = bone.parent == null ? -1 : bone.parent.index;
            int[] childArray = children.get(i).stream().mapToInt(Integer::intValue).toArray();
            ICube[] cubes = createCubes(sourceBone, bone, texWidth, texHeight);
            LocalCubeBounds ownCubeBounds = ownCubeBounds(cubes);
            PolyMesh[] polyMeshes = createPolyMeshes(sourceBone, bone, texWidth, texHeight);
            result[i] = new TreeBoneDefinition(bone.name, bone.index, parentIndex, childArray,
                    bone.pivotX, bone.pivotY, bone.pivotZ, bone.bindX, bone.bindY, bone.bindZ,
                    bone.bindRotation, bone.bindEulerRotation, parseLocators(sourceBone, bone), cubes, ownCubeBounds, polyMeshes,
                    cubes.length > 0, TreeGeometryWriter.hasTriangles(polyMeshes));
        }
        return result;
    }

    private static ICube[] createCubes(BonesItem boneItem, CompileBone bone, int texWidth, int texHeight) {
        if (boneItem.getCubes() == null) return new ICube[0];
        ArrayList<ICube> cubes = new ArrayList<>();
        for (CubesItem cube : boneItem.getCubes()) {
            float[] size = Arrays.copyOf(cube.getSize(), 3);
            float[] origin = Arrays.copyOf(cube.getOrigin(), 3);
            origin[0] = -(origin[0] + size[0]);
            Quaternionf rotation = null;
            float[] pivot = cube.getPivot() != null ? Arrays.copyOf(cube.getPivot(), 3) : null;
            if (pivot != null) pivot[0] = -pivot[0];
            if (cube.getRotation() != null) {
                float[] cubeRotation = Arrays.copyOf(cube.getRotation(), 3);
                cubeRotation[0] = (float) -Math.toRadians(cubeRotation[0]);
                cubeRotation[1] = (float) -Math.toRadians(cubeRotation[1]);
                cubeRotation[2] = (float) Math.toRadians(cubeRotation[2]);
                rotation = new Quaternionf().rotateZYX(cubeRotation[2], cubeRotation[1], cubeRotation[0]);
            }
            float inflate = cube.getInflate();
            float x = (origin[0] - bone.absolutePivotX - inflate) / 16.0f;
            float y = (origin[1] - bone.absolutePivotY - inflate) / 16.0f;
            float z = (origin[2] - bone.absolutePivotZ - inflate) / 16.0f;
            float width = (size[0] + inflate * 2.0f) / 16.0f;
            float height = (size[1] + inflate * 2.0f) / 16.0f;
            float depth = (size[2] + inflate * 2.0f) / 16.0f;
            if (pivot != null) {
                pivot[0] = (pivot[0] - bone.absolutePivotX) / 16.0f;
                pivot[1] = (pivot[1] - bone.absolutePivotY) / 16.0f;
                pivot[2] = (pivot[2] - bone.absolutePivotZ) / 16.0f;
            }
            if (cube.getFaceUv() == null) {
                float[] uv = cube.getUv();
                boolean mirror = cube.isHasMirror() ? cube.isMirror() : boneItem.isMirror();
                cubes.add(new CubeBox(x, y, z, width, height, depth, 0.0f, createBoxUvs(uv[0], uv[1], size[0], size[1], size[2], texWidth, texHeight), mirror ? UV_ORDER_MIRRORED : UV_ORDER_NO_MIRROR, pivot, rotation));
            } else {
                cubes.add(new CubePerFace(x, y, z, width, height, depth, 0.0f, createPerFaceUvs(cube.getFaceUv(), texWidth, texHeight), createEmptyFacesMask(cube.getFaceUv()), pivot, rotation));
            }
        }
        return cubes.toArray(ICube[]::new);
    }

    @Nullable
    private static LocalCubeBounds ownCubeBounds(ICube[] cubes) {
        LocalCubeBounds.Builder bounds = LocalCubeBounds.builder();
        for (ICube cube : cubes) {
            Matrix4f cubeTransform = new Matrix4f();
            if (cube.hasRotation()) {
                float[] pivot = cube.pivot();
                Quaternionf rotation = cube.rotation();
                cubeTransform.translate(pivot[0], pivot[1], pivot[2]);
                cubeTransform.rotate(rotation);
                cubeTransform.translate(-pivot[0], -pivot[1], -pivot[2]);
            }
            bounds.includeCube(cube.x(), cube.y(), cube.z(), cube.width(), cube.height(), cube.depth(), cubeTransform);
        }
        return bounds.build();
    }

    private static PolyMesh[] createPolyMeshes(BonesItem boneItem, CompileBone bone, int texWidth, int texHeight) {
        PolyMeshItem polyMesh = boneItem.getPolyMesh();
        if (polyMesh == null) return new PolyMesh[0];
        return new PolyMesh[]{createPolyMesh(polyMesh, bone, texWidth, texHeight)};
    }

    private static float[] createBoxUvs(float texOffX, float texOffY, float width, float height, float depth, float texWidth, float texHeight) {
        float dx = (float) Math.floor(width);
        float dy = (float) Math.floor(height);
        float dz = (float) Math.floor(depth);
        float scaleU = texWidth == 0 ? 0 : 1.0f / texWidth;
        float scaleV = texHeight == 0 ? 0 : 1.0f / texHeight;
        float[] uvs = new float[9];
        uvs[0] = scaleU * texOffX;
        uvs[1] = scaleU * (texOffX + dz);
        uvs[2] = scaleU * (texOffX + dz + dx);
        uvs[3] = scaleU * (texOffX + dz + dx + dx);
        uvs[4] = scaleU * (texOffX + dz + dx + dz);
        uvs[5] = scaleU * (texOffX + dz + dx + dz + dx);
        uvs[6] = scaleV * texOffY;
        uvs[7] = scaleV * (texOffY + dz);
        uvs[8] = scaleV * (texOffY + dz + dy);
        return uvs;
    }

    private static float[][] createPerFaceUvs(FaceUVsItem faces, float texWidth, float texHeight) {
        float[][] uvs = new float[6][8];
        for (Direction direction : Direction.values()) {
            FaceItem face = faces.getFace(direction);
            if (face == null || isEmptyFace(face)) continue;
            float[] rotatedUVs = getRotatedUVs(face, texWidth, texHeight);
            System.arraycopy(rotatedUVs, 0, uvs[direction.ordinal()], 0, 8);
        }
        return uvs;
    }

    private static int createEmptyFacesMask(FaceUVsItem faces) {
        int mask = 0;
        for (Direction direction : Direction.values()) {
            FaceItem face = faces.getFace(direction);
            if (face == null || isEmptyFace(face)) mask |= 1 << direction.ordinal();
        }
        return mask;
    }

    private static boolean isEmptyFace(FaceItem face) {
        float[] uv = face.getUv();
        float[] uvSize = face.getUvSize();
        return uv == null || uv.length < 2 || uvSize == null || uvSize.length < 2
                || Math.abs(uvSize[0]) < 1.0E-9f || Math.abs(uvSize[1]) < 1.0E-9f;
    }

    private static float[] getRotatedUVs(FaceItem face, float texWidth, float texHeight) {
        return face.getRotatedUVs(texWidth, texHeight);
    }

    private static PolyMesh createPolyMesh(PolyMeshItem polyMesh, CompileBone bone, float texWidth, float texHeight) {
        ArrayList<PolyMesh.Triangle> triangles = new ArrayList<>();
        float[][] positions = polyMesh.getPositions();
        JsonElement polys = polyMesh.getPolys();
        if (positions != null && polys != null && !polys.isJsonNull()) {
            bakePolys(polyMesh, bone, texWidth, texHeight, positions, polyMesh.getNormals(), polyMesh.getUvs(), polys, triangles);
        }
        PolyMesh.Triangle[] triangleArray = triangles.toArray(PolyMesh.Triangle[]::new);
        if (triangleArray.length == 0) return new PolyMesh(triangleArray, 0, 0, 0, 0, 0, 0);

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (PolyMesh.Triangle triangle : triangleArray) {
            for (int i = 0; i < 3; i++) {
                PolyMesh.Vertex vertex = triangle.vertex(i);
                minX = Math.min(minX, vertex.x());
                minY = Math.min(minY, vertex.y());
                minZ = Math.min(minZ, vertex.z());
                maxX = Math.max(maxX, vertex.x());
                maxY = Math.max(maxY, vertex.y());
                maxZ = Math.max(maxZ, vertex.z());
            }
        }
        return new PolyMesh(triangleArray, minX, minY, minZ, maxX - minX, maxY - minY, maxZ - minZ);
    }

    private static void bakePolys(PolyMeshItem polyMesh, CompileBone bone, float texWidth, float texHeight, float[][] positions, float[][] normals,
                                  float[][] uvs, JsonElement polys, List<PolyMesh.Triangle> triangles) {
        if (polys.isJsonPrimitive() && polys.getAsJsonPrimitive().isString()) {
            String mode = polys.getAsString();
            if ("tri_list".equals(mode)) bakeListMode(polyMesh, bone, texWidth, texHeight, positions, normals, uvs, triangles, 3);
            else if ("quad_list".equals(mode)) bakeListMode(polyMesh, bone, texWidth, texHeight, positions, normals, uvs, triangles, 4);
        } else if (polys.isJsonArray()) {
            for (JsonElement polyElement : polys.getAsJsonArray()) {
                if (polyElement != null && polyElement.isJsonArray()) {
                    bakeIndexedPolygon(polyMesh, bone, texWidth, texHeight, positions, normals, uvs, polyElement.getAsJsonArray(), triangles);
                }
            }
        }
    }

    private static void bakeListMode(PolyMeshItem polyMesh, CompileBone bone, float texWidth, float texHeight, float[][] positions, float[][] normals,
                                     float[][] uvs, List<PolyMesh.Triangle> triangles, int stride) {
        for (int i = 0; i + stride - 1 < positions.length; i += stride) {
            ArrayList<PolyMesh.Vertex> polygon = new ArrayList<>(stride);
            for (int j = 0; j < stride; j++) {
                polygon.add(createVertex(polyMesh, bone, texWidth, texHeight, positions, normals, uvs, i + j, i + j, i + j));
            }
            bakePolygon(polygon, triangles);
        }
    }

    private static void bakeIndexedPolygon(PolyMeshItem polyMesh, CompileBone bone, float texWidth, float texHeight, float[][] positions, float[][] normals,
                                           float[][] uvs, JsonArray poly, List<PolyMesh.Triangle> triangles) {
        ArrayList<PolyMesh.Vertex> polygon = new ArrayList<>(poly.size());
        for (JsonElement vertexElement : poly) {
            if (vertexElement == null || !vertexElement.isJsonArray()) continue;
            JsonArray indices = vertexElement.getAsJsonArray();
            if (indices.isEmpty()) continue;
            int positionIndex = indices.get(0).getAsInt();
            int normalIndex = indices.size() > 1 ? indices.get(1).getAsInt() : positionIndex;
            int uvIndex = indices.size() > 2 ? indices.get(2).getAsInt() : positionIndex;
            polygon.add(createVertex(polyMesh, bone, texWidth, texHeight, positions, normals, uvs, positionIndex, normalIndex, uvIndex));
        }
        if (polygon.size() > 1 && samePosition(polygon.get(0), polygon.get(polygon.size() - 1))) polygon.remove(polygon.size() - 1);
        bakePolygon(polygon, triangles);
    }

    private static PolyMesh.Vertex createVertex(PolyMeshItem polyMesh, CompileBone bone, float texWidth, float texHeight, float[][] positions, float[][] normals,
                                                float[][] uvs, int positionIndex, int normalIndex, int uvIndex) {
        float[] position = getArray(positions, positionIndex);
        float x = (-get(position, 0) - bone.absolutePivotX) / 16.0f;
        float y = (get(position, 1) - bone.absolutePivotY) / 16.0f;
        float z = (get(position, 2) - bone.absolutePivotZ) / 16.0f;

        float nx = 0;
        float ny = 0;
        float nz = 0;
        if (normals != null && normalIndex >= 0 && normalIndex < normals.length) {
            float[] normal = normals[normalIndex];
            nx = -get(normal, 0);
            ny = get(normal, 1);
            nz = get(normal, 2);
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length > 1.0E-6f) {
                nx /= length;
                ny /= length;
                nz /= length;
            }
        }

        float u = 0;
        float v = 0;
        if (uvs != null && uvIndex >= 0 && uvIndex < uvs.length) {
            float[] uv = uvs[uvIndex];
            u = get(uv, 0);
            v = get(uv, 1);
            if (!polyMesh.isNormalizedUvs()) {
                u = texWidth == 0 ? 0 : u / texWidth;
                v = texHeight == 0 ? 0 : v / texHeight;
            }
            v = 1.0f - v;
        }
        return new PolyMesh.Vertex(x, y, z, u, v, nx, ny, nz);
    }

    private static void bakePolygon(List<PolyMesh.Vertex> polygon, List<PolyMesh.Triangle> triangles) {
        if (polygon.size() < 3) return;
        if (polygon.size() == 4) {
            bakeTriangle(polygon.get(0), polygon.get(1), polygon.get(2), triangles);
            bakeTriangle(polygon.get(2), polygon.get(3), polygon.get(0), triangles);
            return;
        }
        for (int i = 1; i + 1 < polygon.size(); i++) {
            bakeTriangle(polygon.get(0), polygon.get(i), polygon.get(i + 1), triangles);
        }
    }

    private static void bakeTriangle(PolyMesh.Vertex a, PolyMesh.Vertex b, PolyMesh.Vertex c, List<PolyMesh.Triangle> triangles) {
        if (samePosition(a, b) || samePosition(b, c) || samePosition(c, a)) return;
        Vector3f normal = computeNormal(a, b, c);
        triangles.add(new PolyMesh.Triangle(withFallbackNormal(a, normal), withFallbackNormal(b, normal), withFallbackNormal(c, normal)));
    }

    private static PolyMesh.Vertex withFallbackNormal(PolyMesh.Vertex vertex, Vector3f normal) {
        if (vertex.nx() * vertex.nx() + vertex.ny() * vertex.ny() + vertex.nz() * vertex.nz() > 1.0E-12f) return vertex;
        return new PolyMesh.Vertex(vertex.x(), vertex.y(), vertex.z(), vertex.u(), vertex.v(), normal.x, normal.y, normal.z);
    }

    private static Vector3f computeNormal(PolyMesh.Vertex a, PolyMesh.Vertex b, PolyMesh.Vertex c) {
        Vector3f edge1 = new Vector3f(b.x() - a.x(), b.y() - a.y(), b.z() - a.z());
        Vector3f edge2 = new Vector3f(c.x() - a.x(), c.y() - a.y(), c.z() - a.z());
        Vector3f normal = edge1.cross(edge2, new Vector3f());
        if (normal.lengthSquared() < 1.0E-12f) normal.set(0, 1, 0);
        else normal.normalize();
        return normal;
    }

    private static boolean samePosition(PolyMesh.Vertex first, PolyMesh.Vertex second) {
        return Math.abs(first.x() - second.x()) < 1.0E-6f
                && Math.abs(first.y() - second.y()) < 1.0E-6f
                && Math.abs(first.z() - second.z()) < 1.0E-6f;
    }

    private static float[] getArray(float[][] values, int index) {
        if (index < 0 || index >= values.length || values[index] == null) return new float[0];
        return values[index];
    }

    private static float get(float[] values, int index) {
        return values != null && index >= 0 && index < values.length ? values[index] : 0;
    }

    private static TreeBoneDefinition[] applySubtreeGeometryFlags(TreeBoneDefinition[] definitions) {
        boolean[] hasQuadsInTree = new boolean[definitions.length];
        boolean[] hasVerticesInTree = new boolean[definitions.length];
        boolean[] visited = new boolean[definitions.length];
        for (int i = 0; i < definitions.length; i++) {
            if (definitions[i].parentIndex() < 0) computeFlags(definitions, i, hasQuadsInTree, hasVerticesInTree, visited);
        }
        TreeBoneDefinition[] result = new TreeBoneDefinition[definitions.length];
        for (int i = 0; i < definitions.length; i++) {
            TreeBoneDefinition def = definitions[i];
            result[i] = new TreeBoneDefinition(def.name(), def.index(), def.parentIndex(), def.children(),
                    def.pivotX(), def.pivotY(), def.pivotZ(), def.bindX(), def.bindY(), def.bindZ(),
                    def.bindRotation(), def.bindEulerRotation(), def.locators(), def.cubes(), def.ownCubeBounds(), def.polyMeshes(),
                    hasQuadsInTree[i], hasVerticesInTree[i]);
        }
        return result;
    }

    private static void computeFlags(TreeBoneDefinition[] definitions, int index, boolean[] quads, boolean[] vertices, boolean[] visited) {
        if (visited[index]) return;
        visited[index] = true;
        boolean q = definitions[index].hasQuads();
        boolean v = definitions[index].hasVertices();
        for (int child : definitions[index].children()) {
            computeFlags(definitions, child, quads, vertices, visited);
            q |= quads[child];
            v |= vertices[child];
        }
        quads[index] = q;
        vertices[index] = v;
    }

    private static Pose createBindPose(TreeBoneDefinition[] bones) {
        PoseBuilder poseBuilder = new ArrayPoseBuilder();
        for (TreeBoneDefinition bone : bones) {
            poseBuilder.addBoneTransform(new BoneTransform(bone.index(), new Vector3f(bone.bindX(), bone.bindY(), bone.bindZ()),
                    new BindRotationView(bone.bindRotation(), bone.bindEulerRotation()), new Vector3f(1, 1, 1)));
        }
        return poseBuilder.toPose();
    }

    private static CompileBone[] createCompileBones(BonesItem[] bones) {
        CompileBone[] result = new CompileBone[bones.length];
        for (int i = 0; i < bones.length; i++) {
            BonesItem bone = bones[i];
            CompileBone compileBone = new CompileBone(bone.getName(), i);
            float[] pivot = bone.getPivot() != null ? Arrays.copyOf(bone.getPivot(), 3) : null;
            float[] rotation = bone.getRotation() != null ? Arrays.copyOf(bone.getRotation(), 3) : null;
            if (pivot != null) {
                compileBone.absolutePivotX = -pivot[0];
                compileBone.absolutePivotY = pivot[1];
                compileBone.absolutePivotZ = pivot[2];
                compileBone.pivotX = compileBone.absolutePivotX / 16.0f;
                compileBone.pivotY = compileBone.absolutePivotY / 16.0f;
                compileBone.pivotZ = compileBone.absolutePivotZ / 16.0f;
            }
            if (rotation != null) {
                rotation[0] = (float) -Math.toRadians(rotation[0]);
                rotation[1] = (float) -Math.toRadians(rotation[1]);
                rotation[2] = (float) Math.toRadians(rotation[2]);
                compileBone.bindRotation.rotateZYX(rotation[2], rotation[1], rotation[0]);
                compileBone.bindEulerRotation.set(rotation);
            }
            result[i] = compileBone;
        }
        return result;
    }

    private static Map<String, CompileBone> indexCompileBones(CompileBone[] bones) {
        Map<String, CompileBone> result = new LinkedHashMap<>();
        for (CompileBone bone : bones) result.put(bone.name, bone);
        return result;
    }

    private static void linkCompileBones(BonesItem[] bones, Map<String, CompileBone> compileBones) {
        for (BonesItem bone : bones) {
            CompileBone compileBone = compileBones.get(bone.getName());
            if (bone.getParent() != null) {
                compileBone.parent = compileBones.get(bone.getParent());
            }
        }
        for (CompileBone bone : compileBones.values()) {
            float parentX = bone.parent == null ? 0 : bone.parent.absolutePivotX;
            float parentY = bone.parent == null ? 0 : bone.parent.absolutePivotY;
            float parentZ = bone.parent == null ? 0 : bone.parent.absolutePivotZ;
            bone.bindX = bone.absolutePivotX - parentX;
            bone.bindY = bone.absolutePivotY - parentY;
            bone.bindZ = bone.absolutePivotZ - parentZ;
        }
    }

    private static Map<String, LocatorData> parseLocators(BonesItem item, CompileBone bone) {
        if (item.getLocators() == null || item.getLocators().isEmpty()) return Map.of();
        Map<String, LocatorData> locators = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : item.getLocators().entrySet()) {
            locators.put(entry.getKey(), parseLocator(entry.getValue(), bone));
        }
        return locators;
    }

    private static LocatorData parseLocator(JsonElement element, CompileBone bone) {
        if (element == null || element.isJsonNull()) return LocatorData.EMPTY;
        if (element.isJsonArray()) {
            float[] absOffset = parseArray(element.getAsJsonArray());
            return new LocatorData(new float[]{(-absOffset[0] - bone.absolutePivotX) / 16.0f, (absOffset[1] - bone.absolutePivotY) / 16.0f, (absOffset[2] - bone.absolutePivotZ) / 16.0f}, new float[3]);
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            float[] absOffset = object.has("offset") ? parseArray(object.getAsJsonArray("offset")) : new float[3];
            float[] rotation = object.has("rotation") ? parseArray(object.getAsJsonArray("rotation")) : new float[3];
            return new LocatorData(new float[]{(-absOffset[0] - bone.absolutePivotX) / 16.0f, (absOffset[1] - bone.absolutePivotY) / 16.0f, (absOffset[2] - bone.absolutePivotZ) / 16.0f},
                    new float[]{-rotation[0], -rotation[1], rotation[2]});
        }
        return LocatorData.EMPTY;
    }

    private static float[] parseArray(JsonArray array) {
        float[] values = new float[3];
        int size = Math.min(array.size(), values.length);
        for (int i = 0; i < size; i++) values[i] = array.get(i).getAsFloat();
        return values;
    }

    private static ModelSource modelSource(BedrockModelPOJO pojo) {
        if (BedrockVersion.isLegacyVersion(pojo)) {
            GeometryModelLegacy legacy = pojo.getGeometryModelLegacy();
            legacy.deco();
            return new ModelSource(legacy.getBones(), legacy.getTextureWidth(), legacy.getTextureHeight(), bounds(legacy.getVisibleBoundsOffset(), legacy.getVisibleBoundsWidth(), legacy.getVisibleBoundsHeight()));
        }
        GeometryModelNew modern = pojo.getGeometryModelNew();
        modern.deco();
        Description description = modern.getDescription();
        int texWidth = description == null ? 0 : description.getTextureWidth();
        int texHeight = description == null ? 0 : description.getTextureHeight();
        AABB bounds = description == null ? null : bounds(description.getVisibleBoundsOffset(), description.getVisibleBoundsWidth(), description.getVisibleBoundsHeight());
        return new ModelSource(modern.getBones(), texWidth, texHeight, bounds);
    }

    private static AABB bounds(@Nullable float[] offset, float widthValue, float heightValue) {
        if (offset == null) return null;
        float width = widthValue / 2.0f;
        float height = heightValue / 2.0f;
        return new AABB(offset[0] - width, offset[1] - height, offset[2] - width, offset[0] + width, offset[1] + height, offset[2] + width);
    }

    private record ModelSource(BonesItem[] bones, int texWidth, int texHeight, AABB renderBoundingBox) {}

    private static final class CompileBone {
        final String name;
        final int index;
        CompileBone parent;
        float absolutePivotX;
        float absolutePivotY;
        float absolutePivotZ;
        float pivotX;
        float pivotY;
        float pivotZ;
        float bindX;
        float bindY;
        float bindZ;
        final Quaternionf bindRotation = new Quaternionf();
        final Vector3f bindEulerRotation = new Vector3f();

        private CompileBone(String name, int index) {
            this.name = name;
            this.index = index;
        }
    }

    private record BindRotationView(Quaternionfc quaternion, Vector3fc euler) implements RotationView {
        private BindRotationView(Quaternionfc quaternion, Vector3fc euler) {
            this.quaternion = new Quaternionf(quaternion);
            this.euler = new Vector3f(euler);
        }

        @Override public Vector3fc asEulerAngle() { return euler; }
        @Override public Quaternionfc asQuaternion() { return quaternion; }
    }
}
