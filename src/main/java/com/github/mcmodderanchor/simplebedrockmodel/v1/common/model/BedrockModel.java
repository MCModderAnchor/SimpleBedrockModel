package com.github.mcmodderanchor.simplebedrockmodel.v1.common.model;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.embeddium.EmbeddiumBedrockCubeBox;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.embeddium.EmbeddiumBedrockCubePerFace;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.embeddium.EmbeddiumBedrockPolyMesh;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.embeddium.EmbeddiumCompat;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.sodium.SodiumBedrockCubeBox;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.sodium.SodiumBedrockCubePerFace;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.sodium.SodiumBedrockPolyMesh;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.sodium.SodiumCompat;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.BedrockModelRenderTypes;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.BoneIndexProvider;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.*;
import com.google.common.collect.Collections2;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.maydaymemory.mae.basic.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.*;

import org.jetbrains.annotations.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.Math;
import java.util.*;

public class BedrockModel implements Skeleton, BoneIndexProvider {
    private static final Vector3f NORMAL_SCALE = new Vector3f(1, 1, 1);
    /**
     * 储存 name -> bone 的映射关系
     */
    protected final HashMap<String, BedrockBone> boneMap = new HashMap<>();
    /**
     * 储存 index -> bone 的映射关系
     */
    protected final ArrayList<BedrockBone> boneIndex = new ArrayList<>();
    /**
     * 顶层 bone，没有对应的 name 和 index
     */
    protected final BedrockBone root = new BedrockBone();
    /**
     * 模型的 AABB
     */
    protected AABB renderBoundingBox;
    /**
     * 模型默认的 Pose
     */
    private final Pose bindingPose;

    public BedrockModel(BedrockModelPOJO pojo) {
        if (BedrockVersion.isLegacyVersion(pojo)) {
            loadLegacyModel(pojo);
        }
        if (BedrockVersion.isNewVersion(pojo)) {
            loadNewModel(pojo);
        }
        bindingPose = initializeBindingPose();
    }

    protected Pose initializeBindingPose() {
        PoseBuilder poseBuilder = new ArrayPoseBuilder();
        for (int i = 0; i < boneIndex.size(); i++) {
            BedrockBone part = boneIndex.get(i);
            BoneTransform boneTransform = new BoneTransform(
                    i,
                    new Vector3f(part.x, part.y, part.z),
                    new BindRotationView(part.rotation, part.rotationInEuler),
                    NORMAL_SCALE
            );
            poseBuilder.addBoneTransform(boneTransform);
        }
        return poseBuilder.toPose();
    }

    protected void loadNewModel(BedrockModelPOJO pojo) {
        assert pojo.getGeometryModelNew() != null;
        pojo.getGeometryModelNew().deco();

        BonesItem[] bones = pojo.getGeometryModelNew().getBones();
        Description description = pojo.getGeometryModelNew().getDescription();
        if (description != null) {
            // 材质的长度、宽度
            int texWidth = description.getTextureWidth();
            int texHeight = description.getTextureHeight();

            float[] offset = description.getVisibleBoundsOffset();
            float offsetX = offset[0];
            float offsetY = offset[1];
            float offsetZ = offset[2];
            float width = description.getVisibleBoundsWidth() / 2.0f;
            float height = description.getVisibleBoundsHeight() / 2.0f;
            renderBoundingBox = new AABB(offsetX - width, offsetY - height, offsetZ - width, offsetX + width, offsetY + height, offsetZ + width);
            initialWithBoneItems(bones, texWidth, texHeight);
        } else {
            initialWithBoneItems(bones, 0, 0);
        }
    }

