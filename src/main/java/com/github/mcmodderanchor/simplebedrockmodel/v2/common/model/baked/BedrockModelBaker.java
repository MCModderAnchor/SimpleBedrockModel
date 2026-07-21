package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.LocatorData;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.maydaymemory.mae.basic.ArrayPoseBuilder;
import com.maydaymemory.mae.basic.BoneTransform;
import com.maydaymemory.mae.basic.Pose;
import com.maydaymemory.mae.basic.PoseBuilder;
import com.maydaymemory.mae.basic.RotationView;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.*;
import java.util.regex.Pattern;

public class BedrockModelBaker {
    public static BakedBedrockModel bake(BedrockModelPOJO pojo, BakerOptions options) {
        ModelSource source = modelSource(pojo);
        if (source.bones == null || source.bones.length == 0) {
            return new BakedBedrockModel(new BakedBoneDefinition[0], Map.of(), new BakedGeometryChunk[0], new BakedCubeGeometry[0], options.retainCubeGeometry(),
                    new BoneLocator[0], Map.of(), new QueryTransform[0], Map.of(), new ArrayPoseBuilder().toPose(), source.renderBoundingBox);
        }

        Map<String, CompileBone> compileBones = createCompileBones(source.bones);
        linkCompileBones(source.bones, compileBones);

        Set<String> runtimeBoneNames = collectRuntimeBones(compileBones, options);
        RuntimeIndex runtimeIndex = createRuntimeIndex(source.bones, compileBones, runtimeBoneNames);
        BakedBoneDefinition[] bakedBoneDefinitions = createStaticBones(runtimeIndex);

        Pose bindPose = createBindPose(bakedBoneDefinitions);
        LocatorResult locatorResult = createLocators(source.bones, compileBones);
        QueryResult queryResult = createQueryTransforms(source.bones, compileBones, runtimeIndex);
        BedrockGeometryBaker.BakeResult bakeResult = BedrockGeometryBaker.bake(source.bones, compileBones, runtimeIndex, source.texWidth, source.texHeight, options);

        bakedBoneDefinitions = applySubtreeGeometryFlags(bakedBoneDefinitions, bakeResult.chunks(), runtimeIndex);

        if (options.debugFoldedTree()) {
            SimpleBedrockModel.LOGGER.info("\n{}", describeFoldedTree(source.bones, compileBones, runtimeBoneNames, queryResult, options));
        }

        return new BakedBedrockModel(bakedBoneDefinitions, runtimeIndex.indexByName, bakeResult.chunks(),
                bakeResult.cubeGeometry(), options.retainCubeGeometry(), locatorResult.locators, locatorResult.locatorByName,
                queryResult.queryTransforms, queryResult.queryTransformByName, bindPose, source.renderBoundingBox);
    }

