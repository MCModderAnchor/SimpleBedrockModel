package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.GsonUtil;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockAnimationFile;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockModelPOJO;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.bake.BakerOptions;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.bake.BedrockModelBaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;

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
        BedrockModelInstance instance = model.createInstance();

        assertNotNull(instance.getBone("GunFront"));
        assertNotNull(instance.getBone("Gunbody"));
    }

    private static void assertTaczFixture(String gunName, String foldedQueryBone) throws IOException {
        BedrockModelPOJO pojo = loadModel("tacz/examples/geo/" + gunName + "_geo.json");
        Set<String> animatedBones = loadAnimatedBones("tacz/examples/anim/" + gunName + ".animation.json");
        BakerOptions options = new BakerOptions(animatedBones, Set.of(), true, true);
        BakedBedrockModel model = BakedBedrockModel.bake(pojo, options);
        BedrockModelInstance instance = model.createInstance();

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