    protected void loadLegacyModel(BedrockModelPOJO pojo) {
        assert pojo.getGeometryModelLegacy() != null;
        pojo.getGeometryModelLegacy().deco();

        BonesItem[] bones = pojo.getGeometryModelLegacy().getBones();
        float[] offset = pojo.getGeometryModelLegacy().getVisibleBoundsOffset();
        if (offset != null) {
            // 材质的长度、宽度
            int texWidth = pojo.getGeometryModelLegacy().getTextureWidth();
            int texHeight = pojo.getGeometryModelLegacy().getTextureHeight();

            float offsetX = offset[0];
            float offsetY = offset[1];
            float offsetZ = offset[2];
            float width = pojo.getGeometryModelLegacy().getVisibleBoundsWidth() / 2.0f;
            float height = pojo.getGeometryModelLegacy().getVisibleBoundsHeight() / 2.0f;
            renderBoundingBox = new AABB(offsetX - width, offsetY - height, offsetZ - width, offsetX + width, offsetY + height, offsetZ + width);

            initialWithBoneItems(bones, texWidth, texHeight);
        } else {
            initialWithBoneItems(bones, 0, 0);
        }
    }

    protected BedrockCube createCubeBox(float texOffX, float texOffY, float x, float y, float z, float width, float height, float depth,
                                        float delta, boolean mirror, float texWidth, float texHeight) {
        if (SodiumCompat.isSodiumInstalled()) {
            return new SodiumBedrockCubeBox(texOffX, texOffY, x, y, z, width, height, depth, delta, mirror, texWidth, texHeight);
        }
        if (EmbeddiumCompat.isEmbeddiumInstalled()) {
            return new EmbeddiumBedrockCubeBox(texOffX, texOffY, x, y, z, width, height, depth, delta, mirror, texWidth, texHeight);
        }
        return new BedrockCubeBox(texOffX, texOffY, x, y, z, width, height, depth, delta, mirror, texWidth, texHeight);
    }

    protected BedrockCube createCubePerFace(float x, float y, float z, float width, float height, float depth, float delta,
                                            float texWidth, float texHeight, FaceUVsItem faces) {
        if (SodiumCompat.isSodiumInstalled()) {
            return new SodiumBedrockCubePerFace(x, y, z, width, height, depth, delta, texWidth, texHeight, faces);
        }
        if (EmbeddiumCompat.isEmbeddiumInstalled()) {
            return new EmbeddiumBedrockCubePerFace(x, y, z, width, height, depth, delta, texWidth, texHeight, faces);
        }
        return new BedrockCubePerFace(x, y, z, width, height, depth, delta, texWidth, texHeight, faces);
    }

    protected BedrockMesh createPolyMesh(PolyMeshItem polyMesh, BedrockBone part, float texWidth, float texHeight) {
        if (SodiumCompat.isSodiumInstalled()) {
            return new SodiumBedrockPolyMesh(polyMesh, part, texWidth, texHeight);
        }
        if (EmbeddiumCompat.isEmbeddiumInstalled()) {
            return new EmbeddiumBedrockPolyMesh(polyMesh, part, texWidth, texHeight);
        }
        return new BedrockPolyMesh(polyMesh, part, texWidth, texHeight);
    }

