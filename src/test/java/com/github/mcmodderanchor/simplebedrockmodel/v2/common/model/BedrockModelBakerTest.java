package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.GsonUtil;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockAnimationFile;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockModelPOJO;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedCubeGeometry;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakerOptions;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BedrockModelBaker;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BakedModelInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BoneState;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BoneTreeInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.ModelRayTraceResult;
import com.mojang.blaze3d.vertex.PoseStack;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.TreeBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.TreeModelInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.TreeBoneDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.CubeBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class BedrockModelBakerTest {
    @Test
    @DisplayName("real TACZ fixtures keep folded bones queryable")
    void realTaczFixturesKeepFoldedBonesQueryable() throws IOException {
        assertTaczFixture("kar98", "GunFront");
        assertTaczFixture("rpg7", "group3");
        assertTaczFixture("m16a4", "mag_standard");
    }

    @Test
    @DisplayName("preserved bone regex keeps matching bones at runtime")
    void preservedBoneRegexKeepsMatchingBonesAtRuntime() throws IOException {
        BedrockModelPOJO pojo = loadModel("tacz/examples/geo/kar98_geo.json");
        Set<String> animatedBones = loadAnimatedBones("tacz/examples/anim/kar98.animation.json");
        BakerOptions options = new BakerOptions(animatedBones, Set.of(), Set.of(Pattern.compile("Gun.*")), true, false);
        BakedBedrockModel model = BakedBedrockModel.bake(pojo, options);
        BakedModelInstance instance = model.createInstance();

        assertNotNull(instance.getBone("GunFront"));
        assertNotNull(instance.getBone("Gunbody"));
    }

    @Test
    @DisplayName("structured model keeps all source bones at runtime")
    void structuredModelKeepsAllSourceBonesAtRuntime() throws IOException {
        BedrockModelPOJO pojo = loadModel("tacz/examples/geo/kar98_geo.json");
        TreeBedrockModel model = TreeBedrockModel.bake(pojo);
        TreeModelInstance instance = model.createInstance();

        assertEquals(pojo.getGeometryModelNew().getBones().length, model.bones().length);
        assertNotNull(instance.getBone("root"));
        assertNotNull(instance.getBone("GunFront"));
        assertNotNull(instance.getQueryTransform("GunFront"));
    }

    @Test
    @DisplayName("structured bones expose linked parent and child references")
    void structuredBonesExposeLinkedReferences() {
        BedrockModelPOJO pojo = loadInlineModel("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": {"identifier":"geometry.test", "texture_width": 16, "texture_height": 16},
                    "bones": [
                      {"name": "root", "pivot": [0, 0, 0]},
                      {"name": "child", "parent": "root", "pivot": [0, 0, 0]}
                    ]
                  }]
                }
                """);

        TreeBedrockModel model = TreeBedrockModel.bake(pojo);
        TreeBoneDefinition root = model.bone(model.getIndex("root"));
        TreeBoneDefinition child = model.bone(model.getIndex("child"));

        assertNull(root.parent());
        assertEquals(1, root.childBones().length);
        assertSame(child, root.childBones()[0]);
        assertSame(root, child.parent());
        assertEquals(0, child.childBones().length);
    }

    @Test
    @DisplayName("structured model rotates local geometry without extra absolute pivot offset")
    void structuredModelRotatesLocalGeometryWithoutExtraAbsolutePivotOffset() {
        BedrockModelPOJO pojo = loadInlineModel("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": {"identifier":"geometry.test", "texture_width": 16, "texture_height": 16},
                    "bones": [{"name": "root", "pivot": [16, 0, 0], "rotation": [0, 90, 0]}]
                  }]
                }
                """);

        TreeBedrockModel model = TreeBedrockModel.bake(pojo);
        TreeModelInstance instance = model.createInstance();
        Matrix4f transform = instance.getQueryTransform("root");
        assertNotNull(transform);
        Vector3f transformedPivot = transform.transformPosition(new Vector3f());

        assertEquals(-1.0f, transformedPivot.x(), 1.0e-6f);
        assertEquals(0.0f, transformedPivot.y(), 1.0e-6f);
        assertEquals(0.0f, transformedPivot.z(), 1.0e-6f);
    }

    @Test
    @DisplayName("structured model keeps rotated cubes on source bone without virtual bones")
    void structuredModelKeepsRotatedCubesOnSourceBoneWithoutVirtualBones() {
        BedrockModelPOJO pojo = loadInlineModel("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": {"identifier":"geometry.test", "texture_width": 16, "texture_height": 16},
                    "bones": [{
                      "name": "root",
                      "pivot": [0, 0, 0],
                      "cubes": [{"origin": [0, 0, 0], "size": [2, 2, 2], "uv": [0, 0], "pivot": [1, 1, 1], "rotation": [0, 45, 0]}]
                    }]
                  }]
                }
                """);

        TreeBedrockModel model = TreeBedrockModel.bake(pojo);
        TreeBoneDefinition root = model.bone(model.getIndex("root"));

        assertEquals(1, model.bones().length);
        assertEquals(1, root.cubes().length);
        assertInstanceOf(CubeBox.class, root.cubes()[0]);
        assertTrue(root.cubes()[0].hasRotation());
        assertNotNull(root.getOrCreateCache());
    }

    @Test
    @DisplayName("structured model keeps polymesh on source bone cache")
    void structuredModelKeepsPolyMeshOnSourceBoneCache() {
        BedrockModelPOJO pojo = loadInlineModel("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": {"identifier":"geometry.mesh", "texture_width": 16, "texture_height": 16},
                    "bones": [{
                      "name": "mesh_bone",
                      "pivot": [0, 0, 0],
                      "poly_mesh": {
                        "normalized_uvs": true,
                        "positions": [[0,0,0], [1,0,0], [0,1,0]],
                        "normals": [[0,0,1], [0,0,1], [0,0,1]],
                        "uvs": [[0,0], [1,0], [0,1]],
                        "polys": "tri_list"
                      }
                    }]
                  }]
                }
                """);

        TreeBedrockModel model = TreeBedrockModel.bake(pojo);
        TreeBoneDefinition meshBone = model.bone(model.getIndex("mesh_bone"));

        assertEquals(1, model.bones().length);
        assertEquals(1, meshBone.polyMeshes().length);
        assertTrue(meshBone.hasVertices());
        assertNotNull(meshBone.getOrCreateCache());
    }

    @Test
    @DisplayName("baked cube retention is opt-in and groups folded cubes by attachment")
    void bakedCubeRetentionIsOptInAndGroupsFoldedCubesByAttachment() {
        BedrockModelPOJO pojo = loadInlineModel("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": {"identifier":"geometry.retained", "texture_width":16, "texture_height":16},
                    "bones": [
                      {"name":"root", "pivot":[0,0,0]},
                      {"name":"static_parent", "parent":"root", "pivot":[0,0,0], "rotation":[0,30,0],
                       "cubes":[{"origin":[0,0,0], "size":[2,2,2], "uv":[0,0]}]},
                      {"name":"static_child", "parent":"static_parent", "pivot":[0,0,0],
                       "cubes":[{"origin":[16,0,0], "size":[2,2,2], "inflate":0.5, "uv":[0,0],
                                 "pivot":[17,1,1], "rotation":[0,45,0]}]}
                    ]
                  }]
                }
                """);
        BakerOptions options = BakerOptions.ofAnimatedBones(Set.of("root"));
        BakedBedrockModel defaultModel = BakedBedrockModel.bake(pojo, options);
        BakedBedrockModel retainedModel = BakedBedrockModel.bake(pojo, options.withRetainedCubeGeometry(true));

        assertFalse(defaultModel.retainsCubeGeometry());
        assertEquals(0, defaultModel.cubeGeometry().length);
        assertTrue(retainedModel.retainsCubeGeometry());
        assertEquals(defaultModel.bones().length, retainedModel.bones().length);
        assertEquals(defaultModel.chunks().length, retainedModel.chunks().length);
        for (int i = 0; i < defaultModel.chunks().length; i++) {
            assertEquals(defaultModel.chunks()[i].attachBoneIndex(), retainedModel.chunks()[i].attachBoneIndex());
            assertEquals(defaultModel.chunks()[i].quadCount(), retainedModel.chunks()[i].quadCount());
            assertEquals(defaultModel.chunks()[i].vertexCount(), retainedModel.chunks()[i].vertexCount());
        }

        BakedCubeGeometry[] geometry = retainedModel.cubeGeometry();
        assertEquals(1, geometry.length);
        assertEquals(retainedModel.getIndex("root"), geometry[0].attachBoneIndex());
        assertEquals(2, geometry[0].cubes().length);
        assertNotNull(geometry[0].bounds());
        assertEquals(3.0f / 16.0f, geometry[0].cubes()[1].width(), 1.0e-6f);
        assertTrue(geometry[0].bounds().minX() < geometry[0].bounds().maxX());

        float originalM00 = geometry[0].cubes()[0].localTransform().m00();
        Matrix4f mutableCopy = geometry[0].cubes()[0].localTransform().identity();
        assertNotEquals(mutableCopy.m00(), geometry[0].cubes()[0].localTransform().m00(), 1.0e-6f);
        assertEquals(originalM00, geometry[0].cubes()[0].localTransform().m00(), 1.0e-6f);
    }

    @Test
    @DisplayName("tree ray tracing uses rotated direct-cube bounds before exact OBB testing")
    void treeRayTraceUsesRotatedDirectCubeBounds() {
        BedrockModelPOJO pojo = loadInlineModel("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": {"identifier":"geometry.bounds", "texture_width":16, "texture_height":16},
                    "bones": [{
                      "name":"root", "pivot":[0,0,0],
                      "cubes":[{"origin":[0,0,0], "size":[16,16,16], "uv":[0,0],
                                "pivot":[8,8,8], "rotation":[0,45,0]}]
                    }]
                  }]
                }
                """);
        TreeBedrockModel model = TreeBedrockModel.bake(pojo);
        TreeModelInstance instance = model.createInstance();
        TreeBoneDefinition root = model.bone(model.getIndex("root"));

        assertNotNull(root.ownCubeBounds());
        assertTrue(root.ownCubeBounds().minX() < -1.0f);
        assertTrue(root.ownCubeBounds().maxX() > 0.0f);

        Matrix4f identity = new Matrix4f();
        Vec3 origin = Vec3.ZERO;
        assertNull(instance.rayTrace(identity, origin, new Vec3(3.0, 0.5, 0.5), new Vec3(4.0, 0.5, 0.5)));
        assertNull(instance.rayTrace(identity, origin, new Vec3(0.15, -1.0, -0.15), new Vec3(0.15, 2.0, -0.15)));

        ModelRayTraceResult hit = instance.rayTrace(identity, origin, new Vec3(-2.0, 0.5, 0.5), new Vec3(1.0, 0.5, 0.5));
        assertNotNull(hit);
        assertEquals(root.index(), hit.attachmentBoneIndex());
        assertEquals(0, hit.cubeIndex());
    }

    @Test
    @DisplayName("global bone normal and PoseStack helpers stay correct for tree and baked models")
    void globalBoneNormalAndPoseStackHelpersStayCorrect() {
        BedrockModelPOJO pojo = loadInlineModel("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": {"identifier":"geometry.global_normal", "texture_width":16, "texture_height":16},
                    "bones": [
                      {"name":"root", "pivot":[0,0,0]},
                      {"name":"child", "parent":"root", "pivot":[0,0,0]}
                    ]
                  }]
                }
                """);
        TreeModelInstance tree = TreeBedrockModel.bake(pojo).createInstance();
        BakedModelInstance baked = BakedBedrockModel.bake(pojo,
                BakerOptions.ofAnimatedBones(Set.of("root", "child"))).createInstance();

        configureGlobalNormalPose(tree);
        configureGlobalNormalPose(baked);
        int treeChildIndex = tree.getIndex("child");
        int bakedChildIndex = baked.getIndex("child");
        assertGlobalNormalAndPoseStackHelpers(tree, treeChildIndex);
        assertGlobalNormalAndPoseStackHelpers(baked, bakedChildIndex);
        assertPoseStackRestored(poseStack -> tree.renderSingleBonePass(poseStack, treeChildIndex, null,
                0, 0, 1.0f, 1.0f, 1.0f, 1.0f, true, false));
        assertPoseStackRestored(poseStack -> tree.renderSingleBone(poseStack, treeChildIndex, renderType -> null, null, null,
                0, 0, 1.0f, 1.0f, 1.0f, 1.0f, false));
        assertPoseStackRestored(poseStack -> baked.renderSingleBonePass(poseStack, bakedChildIndex, null,
                0, 0, 1.0f, 1.0f, 1.0f, 1.0f, true, false));
        assertPoseStackRestored(poseStack -> baked.renderSingleBone(poseStack, bakedChildIndex, renderType -> null, null, null,
                0, 0, 1.0f, 1.0f, 1.0f, 1.0f, false));
    }

    @Test
    @DisplayName("tree and baked ray tracing agree for animated poses and world transforms")
    void treeAndBakedRayTracingAgreeForAnimatedWorldPose() {
        BedrockModelPOJO pojo = loadInlineModel("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": {"identifier":"geometry.ray", "texture_width":16, "texture_height":16},
                    "bones": [{"name":"root", "pivot":[0,0,0],
                      "cubes":[{"origin":[0,0,0], "size":[16,16,16], "uv":[0,0],
                                "pivot":[8,8,8], "rotation":[0,30,0]}]}]
                  }]
                }
                """);
        TreeBedrockModel treeModel = TreeBedrockModel.bake(pojo);
        TreeModelInstance tree = treeModel.createInstance();
        BakedModelInstance baked = BakedBedrockModel.bake(pojo,
                BakerOptions.ofAnimatedBones(Set.of("root")).withRetainedCubeGeometry(true)).createInstance();
        BoneState treeRoot = tree.getBone("root");
        BoneState bakedRoot = baked.getBone("root");
        assertNotNull(treeRoot);
        assertNotNull(bakedRoot);
        configureRayPose(treeRoot);
        configureRayPose(bakedRoot);

        Matrix4f cubeToModel = new Matrix4f(tree.getGlobalTransform(treeRoot.index()));
        var cube = treeModel.bone(treeRoot.index()).cubes()[0];
        float[] cubePivot = cube.pivot();
        assertNotNull(cubePivot);
        cubeToModel.translate(cubePivot[0], cubePivot[1], cubePivot[2]);
        cubeToModel.rotate(cube.rotation());
        cubeToModel.translate(-cubePivot[0], -cubePivot[1], -cubePivot[2]);
        Vector3f modelStart = new Vector3f(-2.0f, 0.5f, 0.5f).mulPosition(cubeToModel);
        Vector3f modelEnd = new Vector3f(1.0f, 0.5f, 0.5f).mulPosition(cubeToModel);
        Matrix4f modelRotation = new Matrix4f().rotateY((float) Math.toRadians(27.0));
        Vec3 modelOrigin = new Vec3(1_000_000.25, 64.5, -1_000_000.75);
        Vec3 rayStart = worldPosition(modelRotation, modelOrigin, modelStart);
        Vec3 rayEnd = worldPosition(modelRotation, modelOrigin, modelEnd);

        ModelRayTraceResult treeHit = tree.rayTrace(modelRotation, modelOrigin, rayStart, rayEnd);
        ModelRayTraceResult bakedHit = baked.rayTrace(modelRotation, modelOrigin, rayStart, rayEnd);
        assertNotNull(treeHit);
        assertNotNull(bakedHit);
        assertEquals(1.0 / 3.0, treeHit.t(), 1.0e-6);
        assertEquals(treeHit.t(), bakedHit.t(), 1.0e-6);
        assertEquals(treeHit.attachmentBoneIndex(), bakedHit.attachmentBoneIndex());
        assertEquals(0, treeHit.attachmentBoneIndex());
        assertEquals(treeHit.cubeIndex(), bakedHit.cubeIndex());
        assertVecEquals(treeHit.location(), bakedHit.location(), 1.0e-6);
        assertNotNull(treeHit.normal());
        assertNotNull(bakedHit.normal());
        assertNotNull(treeHit.attachmentNormal());
        assertNotNull(bakedHit.attachmentNormal());
        assertVecEquals(treeHit.normal(), bakedHit.normal(), 1.0e-6);
        Vector3f expectedWorldNormal = new Vector3f(-1.0f, 0.0f, 0.0f)
                .mul(new Matrix3f(cubeToModel).invert().transpose())
                .mul(new Matrix3f(modelRotation))
                .normalize();
        assertVecEquals(new Vec3(expectedWorldNormal.x, expectedWorldNormal.y, expectedWorldNormal.z), treeHit.normal(), 1.0e-6);
        assertVecEquals(treeHit.attachmentOffset(), bakedHit.attachmentOffset(), 1.0e-6);
        assertVecEquals(treeHit.attachmentNormal(), bakedHit.attachmentNormal(), 1.0e-6);
        assertMatrixEquals(treeHit.attachmentTransform(), bakedHit.attachmentTransform(), 1.0e-6f);
        assertMatrixEquals(treeHit.cubeTransform(), bakedHit.cubeTransform(), 1.0e-6f);
        Matrix4f mutableCubeTransform = treeHit.cubeTransform().identity();
        assertNotEquals(mutableCubeTransform.m00(), treeHit.cubeTransform().m00(), 1.0e-6f);

        treeRoot.visible = false;
        bakedRoot.visible = false;
        assertNull(tree.rayTrace(modelRotation, modelOrigin, rayStart, rayEnd));
        assertNull(baked.rayTrace(modelRotation, modelOrigin, rayStart, rayEnd));

        treeRoot.visible = true;
        bakedRoot.visible = true;
        treeRoot.xScale = 0.0f;
        bakedRoot.xScale = 0.0f;
        assertNull(tree.rayTrace(modelRotation, modelOrigin, rayStart, rayEnd));
        assertNull(baked.rayTrace(modelRotation, modelOrigin, rayStart, rayEnd));
    }

    @Test
    @DisplayName("baked root geometry ray tracing supports static groups, nearest hits, and inside starts")
    void bakedStaticRootRayTraceUsesNearestClosedSegmentHit() {
        BedrockModelPOJO pojo = loadInlineModel("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": {"identifier":"geometry.static_ray", "texture_width":16, "texture_height":16},
                    "bones": [{"name":"root", "pivot":[0,0,0], "cubes":[
                      {"origin":[0,0,0], "size":[16,16,16], "uv":[0,0]},
                      {"origin":[32,0,0], "size":[16,16,16], "uv":[0,0]}
                    ]}]
                  }]
                }
                """);
        BakedBedrockModel model = BakedBedrockModel.bake(pojo, BakerOptions.defaults().withRetainedCubeGeometry(true));
        assertEquals(0, model.bones().length);
        assertEquals(1, model.cubeGeometry().length);
        assertEquals(-1, model.cubeGeometry()[0].attachBoneIndex());
        BakedModelInstance instance = model.createInstance();
        Matrix4f identity = new Matrix4f();

        ModelRayTraceResult insideHit = instance.rayTrace(identity, Vec3.ZERO, new Vec3(-0.5, 0.5, 0.5), new Vec3(1.0, 0.5, 0.5));
        assertNotNull(insideHit);
        assertEquals(0.0, insideHit.t(), 1.0e-9);
        assertEquals(-1, insideHit.attachmentBoneIndex());
        assertEquals(0, insideHit.cubeIndex());
        assertNull(insideHit.normal());
        assertNull(insideHit.attachmentNormal());
        assertVecEquals(new Vec3(-0.5, 0.5, 0.5), insideHit.attachmentOffset(), 1.0e-6);

        ModelRayTraceResult nearestHit = instance.rayTrace(identity, Vec3.ZERO, new Vec3(1.0, 0.5, 0.5), new Vec3(-4.0, 0.5, 0.5));
        assertNotNull(nearestHit);
        assertEquals(0.2, nearestHit.t(), 1.0e-6);
        assertEquals(0, nearestHit.cubeIndex());
        assertVecEquals(new Vec3(1.0, 0.0, 0.0), nearestHit.normal(), 1.0e-6);
        assertVecEquals(new Vec3(0.0, 0.5, 0.5), nearestHit.attachmentOffset(), 1.0e-6);
        assertVecEquals(new Vec3(1.0, 0.0, 0.0), nearestHit.attachmentNormal(), 1.0e-6);
        assertMatrixEquals(new Matrix4f(), nearestHit.attachmentTransform(), 1.0e-6f);
        assertMatrixEquals(new Matrix4f(), nearestHit.cubeTransform(), 1.0e-6f);

        ModelRayTraceResult compatibilityHit = new ModelRayTraceResult(Vec3.ZERO, 0.25, 3, 4);
        assertNull(compatibilityHit.normal());
        assertNull(compatibilityHit.attachmentNormal());
        assertVecEquals(Vec3.ZERO, compatibilityHit.attachmentOffset(), 1.0e-6);
    }

    private static void configureGlobalNormalPose(BoneTreeInstance instance) {
        BoneState root = instance.getBone("root");
        assertNotNull(root);
        root.x = 8.0f;
        root.y = -4.0f;
        root.z = 3.0f;
        root.rotation.set(new Quaternionf().rotateZYX(0.2f, 0.4f, -0.1f));
        root.xScale = 1.25f;
        root.yScale = 0.75f;
        root.zScale = 0.9f;

        BoneState child = instance.getBone("child");
        assertNotNull(child);
        child.x = -6.0f;
        child.y = 5.0f;
        child.z = 2.0f;
        child.rotation.set(new Quaternionf().rotateYXZ(-0.3f, 0.15f, 0.25f));
        child.xScale = 0.8f;
        child.yScale = 1.4f;
        child.zScale = 0.65f;
    }

    private static void assertGlobalNormalAndPoseStackHelpers(BoneTreeInstance instance, int boneIndex) {
        Matrix4f globalTransform = instance.getGlobalTransform(boneIndex);
        Matrix3f expectedGlobalNormal = new Matrix3f(globalTransform).invert().transpose();
        assertMatrixEquals(expectedGlobalNormal, instance.getGlobalNormal(boneIndex), 1.0e-6f);

        PoseStack globalPoseStack = newNonIdentityPoseStack();
        Matrix4f expectedGlobalPose = new Matrix4f(globalPoseStack.last().pose()).mul(globalTransform);
        Matrix3f expectedGlobalPoseNormal = new Matrix3f(globalPoseStack.last().normal()).mul(expectedGlobalNormal);
        instance.mulGlobalTransform(globalPoseStack, boneIndex);
        assertMatrixEquals(expectedGlobalPose, globalPoseStack.last().pose(), 1.0e-6f);
        assertMatrixEquals(expectedGlobalPoseNormal, globalPoseStack.last().normal(), 1.0e-6f);

        int parentIndex = instance.getFather(boneIndex);
        PoseStack parentPoseStack = newNonIdentityPoseStack();
        Matrix4f expectedParentPose = new Matrix4f(parentPoseStack.last().pose()).mul(instance.getGlobalTransform(parentIndex));
        Matrix3f expectedParentNormal = new Matrix3f(parentPoseStack.last().normal()).mul(instance.getGlobalNormal(parentIndex));
        instance.mulParentGlobalTransform(parentPoseStack, boneIndex);
        assertMatrixEquals(expectedParentPose, parentPoseStack.last().pose(), 1.0e-6f);
        assertMatrixEquals(expectedParentNormal, parentPoseStack.last().normal(), 1.0e-6f);

    }

    private static void assertPoseStackRestored(java.util.function.Consumer<PoseStack> renderer) {
        PoseStack poseStack = newNonIdentityPoseStack();
        Matrix4f expectedPose = new Matrix4f(poseStack.last().pose());
        Matrix3f expectedNormal = new Matrix3f(poseStack.last().normal());
        renderer.accept(poseStack);
        assertMatrixEquals(expectedPose, poseStack.last().pose(), 1.0e-6f);
        assertMatrixEquals(expectedNormal, poseStack.last().normal(), 1.0e-6f);
    }

    private static PoseStack newNonIdentityPoseStack() {
        PoseStack poseStack = new PoseStack();
        poseStack.translate(0.5f, -1.25f, 2.0f);
        poseStack.mulPose(new Quaternionf().rotateXYZ(0.1f, -0.2f, 0.3f));
        poseStack.scale(1.2f, 0.85f, 1.1f);
        return poseStack;
    }

    private static void configureRayPose(BoneState bone) {
        bone.x = 8.0f;
        bone.y = -4.0f;
        bone.z = 3.0f;
        bone.rotation.set(new Quaternionf().rotateZYX(0.2f, 0.4f, -0.1f));
        bone.xScale = 1.25f;
        bone.yScale = 0.75f;
        bone.zScale = 0.9f;
    }

    private static Vec3 worldPosition(Matrix4f modelRotation, Vec3 modelOrigin, Vector3f modelPosition) {
        Vector3f transformed = new Vector3f(modelPosition).mulPosition(modelRotation);
        return modelOrigin.add(transformed.x, transformed.y, transformed.z);
    }

    private static void assertVecEquals(Vec3 expected, Vec3 actual, double epsilon) {
        assertEquals(expected.x, actual.x, epsilon);
        assertEquals(expected.y, actual.y, epsilon);
        assertEquals(expected.z, actual.z, epsilon);
    }

    private static void assertTaczFixture(String gunName, String foldedQueryBone) throws IOException {
        BedrockModelPOJO pojo = loadModel("tacz/examples/geo/" + gunName + "_geo.json");
        Set<String> animatedBones = loadAnimatedBones("tacz/examples/anim/" + gunName + ".animation.json");
        BakerOptions options = new BakerOptions(animatedBones, Set.of(), true, true);
        BakedBedrockModel model = BakedBedrockModel.bake(pojo, options);
        BakedModelInstance instance = model.createInstance();

        int originalBoneCount = pojo.getGeometryModelNew().getBones().length;
        assertTrue(model.bones().length > 0, gunName);
        assertTrue(model.bones().length < originalBoneCount, gunName + " should fold at least one real TACZ bone");
        assertNotNull(instance.getBone("root"), gunName + " root should stay runtime");
        assertNull(instance.getBone(foldedQueryBone), gunName + " folded bone should not stay runtime");

        assertNull(instance.getLocatorTransform(foldedQueryBone), gunName + " folded bone should not be treated as a locator");
        Matrix4f foldedTransform = instance.getQueryTransform(foldedQueryBone);
        assertNotNull(foldedTransform, gunName + " folded bone should remain queryable");
        assertMatrixEquals(foldedTransform, instance.getQueryTransform(foldedQueryBone), 1.0e-6f);

        String tree = BedrockModelBaker.describeFoldedTree(pojo, options);
        assertTrue(tree.contains("root [runtime"), tree);
        assertTrue(tree.contains(foldedQueryBone + " [folded"), tree);
        assertTrue(tree.contains(foldedQueryBone + " [folded") && tree.contains("query"), tree);
    }

    private static BedrockModelPOJO loadModel(String resourceName) throws IOException {
        try (InputStream stream = openResource(resourceName);
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return GsonUtil.CLIENT_GSON.fromJson(reader, BedrockModelPOJO.class);
        }
    }

    private static BedrockModelPOJO loadInlineModel(String json) {
        return GsonUtil.CLIENT_GSON.fromJson(json, BedrockModelPOJO.class);
    }

    private static Set<String> loadAnimatedBones(String resourceName) throws IOException {
        try (InputStream stream = openResource(resourceName);
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            BedrockAnimationFile animationFile = GsonUtil.CLIENT_GSON.fromJson(reader, BedrockAnimationFile.class);
            return BakerOptions.collectAnimatedBones(animationFile);
        }
    }

    private static InputStream openResource(String resourceName) {
        InputStream stream = BedrockModelBakerTest.class.getClassLoader().getResourceAsStream(resourceName);
        assertNotNull(stream, "Resource not found: " + resourceName);
        return stream;
    }

    private static void assertMatrixEquals(Matrix3f expected, Matrix3f actual, float epsilon) {
        assertEquals(expected.m00(), actual.m00(), epsilon);
        assertEquals(expected.m01(), actual.m01(), epsilon);
        assertEquals(expected.m02(), actual.m02(), epsilon);
        assertEquals(expected.m10(), actual.m10(), epsilon);
        assertEquals(expected.m11(), actual.m11(), epsilon);
        assertEquals(expected.m12(), actual.m12(), epsilon);
        assertEquals(expected.m20(), actual.m20(), epsilon);
        assertEquals(expected.m21(), actual.m21(), epsilon);
        assertEquals(expected.m22(), actual.m22(), epsilon);
    }

    private static void assertMatrixEquals(Matrix4f expected, Matrix4f actual, float epsilon) {
        assertEquals(expected.m00(), actual.m00(), epsilon);
        assertEquals(expected.m01(), actual.m01(), epsilon);
        assertEquals(expected.m02(), actual.m02(), epsilon);
        assertEquals(expected.m03(), actual.m03(), epsilon);
        assertEquals(expected.m10(), actual.m10(), epsilon);
        assertEquals(expected.m11(), actual.m11(), epsilon);
        assertEquals(expected.m12(), actual.m12(), epsilon);
        assertEquals(expected.m13(), actual.m13(), epsilon);
        assertEquals(expected.m20(), actual.m20(), epsilon);
        assertEquals(expected.m21(), actual.m21(), epsilon);
        assertEquals(expected.m22(), actual.m22(), epsilon);
        assertEquals(expected.m23(), actual.m23(), epsilon);
        assertEquals(expected.m30(), actual.m30(), epsilon);
        assertEquals(expected.m31(), actual.m31(), epsilon);
        assertEquals(expected.m32(), actual.m32(), epsilon);
        assertEquals(expected.m33(), actual.m33(), epsilon);
    }
}