    private static ModelSource modelSource(BedrockModelPOJO pojo) {
        if (BedrockVersion.isLegacyVersion(pojo)) {
            GeometryModelLegacy legacy = pojo.getGeometryModelLegacy();
            legacy.deco();
            return new ModelSource(legacy.getBones(), legacy.getTextureWidth(), legacy.getTextureHeight(), bounds(
                    legacy.getVisibleBoundsOffset(), legacy.getVisibleBoundsWidth(), legacy.getVisibleBoundsHeight()));
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
        float offsetX = offset[0];
        float offsetY = offset[1];
        float offsetZ = offset[2];
        float width = widthValue / 2.0f;
        float height = heightValue / 2.0f;
        return new AABB(offsetX - width, offsetY - height, offsetZ - width, offsetX + width, offsetY + height, offsetZ + width);
    }

    private static Map<String, CompileBone> createCompileBones(BonesItem[] bones) {
        Map<String, CompileBone> result = new LinkedHashMap<>();
        for (BonesItem bone : bones) {
            CompileBone compileBone = new CompileBone(bone.getName());
            float[] pivot = bone.getPivot() != null ? Arrays.copyOf(bone.getPivot(), 3) : null;
            float[] rotation = bone.getRotation() != null ? Arrays.copyOf(bone.getRotation(), 3) : null;
            if (pivot != null) {
                compileBone.pivotX = -pivot[0] / 16.0f;
                compileBone.pivotY = pivot[1] / 16.0f;
                compileBone.pivotZ = pivot[2] / 16.0f;
            }
            if (rotation != null) {
                rotation[0] = (float) -Math.toRadians(rotation[0]);
                rotation[1] = (float) -Math.toRadians(rotation[1]);
                rotation[2] = (float) Math.toRadians(rotation[2]);
                compileBone.bindRotation.rotateZYX(rotation[2], rotation[1], rotation[0]);
                compileBone.bindEulerRotation.set(rotation);
            }
            result.put(bone.getName(), compileBone);
        }
        return result;
    }

    private static void linkCompileBones(BonesItem[] bones, Map<String, CompileBone> compileBones) {
        for (BonesItem bone : bones) {
            CompileBone compileBone = compileBones.get(bone.getName());
            String parentName = bone.getParent();
            if (parentName != null) {
                compileBone.parent = compileBones.get(parentName);
                if (compileBone.parent != null) {
                    compileBone.parent.children.add(compileBone);
                }
            }
        }
    }

    private static Set<String> collectRuntimeBones(Map<String, CompileBone> compileBones, BakerOptions options) {
        Set<String> names = new LinkedHashSet<>();
        names.addAll(options.animatedBones());
        names.addAll(options.preservedBones());
        names.removeIf(name -> !compileBones.containsKey(name));
        names.addAll(collectPatternPreservedBones(compileBones, options));
        return names;
    }

    private static Set<String> collectPatternPreservedBones(Map<String, CompileBone> compileBones, BakerOptions options) {
        if (options.preservedBonePatterns().isEmpty()) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (String boneName : compileBones.keySet()) {
            for (Pattern pattern : options.preservedBonePatterns()) {
                if (pattern.matcher(boneName).matches()) {
                    names.add(boneName);
                    break;
                }
            }
        }
        return names;
    }

    private static RuntimeIndex createRuntimeIndex(BonesItem[] bones, Map<String, CompileBone> compileBones, Set<String> runtimeBoneNames) {
        Map<String, Integer> indexByName = new LinkedHashMap<>();
        ArrayList<CompileBone> runtimeBones = new ArrayList<>();
        for (BonesItem item : bones) {
            if (!runtimeBoneNames.contains(item.getName())) continue;
            CompileBone bone = compileBones.get(item.getName());
            bone.runtimeIndex = runtimeBones.size();
            indexByName.put(bone.name, bone.runtimeIndex);
            runtimeBones.add(bone);
        }
        return new RuntimeIndex(runtimeBones, indexByName);
    }

    private static BakedBoneDefinition[] createStaticBones(RuntimeIndex runtimeIndex) {
        BakedBoneDefinition[] result = new BakedBoneDefinition[runtimeIndex.bones.size()];
        ArrayList<List<Integer>> children = new ArrayList<>(runtimeIndex.bones.size());
        for (int i = 0; i < runtimeIndex.bones.size(); i++) children.add(new ArrayList<>());
        for (CompileBone bone : runtimeIndex.bones) {
            int parentIndex = runtimeParentIndex(bone);
            if (parentIndex >= 0) children.get(parentIndex).add(bone.runtimeIndex);
        }
        for (CompileBone bone : runtimeIndex.bones) {
            int parentIndex = runtimeParentIndex(bone);
            Matrix4f bindLocalTransform = bindLocalTransformToRuntimeParent(bone, parentIndex, runtimeIndex);
            Matrix3f bindLocalNormalTransform = bindLocalTransform == null ? null : new Matrix3f(bindLocalTransform);
            int[] childArray = children.get(bone.runtimeIndex).stream().mapToInt(Integer::intValue).toArray();
            float bindX = bindXToRuntimeParent(bone, parentIndex, runtimeIndex);
            float bindY = bindYToRuntimeParent(bone, parentIndex, runtimeIndex);
            float bindZ = bindZToRuntimeParent(bone, parentIndex, runtimeIndex);
            Matrix4f foldedParentTransform = foldedParentTransform(bindLocalTransform, bindX, bindY, bindZ, bone);
            Matrix3f foldedParentNormalTransform = foldedParentTransform == null ? null : new Matrix3f(foldedParentTransform);
            result[bone.runtimeIndex] = new BakedBoneDefinition(bone.name, bone.runtimeIndex, parentIndex, childArray,
                    bone.pivotX, bone.pivotY, bone.pivotZ, bindX, bindY, bindZ, bindLocalTransform, bindLocalNormalTransform,
                    foldedParentTransform, foldedParentNormalTransform, bone.bindRotation, bone.bindEulerRotation, false, false);
        }
        return result;
    }

    private static BakedBoneDefinition[] applySubtreeGeometryFlags(BakedBoneDefinition[] definitions, BakedGeometryChunk[] chunks, RuntimeIndex runtimeIndex) {
        boolean[] selfQuads = new boolean[definitions.length];
        boolean[] selfVertices = new boolean[definitions.length];
        for (BakedGeometryChunk chunk : chunks) {
            int boneIndex = chunk.attachBoneIndex();
            if (boneIndex < 0 || boneIndex >= definitions.length) continue;
            if (chunk.hasQuads()) selfQuads[boneIndex] = true;
            if (chunk.hasVertices()) selfVertices[boneIndex] = true;
        }
        boolean[] hasQuadsInTree = new boolean[definitions.length];
        boolean[] hasVerticesInTree = new boolean[definitions.length];
        boolean[] visited = new boolean[definitions.length];
        for (int i = 0; i < definitions.length; i++) {
            if (definitions[i].parentIndex() < 0) {
                computeSubtreeGeometryFlags(definitions, i, selfQuads, selfVertices, hasQuadsInTree, hasVerticesInTree, visited);
            }
        }
        BakedBoneDefinition[] result = new BakedBoneDefinition[definitions.length];
        for (int i = 0; i < definitions.length; i++) {
            BakedBoneDefinition def = definitions[i];
            result[i] = new BakedBoneDefinition(def.name(), def.index(), def.parentIndex(), def.children(),
                    def.pivotX(), def.pivotY(), def.pivotZ(), def.bindX(), def.bindY(), def.bindZ(),
                    def.bindLocalTransform(), def.bindLocalNormalTransform(), def.foldedParentTransform(), def.foldedParentNormalTransform(),
                    def.bindRotation(), def.bindEulerRotation(), hasQuadsInTree[i], hasVerticesInTree[i]);
        }
        return result;
    }

    private static void computeSubtreeGeometryFlags(BakedBoneDefinition[] definitions, int boneIndex,
                                                    boolean[] selfQuads, boolean[] selfVertices,
                                                    boolean[] hasQuadsInTree, boolean[] hasVerticesInTree,
                                                    boolean[] visited) {
        if (visited[boneIndex]) return;
        visited[boneIndex] = true;
        boolean q = selfQuads[boneIndex];
        boolean v = selfVertices[boneIndex];
        for (int childIndex : definitions[boneIndex].children()) {
            computeSubtreeGeometryFlags(definitions, childIndex, selfQuads, selfVertices, hasQuadsInTree, hasVerticesInTree, visited);
            q |= hasQuadsInTree[childIndex];
            v |= hasVerticesInTree[childIndex];
        }
        hasQuadsInTree[boneIndex] = q;
        hasVerticesInTree[boneIndex] = v;
    }

    private static int runtimeParentIndex(CompileBone bone) {
        CompileBone parent = bone.parent;
        while (parent != null) {
            if (parent.runtimeIndex >= 0) return parent.runtimeIndex;
            parent = parent.parent;
        }
        return -1;
    }

    private static float bindXToRuntimeParent(CompileBone bone, int parentIndex, RuntimeIndex runtimeIndex) {
        float parentPivotX = parentIndex < 0 ? 0.0f : runtimeIndex.bones.get(parentIndex).pivotX;
        return (bone.pivotX - parentPivotX) * 16.0f;
    }

    private static float bindYToRuntimeParent(CompileBone bone, int parentIndex, RuntimeIndex runtimeIndex) {
        float parentPivotY = parentIndex < 0 ? 0.0f : runtimeIndex.bones.get(parentIndex).pivotY;
        return (bone.pivotY - parentPivotY) * 16.0f;
    }

    private static float bindZToRuntimeParent(CompileBone bone, int parentIndex, RuntimeIndex runtimeIndex) {
        float parentPivotZ = parentIndex < 0 ? 0.0f : runtimeIndex.bones.get(parentIndex).pivotZ;
        return (bone.pivotZ - parentPivotZ) * 16.0f;
    }

    @Nullable
    private static Matrix4f foldedParentTransform(@Nullable Matrix4f bindLocalTransform, float bindX, float bindY, float bindZ, CompileBone bone) {
        Matrix4f fullBind = bindLocalTransform == null ? new Matrix4f() : new Matrix4f(bindLocalTransform);
        Matrix4f selfBind = createSelfBindTransform(bindX, bindY, bindZ, bone.pivotX, bone.pivotY, bone.pivotZ,
                bone.bindRotation, 1, 1, 1);
        Matrix4f foldedParent = fullBind.mul(selfBind.invert(new Matrix4f()), new Matrix4f());
        return isIdentity(foldedParent) ? null : foldedParent;
    }

    private static Matrix4f createSelfBindTransform(float x, float y, float z, float pivotX, float pivotY, float pivotZ,
                                                    Quaternionf rotation, float xScale, float yScale, float zScale) {
        Matrix4f matrix = new Matrix4f();
        if (x != 0 || y != 0 || z != 0) {
            matrix.translate(x / 16.0F, y / 16.0F, z / 16.0F);
        }
        matrix.translate(pivotX, pivotY, pivotZ);
        matrix.rotate(rotation);
        matrix.scale(xScale, yScale, zScale);
        matrix.translate(-pivotX, -pivotY, -pivotZ);
        return matrix;
    }

    @Nullable
    private static Matrix4f bindLocalTransformToRuntimeParent(CompileBone bone, int parentIndex, RuntimeIndex runtimeIndex) {
        Matrix4f sourceGlobal = globalBindMatrix(bone);
        Matrix4f transform;
        if (parentIndex < 0) {
            transform = sourceGlobal;
        } else {
            Matrix4f parentInverse = globalBindMatrix(runtimeIndex.bones.get(parentIndex)).invert(new Matrix4f());
            transform = parentInverse.mul(sourceGlobal, new Matrix4f());
        }
        return isIdentity(transform) ? null : transform;
    }

    private static boolean isIdentity(Matrix4f matrix) {
        return matrix.equals(new Matrix4f(), 1.0E-6f);
    }

    private static Pose createBindPose(BakedBoneDefinition[] bones) {
        PoseBuilder poseBuilder = new ArrayPoseBuilder();
        for (BakedBoneDefinition bone : bones) {
            poseBuilder.addBoneTransform(new BoneTransform(bone.index(), new Vector3f(bone.bindX(), bone.bindY(), bone.bindZ()),
                    new BindRotationView(bone.bindRotation(), bone.bindEulerRotation()),
                    new Vector3f(1.0f, 1.0f, 1.0f)));
        }
        return poseBuilder.toPose();
    }

    private static LocatorResult createLocators(BonesItem[] bones, Map<String, CompileBone> compileBones) {
        ArrayList<BoneLocator> locators = new ArrayList<>();
        Map<String, BoneLocator> locatorByName = new HashMap<>();
        for (BonesItem item : bones) {
            if (item.getLocators() == null || item.getLocators().isEmpty()) continue;
            CompileBone bone = compileBones.get(item.getName());
            if (bone.runtimeIndex < 0) continue;
            for (Map.Entry<String, JsonElement> entry : item.getLocators().entrySet()) {
                BoneLocator locator = new BoneLocator(entry.getKey(), bone.runtimeIndex, parseLocator(entry.getValue(), bone));
                locators.add(locator);
                locatorByName.put(entry.getKey(), locator);
            }
        }
        return new LocatorResult(locators.toArray(BoneLocator[]::new), locatorByName);
    }

    private static QueryResult createQueryTransforms(BonesItem[] bones, Map<String, CompileBone> compileBones, RuntimeIndex runtimeIndex) {
        ArrayList<QueryTransform> transforms = new ArrayList<>();
        Map<String, QueryTransform> transformByName = new LinkedHashMap<>();
        for (BonesItem item : bones) {
            CompileBone bone = compileBones.get(item.getName());
            if (bone.runtimeIndex >= 0) {
                continue;
            }
            int attachIndex = nearestRuntimeAncestorOrSelf(bone);
            Matrix4f boneToAttach = localTransformFromAttach(bone, runtimeIndex);
            QueryTransform queryBone = new QueryTransform(bone.name, attachIndex, boneToAttach);
            transforms.add(queryBone);
            transformByName.putIfAbsent(bone.name, queryBone);
            if (item.getLocators() == null || item.getLocators().isEmpty()) {
                continue;
            }
            for (Map.Entry<String, JsonElement> entry : item.getLocators().entrySet()) {
                LocatorData locator = parseLocator(entry.getValue(), bone);
                Matrix4f queryLocal = new Matrix4f(boneToAttach).mul(buildLocatorLocalTransform(locator));
                QueryTransform queryLocator = new QueryTransform(entry.getKey(), attachIndex, queryLocal);
                transforms.add(queryLocator);
                transformByName.put(entry.getKey(), queryLocator);
            }
        }
        return new QueryResult(transforms.toArray(QueryTransform[]::new), transformByName);
    }

    static int nearestRuntimeAncestorOrSelf(CompileBone bone) {
        CompileBone current = bone;
        while (current != null) {
            if (current.runtimeIndex >= 0) return current.runtimeIndex;
            current = current.parent;
        }
        return -1;
    }

    static Matrix4f globalBindMatrix(CompileBone bone) {
        if (bone.cachedBindGlobal != null) {
            return new Matrix4f(bone.cachedBindGlobal);
        }
        Matrix4f matrix = new Matrix4f();
        if (bone.parent != null) {
            matrix.set(globalBindMatrix(bone.parent));
        }
        matrix.translate(bone.pivotX, bone.pivotY, bone.pivotZ);
        matrix.rotate(bone.bindRotation);
        matrix.scale(1, 1, 1);
        matrix.translate(-bone.pivotX, -bone.pivotY, -bone.pivotZ);
        bone.cachedBindGlobal = new Matrix4f(matrix);
        return matrix;
    }

    private static LocatorData parseLocator(JsonElement element, CompileBone part) {
        if (element == null || element.isJsonNull()) return LocatorData.EMPTY;
        if (element.isJsonArray()) {
            float[] absOffset = parseLocatorArray(element.getAsJsonArray());
            return new LocatorData(new float[]{-absOffset[0] / 16.0f - part.pivotX, absOffset[1] / 16.0f - part.pivotY, absOffset[2] / 16.0f - part.pivotZ}, new float[3]);
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            float[] absOffset = object.has("offset") ? parseLocatorArray(object.getAsJsonArray("offset")) : new float[3];
            float[] rotation = object.has("rotation") ? parseLocatorArray(object.getAsJsonArray("rotation")) : new float[3];
            return new LocatorData(new float[]{-absOffset[0] / 16.0f - part.pivotX, absOffset[1] / 16.0f - part.pivotY, absOffset[2] / 16.0f - part.pivotZ}, convertLocatorRotation(rotation));
        }
        return LocatorData.EMPTY;
    }

    private static Matrix4f localTransformFromAttach(CompileBone bone, RuntimeIndex runtimeIndex) {
        int attachIndex = nearestRuntimeAncestorOrSelf(bone);
        Matrix4f sourceGlobal = globalBindMatrix(bone);
        if (attachIndex < 0) {
            return sourceGlobal;
        }
        Matrix4f attachInverse = globalBindMatrix(runtimeIndex.bones.get(attachIndex)).invert(new Matrix4f());
        return attachInverse.mul(sourceGlobal, new Matrix4f());
    }

    private static Matrix4f buildLocatorLocalTransform(LocatorData locator) {
        Matrix4f transform = new Matrix4f();
        float[] offset = locator.offset();
        if (offset[0] != 0 || offset[1] != 0 || offset[2] != 0) {
            transform.translate(offset[0], offset[1], offset[2]);
        }
        float[] rotation = locator.rotation();
        if (rotation[0] != 0 || rotation[1] != 0 || rotation[2] != 0) {
            Quaternionf q = new Quaternionf()
                    .rotateZ((float) Math.toRadians(rotation[2]))
                    .rotateY((float) Math.toRadians(rotation[1]))
                    .rotateX((float) Math.toRadians(rotation[0]));
            transform.rotate(q);
        }
        return transform;
    }

    public static String describeFoldedTree(BedrockModelPOJO pojo, BakerOptions options) {
        ModelSource source = modelSource(pojo);
        if (source.bones == null || source.bones.length == 0) {
            return "<empty>";
        }
        Map<String, CompileBone> compileBones = createCompileBones(source.bones);
        linkCompileBones(source.bones, compileBones);
        Set<String> runtimeBoneNames = collectRuntimeBones(compileBones, options);
        RuntimeIndex runtimeIndex = createRuntimeIndex(source.bones, compileBones, runtimeBoneNames);
        QueryResult queryResult = createQueryTransforms(source.bones, compileBones, runtimeIndex);
        return describeFoldedTree(source.bones, compileBones, runtimeBoneNames, queryResult, options);
    }

    private static String describeFoldedTree(BonesItem[] bones, Map<String, CompileBone> compileBones, Set<String> runtimeBoneNames,
                                             QueryResult queryResult, BakerOptions options) {
        StringBuilder builder = new StringBuilder();
        Set<String> runtimeSeeds = new LinkedHashSet<>();
        runtimeSeeds.addAll(options.animatedBones());
        runtimeSeeds.addAll(options.preservedBones());
        runtimeSeeds.addAll(collectPatternPreservedBones(compileBones, options));
        appendFoldedStats(builder, bones, runtimeBoneNames, runtimeSeeds, queryResult);
        builder.append("\nOriginal tree\n");
        builder.append("-------------\n");
        for (BonesItem bone : bones) {
            CompileBone compileBone = compileBones.get(bone.getName());
            if (compileBone != null && compileBone.parent == null) {
                appendOriginalTree(builder, bone.getName(), compileBones, runtimeBoneNames, runtimeSeeds, queryResult, bones, 0);
            }
        }
        builder.append("\nRuntime tree after folding\n");
        builder.append("--------------------------\n");
        boolean hasRuntimeRoot = false;
        for (CompileBone bone : compileBones.values()) {
            if (bone.runtimeIndex >= 0 && runtimeParentIndex(bone) < 0) {
                appendRuntimeTree(builder, bone, compileBones, runtimeSeeds, queryResult, bones, 0, new HashSet<>());
                hasRuntimeRoot = true;
            }
        }
        if (!hasRuntimeRoot) {
            builder.append("<empty>\n");
        }
        return builder.toString();
    }

    private static void appendFoldedStats(StringBuilder builder, BonesItem[] bones, Set<String> runtimeBoneNames,
                                          Set<String> runtimeSeeds, QueryResult queryResult) {
        int geometryBones = 0;
        int locatorBones = 0;
        int runtimeGeometryBones = 0;
        int foldedGeometryBones = 0;
        for (BonesItem bone : bones) {
            boolean hasGeometry = bone.getCubes() != null || bone.getPolyMesh() != null;
            if (hasGeometry) {
                geometryBones++;
                if (runtimeBoneNames.contains(bone.getName())) {
                    runtimeGeometryBones++;
                } else {
                    foldedGeometryBones++;
                }
            }
            if (bone.getLocators() != null && !bone.getLocators().isEmpty()) {
                locatorBones++;
            }
        }
        int originalBones = bones.length;
        int runtimeBones = runtimeBoneNames.size();
        int foldedBones = originalBones - runtimeBones;
        int runtimeSeedBones = 0;
        for (String runtimeBoneName : runtimeBoneNames) {
            if (runtimeSeeds.contains(runtimeBoneName)) {
                runtimeSeedBones++;
            }
        }
        int ancestorBones = runtimeBones - runtimeSeedBones;
        builder.append("Folded bone tree debug\n");
        builder.append("======================\n");
        builder.append("Stats\n");
        builder.append("-----\n");
        builder.append("original bones:         ").append(originalBones).append('\n');
        builder.append("runtime bones:          ").append(runtimeBones).append('\n');
        builder.append("runtime seed bones:     ").append(runtimeSeedBones).append('\n');
        builder.append("runtime ancestor bones: ").append(Math.max(0, ancestorBones)).append('\n');
        builder.append("folded bones:           ").append(foldedBones).append('\n');
        builder.append("geometry bones:         ").append(geometryBones).append('\n');
        builder.append("runtime geometry bones: ").append(runtimeGeometryBones).append('\n');
        builder.append("folded geometry bones:  ").append(foldedGeometryBones).append('\n');
        builder.append("bones with locators:    ").append(locatorBones).append('\n');
        builder.append("query transforms:       ").append(queryResult.queryTransforms.length).append('\n');
        builder.append("query names:            ").append(queryResult.queryTransformByName.size()).append('\n');
        if (originalBones > 0) {
            builder.append("folded ratio:           ").append(String.format(Locale.ROOT, "%.1f%%", foldedBones * 100.0f / originalBones)).append('\n');
        }
    }

    private static void appendOriginalTree(StringBuilder builder, String boneName, Map<String, CompileBone> compileBones,
                                           Set<String> runtimeBoneNames, Set<String> runtimeSeeds, QueryResult queryResult,
                                           BonesItem[] bones, int depth) {
        CompileBone compileBone = compileBones.get(boneName);
        if (compileBone == null) {
            return;
        }
        BonesItem sourceBone = findSourceBone(bones, boneName);
        if (sourceBone == null) {
            return;
        }
        builder.append("  ".repeat(Math.max(0, depth))).append("- ").append(boneName).append(" [");
        ArrayList<String> markers = new ArrayList<>();
        if (runtimeBoneNames.contains(boneName)) {
            markers.add(runtimeSeeds.contains(boneName) ? "runtime" : "ancestor");
        } else {
            markers.add("folded");
        }
        if (sourceBone.getCubes() != null || sourceBone.getPolyMesh() != null) {
            markers.add("geom");
        }
        if (sourceBone.getLocators() != null && !sourceBone.getLocators().isEmpty()) {
            markers.add("locators=" + String.join("|", sourceBone.getLocators().keySet()));
        }
        if (queryResult.queryTransformByName.containsKey(boneName)) {
            markers.add("query");
        }
        builder.append(String.join(", ", markers)).append("]\n");
        for (CompileBone child : compileBone.children) {
            appendOriginalTree(builder, child.name, compileBones, runtimeBoneNames, runtimeSeeds, queryResult, bones, depth + 1);
        }
    }

    private static void appendRuntimeTree(StringBuilder builder, CompileBone bone, Map<String, CompileBone> compileBones,
                                          Set<String> runtimeSeeds, QueryResult queryResult, BonesItem[] bones, int depth,
                                          Set<CompileBone> path) {
        if (!path.add(bone)) {
            builder.append("  ".repeat(Math.max(0, depth))).append("- ").append(bone.name).append(" [cycle]\n");
            return;
        }
        BonesItem sourceBone = findSourceBone(bones, bone.name);
        builder.append("  ".repeat(Math.max(0, depth))).append("- ").append(bone.name).append(" [runtime");
        ArrayList<String> markers = new ArrayList<>();
        if (sourceBone != null) {
            if (sourceBone.getCubes() != null || sourceBone.getPolyMesh() != null) {
                markers.add("geom");
            }
            if (sourceBone.getLocators() != null && !sourceBone.getLocators().isEmpty()) {
                markers.add("locators=" + String.join("|", sourceBone.getLocators().keySet()));
            }
        }
        int queryCount = countQueryChildren(queryResult, bone.runtimeIndex);
        if (queryCount > 0) {
            markers.add("queries=" + queryCount);
        }
        if (runtimeSeeds.contains(bone.name)) {
            markers.add("seed");
        }
        if (!markers.isEmpty()) {
            builder.append(", ").append(String.join(", ", markers));
        }
        builder.append("]\n");
        for (CompileBone child : compileBones.values()) {
            if (child.runtimeIndex >= 0 && child != bone && runtimeParentIndex(child) == bone.runtimeIndex) {
                appendRuntimeTree(builder, child, compileBones, runtimeSeeds, queryResult, bones, depth + 1, path);
            }
        }
        path.remove(bone);
    }

    private static BonesItem findSourceBone(BonesItem[] bones, String boneName) {
        if (bones == null) {
            return null;
        }
        for (BonesItem bone : bones) {
            if (boneName.equals(bone.getName())) {
                return bone;
            }
        }
        return null;
    }

    private static int countQueryChildren(QueryResult queryResult, int runtimeBoneIndex) {
        int count = 0;
        for (QueryTransform transform : queryResult.queryTransforms) {
            if (transform.attachBoneIndex() == runtimeBoneIndex) {
                count++;
            }
        }
        return count;
    }

    private static float[] parseLocatorArray(JsonArray array) {
        float[] values = new float[3];
        int size = Math.min(array.size(), values.length);
        for (int i = 0; i < size; i++) values[i] = array.get(i).getAsFloat();
        return values;
    }

    private static float[] convertLocatorRotation(float[] rotation) {
        return new float[]{-rotation[0], -rotation[1], rotation[2]};
    }

    public record ModelSource(BonesItem[] bones, int texWidth, int texHeight, AABB renderBoundingBox) {}

    public record RuntimeIndex(ArrayList<CompileBone> bones, Map<String, Integer> indexByName) {}

    public record LocatorResult(BoneLocator[] locators, Map<String, BoneLocator> locatorByName) {}

    public record QueryResult(QueryTransform[] queryTransforms, Map<String, QueryTransform> queryTransformByName) {}

    public static final class CompileBone {
        final String name;
        CompileBone parent;
        final ArrayList<CompileBone> children = new ArrayList<>();
        float pivotX;
        float pivotY;
        float pivotZ;
        final Quaternionf bindRotation = new Quaternionf();
        final Vector3f bindEulerRotation = new Vector3f();
        int runtimeIndex = -1;
        Matrix4f cachedBindGlobal;

        private CompileBone(String name) {
            this.name = name;
        }
    }

    public record BindRotationView(Quaternionfc quaternion, Vector3fc euler) implements RotationView {
        public BindRotationView(Quaternionfc quaternion, Vector3fc euler) {
            this.quaternion = new Quaternionf(quaternion);
            this.euler = new Vector3f(euler);
        }

        @Override public Vector3fc asEulerAngle() { return euler; }

        @Override public Quaternionfc asQuaternion() { return quaternion; }
    }
}