    @OnlyIn(Dist.CLIENT)
    @ParametersAreNonnullByDefault
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay) {
        root.render(poseStack, buffer, packedLight, packedOverlay);
    }

    @OnlyIn(Dist.CLIENT)
    @ParametersAreNonnullByDefault
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /**
     * 同时渲染poly_mesh和cube的入口
     * @param poseStack
     * @param quadConsumer
     * @param triangleConsumer 用于渲染mesh。需要VertexFormat.Mode为TRIANGLES，参见{@link BedrockModelRenderTypes}
     * @param packedLight
     * @param packedOverlay
     */
    @OnlyIn(Dist.CLIENT)
    @ParametersAreNonnullByDefault
    public void renderToBuffer(PoseStack poseStack, VertexConsumer quadConsumer, VertexConsumer triangleConsumer, int packedLight, int packedOverlay) {
        root.render(poseStack, quadConsumer, triangleConsumer, packedLight, packedOverlay);
    }

    @OnlyIn(Dist.CLIENT)
    @ParametersAreNonnullByDefault
    public void renderToBuffer(PoseStack poseStack, VertexConsumer quadConsumer, VertexConsumer triangleConsumer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        root.render(poseStack, quadConsumer, triangleConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    public AABB getRenderBoundingBox() {
        return renderBoundingBox;
    }

    public HashMap<String, BedrockBone> getBoneMap() {
        return boneMap;
    }

    public ArrayList<BedrockBone> getBoneIndexes() {
        return boneIndex;
    }

    private void convertPivot(BedrockBone root) {
        // 后序遍历，子节点计算完后计算当前节点
        for (BedrockBone child : root.getChildren()) {
            convertPivot(child);
        }
        if (root.parent != null) {
            root.x = root.x - root.parent.x;
            root.y = root.y - root.parent.y;
            root.z = root.z - root.parent.z;
        }
    }

    private void initialWithBoneItems(BonesItem[] bones, int texWidth, int texHeight) {
        if (bones == null) {
            return;
        }
        // 建立 name -> bone 和 index -> bone 的映射，对 BedrockPart 实例进行第一遍初始化
        for (BonesItem bone : bones) {
            BedrockBone part = new BedrockBone();
            float[] pivot = bone.getPivot() != null ? Arrays.copyOf(bone.getPivot(), 3) : null;
            float[] rotation = bone.getRotation() != null ? Arrays.copyOf(bone.getRotation(), 3) : null;
            // 这里先简单的将左手系坐标转换为右手系坐标，待父子关系建立后再转换为相对坐标
            if (pivot != null) {
                part.x = -pivot[0];
                part.y = pivot[1];
                part.z = pivot[2];
            }
            if (rotation != null) {
                rotation[0] = (float) -Math.toRadians(rotation[0]);
                rotation[1] = (float) -Math.toRadians(rotation[1]);
                rotation[2] = (float) Math.toRadians(rotation[2]);
                part.rotation.rotateZYX(rotation[2], rotation[1], rotation[0]);
                part.rotationInEuler.set(rotation);
            }
            part.mirror = bone.isMirror();
            part.index = boneIndex.size();

            boneIndex.add(part);
            boneMap.put(bone.getName(), part);
        }
        // 建立父子关系，塞入 cubes（因为 cube 的 origin 需要依赖绝对坐标的 pivot 计算，因此必须排在计算相对 pivot 之前）
        for (BonesItem bone : bones) {
            BedrockBone part = boneMap.get(bone.getName());
            // 父骨骼的名称，可能为空
            @Nullable String parentName = bone.getParent();
            if (parentName != null) {
                part.parent = boneMap.get(parentName);
                Objects.requireNonNull(part.parent);
                part.parent.addChild(part);
            } else {
                part.parent = root;
                root.addChild(part);
            }
            // 塞入 cubes
            part.setLocators(parseLocators(bone, part));
            if (bone.getPolyMesh() != null) {
                BedrockMesh polyMesh = createPolyMesh(bone.getPolyMesh(), part, texWidth, texHeight);
                if (polyMesh != null) {
                    part.meshes.add(polyMesh);
                }
            }
            if (bone.getCubes() != null) {
                for (CubesItem cube : bone.getCubes()) {
                    float[] uv = cube.getUv();
                    @Nullable FaceUVsItem faceUv = cube.getFaceUv();
                    float[] size = cube.getSize();
                    float[] origin = Arrays.copyOf(cube.getOrigin(), 3);
                    @Nullable float[] cubeRotation = cube.getRotation() != null ? Arrays.copyOf(cube.getRotation(), 3) : null;
                    @Nullable float[] cubePivot = cube.getPivot() != null ? Arrays.copyOf(cube.getPivot(), 3) : null;
                    boolean mirror = cube.isMirror();
                    float inflate = cube.getInflate();
                    // 先将 origin 的 x 轴坐标处理一下，先加上 size.x 再镜像。
                    origin[0] = -(origin[0] + size[0]);
                    // 初步处理 cubeRotation 和 cubePivot，其中 cubePivot 是从左手系转换为右手系的绝对坐标
                    if (cubeRotation != null) {
                        cubeRotation[0] = (float) -Math.toRadians(cubeRotation[0]);
                        cubeRotation[1] = (float) -Math.toRadians(cubeRotation[1]);
                        cubeRotation[2] = (float) Math.toRadians(cubeRotation[2]);
                    }
                    if (cubePivot != null) {
                        cubePivot[0] = -cubePivot[0];
                    }
                    // 根据情况建立好 BedrockCube 实例
                    BedrockCube cubeInstance;
                    float originX = cubePivot == null ? origin[0] - part.x : origin[0] - cubePivot[0];
                    float originY = cubePivot == null ? origin[1] - part.y : origin[1] - cubePivot[1];
                    float originZ = cubePivot == null ? origin[2] - part.z : origin[2] - cubePivot[2];
                    if (faceUv == null) {
                        cubeInstance = createCubeBox(
                                uv[0], uv[1],
                                originX, originY, originZ,
                                size[0], size[1], size[2],
                                inflate, mirror, texWidth, texHeight
                        );
                    } else {
                        cubeInstance = createCubePerFace(
                                originX, originY, originZ,
                                size[0], size[1], size[2],
                                inflate, texWidth, texHeight, faceUv
                        );
                    }
                    if (cubeRotation == null || cubePivot == null) {
                        // 普通 cube 直接放入 cubes
                        part.cubes.add(cubeInstance);
                    } else {
                        // 带有 pivot 和 rotation 的需要套一层 BedrockPart
                        BedrockBone cubeRenderer = new BedrockBone();
                        cubeRenderer.x = cubePivot[0];
                        cubeRenderer.y = cubePivot[1];
                        cubeRenderer.z = cubePivot[2];
                        cubeRenderer.rotation.rotateZYX(cubeRotation[2], cubeRotation[1], cubeRotation[0]);
                        cubeRenderer.cubes.add(cubeInstance);
                        // 添加进父骨骼中
                        cubeRenderer.parent = part;
                        part.addChild(cubeRenderer);
                    }
                }
            }
        }
        // 将所有相对 pivot 转换为绝对 pivot，使用 DFS 实现
        convertPivot(root);
    }

    private Map<String, LocatorData> parseLocators(BonesItem bone, BedrockBone part) {
        Map<String, JsonElement> locators = bone.getLocators();
        if (locators == null || locators.isEmpty()) {
            return Map.of();
        }
        // 此时 part.x/y/z 是绝对坐标（已做左手系转换，尚未 convertPivot）
        // locator offset 也是绝对坐标（Bedrock 左手系），需要转换为相对于骨骼 pivot 的偏移
        Map<String, LocatorData> parsed = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : locators.entrySet()) {
            parsed.put(entry.getKey(), parseLocator(entry.getValue(), part));
        }
        return Map.copyOf(parsed);
    }

    private LocatorData parseLocator(JsonElement element, BedrockBone part) {
        if (element == null || element.isJsonNull()) {
            return LocatorData.EMPTY;
        }
        if (element.isJsonArray()) {
            float[] absOffset = parseLocatorArray(element.getAsJsonArray());
            // 左手系转右手系，然后减去骨骼的绝对 pivot 得到相对偏移
            float relX = -absOffset[0] - part.x;
            float relY = absOffset[1] - part.y;
            float relZ = absOffset[2] - part.z;
            return new LocatorData(new float[]{relX, relY, relZ}, new float[3]);
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            float[] absOffset = object.has("offset") ? parseLocatorArray(object.getAsJsonArray("offset")) : new float[3];
            float[] rotation = object.has("rotation") ? parseLocatorArray(object.getAsJsonArray("rotation")) : new float[3];
            // 左手系转右手系，然后减去骨骼的绝对 pivot 得到相对偏移
            float relX = -absOffset[0] - part.x;
            float relY = absOffset[1] - part.y;
            float relZ = absOffset[2] - part.z;
            return new LocatorData(new float[]{relX, relY, relZ}, rotation);
        }
        return LocatorData.EMPTY;
    }

    private float[] parseLocatorArray(JsonArray array) {
        float[] values = new float[3];
        int size = Math.min(array.size(), values.length);
        for (int i = 0; i < size; i++) {
            values[i] = array.get(i).getAsFloat();
        }
        return values;
    }

    @Override
    public Collection<Integer> getChildren(int i) {
        BedrockBone part = boneIndex.get(i);
        return Collections2.transform(part.getChildren(), val -> val.index);
    }

    @Override
    public int getFather(int i) {
        BedrockBone part = boneIndex.get(i);
        if (part.parent == null) {
            return -1;
        }
        return part.parent.index;
    }

    @Override
    public void applyPose(Pose pose) {
        for (BoneTransform boneTransform : pose.getBoneTransforms()) {
            BedrockBone part = boneIndex.get(boneTransform.boneIndex());
            Vector3fc translation = boneTransform.translation();
            Quaternionfc rotation = boneTransform.rotation().asQuaternion();
            Vector3fc scale = boneTransform.scale();
            part.x = translation.x();
            part.y = translation.y();
            part.z = translation.z();
            part.rotation.set(rotation);
            part.xScale = scale.x();
            part.yScale = scale.y();
            part.zScale = scale.z();
        }
    }

    @Override
    public Pose getPose() {
        PoseBuilder poseBuilder = new ArrayPoseBuilder();
        for (BedrockBone part : boneIndex) {
            BoneTransform boneTransform = part.getBoneTransform();
            poseBuilder.addBoneTransform(boneTransform);
        }
        return poseBuilder.toPose();
    }

    @Override
    public Pose getBindPose() {
        return bindingPose;
    }

    @Override
    public int getIndex(String boneName) {
        BedrockBone bone = boneMap.get(boneName);
        return bone == null ? -1 : bone.index;
    }

    public BedrockBone getBone(String boneName) {
        return boneMap.get(boneName);
    }

    /**
     * 在模型中查找指定名称的 locator。遍历所有骨骼的 locator 映射。
     *
     * @param locatorName locator 名称
     * @return 包含骨骼和 locator 数据的结果，未找到时返回 null
     */
    @Nullable
    public LocatorResult findLocator(String locatorName) {
        for (BedrockBone bone : boneIndex) {
            Map<String, LocatorData> locators = bone.getLocators();
            if (locators.containsKey(locatorName)) {
                return new LocatorResult(bone, locators.get(locatorName));
            }
        }
        return null;
    }

    /**
     * 获取 locator 的完整变换矩阵（骨骼全局变换 × locator 偏移和旋转）。
     *
     * @param locatorName locator 名称
     * @return 变换矩阵，未找到时返回 null
     */
    @Nullable
    public Matrix4f getLocatorTransform(String locatorName) {
        LocatorResult result = findLocator(locatorName);
        if (result == null) return null;

        Matrix4f transform = result.bone().getGlobalTransform();
        LocatorData locator = result.locator();

        // 应用 locator 偏移（Bedrock 坐标单位是像素，需要 /16）
        float[] offset = locator.offset();
        if (offset[0] != 0 || offset[1] != 0 || offset[2] != 0) {
            transform.translate(offset[0] / 16f, offset[1] / 16f, offset[2] / 16f);
        }

        // 应用 locator 旋转（度转弧度，ZYX 顺序）
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

    /**
     * locator 查找结果。
     */
    public record LocatorResult(BedrockBone bone, LocatorData locator) {}

    private record BindRotationView(Quaternionfc quaternion, Vector3fc euler) implements RotationView {
        private BindRotationView(Quaternionfc quaternion, Vector3fc euler) {
            this.quaternion = new Quaternionf(quaternion);
            this.euler = new Vector3f(euler);
        }

        @Override
        public Vector3fc asEulerAngle() {
            return euler;
        }

        @Override
        public Quaternionfc asQuaternion() {
            return quaternion;
        }
    }
}
