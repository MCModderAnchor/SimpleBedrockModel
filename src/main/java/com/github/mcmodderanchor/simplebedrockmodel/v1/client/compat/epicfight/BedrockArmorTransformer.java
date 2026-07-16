package com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.epicfight;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.model.BedrockArmorModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.GeoArmorRenderer;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockBone;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockCube;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.renderer.GeoArmorRendererV2;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BoneState;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.TreeArmorModelInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.CubeBox;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.CubePerFace;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.ICube;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.TreeBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.TreeBoneDefinition;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import yesman.epicfight.api.client.event.types.render.AnimatedArmorTextureEvent;
import yesman.epicfight.api.client.model.Mesh;
import yesman.epicfight.api.client.model.MeshPartDefinition;
import yesman.epicfight.api.client.model.SingleGroupVertexBuilder;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.client.model.transformer.HumanoidModelTransformer;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec2f;
import yesman.epicfight.api.utils.math.Vec3f;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Transforms BedrockArmorModel into EpicFight SkinnedMesh.
 * Based on EpicFight's GeoModelTransformer for GeckoLib armor.
 */
public class BedrockArmorTransformer extends HumanoidModelTransformer {

    public static void getBedrockArmorTexturePath(AnimatedArmorTextureEvent event) {
        IClientItemExtensions customRenderProperties = IClientItemExtensions.of(event.getItemStack());

        HumanoidModel<?> extensionRenderer = customRenderProperties.getHumanoidArmorModel(
                event.getLivingEntity(), event.getItemStack(), event.getEquipmentSlot(), event.getOriginalModel());

        if (extensionRenderer instanceof GeoArmorRenderer geoArmorRenderer) {
            event.setResultLocation(geoArmorRenderer.getTexture());
        } else if (extensionRenderer instanceof GeoArmorRendererV2 geoArmorRenderer) {
            event.setResultLocation(geoArmorRenderer.getTexture());
        }
    }

    static final CubeTransformer HEAD = new SimpleTransformer(9);
    static final CubeTransformer LEFT_FEET = new SimpleTransformer(5);
    static final CubeTransformer RIGHT_FEET = new SimpleTransformer(2);
    static final CubeTransformer LEFT_ARM = new LimbPartTransformer(16, 17, 19, 1.125F, false, AABB.ofSize(new Vec3(-0.375D, 1.125D, 0), 0.5D, 0.85D, 0.5D));
    static final CubeTransformer RIGHT_ARM = new LimbPartTransformer(11, 12, 14, 1.125F, false, AABB.ofSize(new Vec3(0.375D, 1.125D, 0), 0.5D, 0.85D, 0.5D));
    static final CubeTransformer LEFT_LEG = new LimbPartTransformer(4, 5, 6, 0.375F, true, AABB.ofSize(new Vec3(-0.15D, 0.375D, 0), 0.5D, 0.85D, 0.5D));
    static final CubeTransformer RIGHT_LEG = new LimbPartTransformer(1, 2, 3, 0.375F, true, AABB.ofSize(new Vec3(0.15D, 0.375D, 0), 0.5D, 0.85D, 0.5D));
    static final CubeTransformer CHEST = new ChestPartTransformer(8, 7, 1.125F, AABB.ofSize(new Vec3(0, 1.125D, 0), 0.9D, 0.85D, 0.45D));

    static class BedrockModelPartition {
        final CubeTransformer cubeTransformer;
        final BedrockBone bone;

        BedrockModelPartition(CubeTransformer cubeTransformer, BedrockBone bone) {
            this.cubeTransformer = cubeTransformer;
            this.bone = bone;
        }
    }

    static class TreeModelPartition {
        final CubeTransformer cubeTransformer;
        final BoneState bone;

        TreeModelPartition(CubeTransformer cubeTransformer, BoneState bone) {
            this.cubeTransformer = cubeTransformer;
            this.bone = bone;
        }
    }

    @Override
    public SkinnedMesh transformArmorModel(HumanoidModel<?> humanoidModel) {
        if (humanoidModel instanceof GeoArmorRenderer geoArmor) {
            return transformArmorModel(geoArmor.getModel(), geoArmor.getEquipmentSlot());
        }
        if (humanoidModel instanceof GeoArmorRendererV2 geoArmor) {
            EquipmentSlot slot = geoArmor.getCurrentSlot() != null ? geoArmor.getCurrentSlot() : geoArmor.getArmorSlot();
            return transformTreeArmorModel(geoArmor.getModel(), geoArmor.getInstance(), slot);
        }

        return null;
    }

    public static SkinnedMesh transformArmorModel(BedrockArmorModel model) {
        return transformArmorModel(model, null);
    }

    public static SkinnedMesh transformArmorModel(BedrockArmorModel model, @Nullable EquipmentSlot equipmentSlot) {
        // Reset to bind pose to get default bone transforms
        model.applyPose(model.getBindPose());

        BedrockBone headBone = model.getArmorHead();
        BedrockBone bodyBone = model.getArmorBody();
        BedrockBone rightArmBone = model.getArmorRightArm();
        BedrockBone leftArmBone = model.getArmorLeftArm();
        BedrockBone rightLegBone = model.getArmorRightLeg();
        BedrockBone leftLegBone = model.getArmorLeftLeg();
        BedrockBone rightBootBone = model.getArmorRightBoot();
        BedrockBone leftBootBone = model.getArmorLeftBoot();

        // Reset rotations only (preserve default positions from model)
        resetRotation(headBone);
        resetRotation(bodyBone);
        resetRotation(rightArmBone);
        resetRotation(leftArmBone);
        resetRotation(rightLegBone);
        resetRotation(leftLegBone);
        resetRotation(rightBootBone);
        resetRotation(leftBootBone);

        List<BedrockModelPartition> partitions = Lists.newArrayList();
        if (equipmentSlot == null || equipmentSlot == EquipmentSlot.HEAD) {
            partitions.add(new BedrockModelPartition(HEAD, headBone));
        }
        if (equipmentSlot == null || equipmentSlot == EquipmentSlot.CHEST) {
            partitions.add(new BedrockModelPartition(CHEST, bodyBone));
            partitions.add(new BedrockModelPartition(RIGHT_ARM, rightArmBone));
            partitions.add(new BedrockModelPartition(LEFT_ARM, leftArmBone));
        }
        if (equipmentSlot == null || equipmentSlot == EquipmentSlot.LEGS) {
            partitions.add(new BedrockModelPartition(LEFT_LEG, leftLegBone));
            partitions.add(new BedrockModelPartition(RIGHT_LEG, rightLegBone));
        }
        if (equipmentSlot == null || equipmentSlot == EquipmentSlot.FEET) {
            partitions.add(new BedrockModelPartition(LEFT_FEET, leftBootBone));
            partitions.add(new BedrockModelPartition(RIGHT_FEET, rightBootBone));
        }

        return bakeMeshFromBones(partitions);
    }

    public static SkinnedMesh transformTreeArmorModel(TreeBedrockModel model, TreeArmorModelInstance instance) {
        return transformTreeArmorModel(model, instance, null);
    }

    public static SkinnedMesh transformTreeArmorModel(TreeBedrockModel model, TreeArmorModelInstance instance, @Nullable EquipmentSlot equipmentSlot) {
        instance.resetPose();

        BoneState headBone = instance.getArmorHead();
        BoneState bodyBone = instance.getArmorBody();
        BoneState rightArmBone = instance.getArmorRightArm();
        BoneState leftArmBone = instance.getArmorLeftArm();
        BoneState rightLegBone = instance.getArmorRightLeg();
        BoneState leftLegBone = instance.getArmorLeftLeg();
        BoneState rightBootBone = instance.getArmorRightBoot();
        BoneState leftBootBone = instance.getArmorLeftBoot();

        resetRotation(headBone);
        resetRotation(bodyBone);
        resetRotation(rightArmBone);
        resetRotation(leftArmBone);
        resetRotation(rightLegBone);
        resetRotation(leftLegBone);
        resetRotation(rightBootBone);
        resetRotation(leftBootBone);

        List<TreeModelPartition> partitions = Lists.newArrayList();
        if (equipmentSlot == null || equipmentSlot == EquipmentSlot.HEAD) {
            partitions.add(new TreeModelPartition(HEAD, headBone));
        }
        if (equipmentSlot == null || equipmentSlot == EquipmentSlot.CHEST) {
            partitions.add(new TreeModelPartition(CHEST, bodyBone));
            partitions.add(new TreeModelPartition(RIGHT_ARM, rightArmBone));
            partitions.add(new TreeModelPartition(LEFT_ARM, leftArmBone));
        }
        if (equipmentSlot == null || equipmentSlot == EquipmentSlot.LEGS) {
            partitions.add(new TreeModelPartition(LEFT_LEG, leftLegBone));
            partitions.add(new TreeModelPartition(RIGHT_LEG, rightLegBone));
        }
        if (equipmentSlot == null || equipmentSlot == EquipmentSlot.FEET) {
            partitions.add(new TreeModelPartition(LEFT_FEET, leftBootBone));
            partitions.add(new TreeModelPartition(RIGHT_FEET, rightBootBone));
        }

        return bakeMeshFromTreeBones(model, instance, partitions);
    }

    private static void resetRotation(@Nullable BedrockBone bone) {
        if (bone == null) return;
        bone.rotation.identity();
    }

    private static void resetRotation(@Nullable BoneState bone) {
        if (bone == null) return;
        bone.rotation.identity();
        bone.rotationInEuler.zero();
    }

    private static SkinnedMesh bakeMeshFromBones(List<BedrockModelPartition> partitions) {
        List<SingleGroupVertexBuilder> vertices = Lists.newArrayList();
        Map<MeshPartDefinition, IntList> indices = Maps.newHashMap();
        PoseStack poseStack = new PoseStack();
        IndexCounter indexCounter = new IndexCounter();

        for (BedrockModelPartition partition : partitions) {
            bake(poseStack, partition, partition.bone != null ? partition.bone.toString() : "",
                    partition.bone, vertices, indices, indexCounter);
        }

        return SingleGroupVertexBuilder.loadVertexInformation(vertices, indices);
    }

    private static SkinnedMesh bakeMeshFromTreeBones(TreeBedrockModel model, TreeArmorModelInstance instance,
                                                     List<TreeModelPartition> partitions) {
        List<SingleGroupVertexBuilder> vertices = Lists.newArrayList();
        Map<MeshPartDefinition, IntList> indices = Maps.newHashMap();
        PoseStack poseStack = new PoseStack();
        IndexCounter indexCounter = new IndexCounter();

        for (TreeModelPartition partition : partitions) {
            bakeTree(poseStack, model, instance, partition, partition.bone != null ? partition.bone.name() : "",
                    partition.bone, vertices, indices, indexCounter);
        }

        return SingleGroupVertexBuilder.loadVertexInformation(vertices, indices);
    }

    private static void bake(PoseStack poseStack, BedrockModelPartition partition, String partName,
                             @Nullable BedrockBone bone, List<SingleGroupVertexBuilder> vertices,
                             Map<MeshPartDefinition, IntList> indices, IndexCounter indexCounter) {
        if (bone == null) return;

        poseStack.pushPose();
        bone.translateAndRotateAndScale(poseStack);

        MeshPartDefinition partDefinition = BedrockMeshPart.of(partName);

        for (BedrockCube cube : bone.cubes) {
            partition.cubeTransformer.bakeCube(poseStack, partDefinition, new BedrockArmorCube(cube), vertices, indices, indexCounter);
        }

        for (BedrockBone childBone : bone.getChildren()) {
            bake(poseStack, partition, partName, childBone, vertices, indices, indexCounter);
        }

        poseStack.popPose();
    }

    private static void bakeTree(PoseStack poseStack, TreeBedrockModel model, TreeArmorModelInstance instance,
                                 TreeModelPartition partition, String partName, @Nullable BoneState bone,
                                 List<SingleGroupVertexBuilder> vertices,
                                 Map<MeshPartDefinition, IntList> indices, IndexCounter indexCounter) {
        if (bone == null) return;

        poseStack.pushPose();
        bone.translateAndRotateAndScale(poseStack);

        MeshPartDefinition partDefinition = BedrockMeshPart.of(partName);
        TreeBoneDefinition definition = model.bone(bone.index());

        for (ICube cube : definition.cubes()) {
            partition.cubeTransformer.bakeCube(poseStack, partDefinition, new TreeArmorCube(cube), vertices, indices, indexCounter);
        }

        for (int childIndex : definition.children()) {
            bakeTree(poseStack, model, instance, partition, partName, instance.getBone(childIndex), vertices, indices, indexCounter);
        }

        poseStack.popPose();
    }

    // ========== Vertex computation helpers ==========

    /**
     * Compute 8 vertex positions for a cube, transformed by the pose matrix.
     * Same math as BedrockCubeBox.prepareVertices but thread-safe (no shared static arrays).
     */
    static Vector3f[] computeVertices(Matrix4f pose, ArmorCube cube) {
        Matrix4f cubePose = cube.transformedPose(pose);
        float x = cube.x(), y = cube.y(), z = cube.z();
        float w = cube.width(), h = cube.height(), d = cube.depth();

        Vector3f edgeX = new Vector3f(cubePose.m00(), cubePose.m01(), cubePose.m02()).mul(w);
        Vector3f edgeY = new Vector3f(cubePose.m10(), cubePose.m11(), cubePose.m12()).mul(h);
        Vector3f edgeZ = new Vector3f(cubePose.m20(), cubePose.m21(), cubePose.m22()).mul(d);

        Vector3f v0 = new Vector3f(x, y, z).mulPosition(cubePose);
        Vector3f v1 = new Vector3f(v0).add(edgeX);
        Vector3f v2 = new Vector3f(v1).add(edgeY);
        Vector3f v3 = new Vector3f(v0).add(edgeY);
        Vector3f v4 = new Vector3f(v0).add(edgeZ);
        Vector3f v5 = new Vector3f(v1).add(edgeZ);
        Vector3f v6 = new Vector3f(v2).add(edgeZ);
        Vector3f v7 = new Vector3f(v3).add(edgeZ);

        return new Vector3f[]{v0, v1, v2, v3, v4, v5, v6, v7};
    }

    static Vec3 getCenterOfCube(PoseStack poseStack, ArmorCube cube) {
        Matrix4f matrix = poseStack.last().pose();
        Vector3f[] verts = computeVertices(matrix, cube);

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (Vector3f v : verts) {
            if (minX > v.x) minX = v.x;
            if (minY > v.y) minY = v.y;
            if (minZ > v.z) minZ = v.z;
            if (maxX < v.x) maxX = v.x;
            if (maxY < v.y) maxY = v.y;
            if (maxZ < v.z) maxZ = v.z;
        }

        return new Vec3(minX + (maxX - minX) * 0.5D, minY + (maxY - minY) * 0.5D, minZ + (maxZ - minZ) * 0.5D);
    }

    static Direction getFaceDirection(int faceIndex) {
        return Direction.values()[faceIndex];
    }

    static Vector3f getClipPoint(Vector3f pos1, Vector3f pos2, float yClip) {
        Vector3f direct = new Vector3f(pos2).sub(pos1);
        direct.mul((yClip - pos1.y()) / (pos2.y() - pos1.y()));
        return new Vector3f(pos1).add(direct);
    }

    static PosTexVertex makeVertex(Vector3f pos, float u, float v) {
        return new PosTexVertex(pos.x, pos.y, pos.z, u, v);
    }

    private interface ArmorCube {
        float x();

        float y();

        float z();

        float width();

        float height();

        float depth();

        float getU(int face, int vertex);

        float getV(int face, int vertex);

        boolean isEmptyFace(int face);

        Matrix4f transformedPose(Matrix4f pose);

        Vector3f transformedNormal(Direction direction, Matrix3f normalMatrix);
    }

    private record BedrockArmorCube(BedrockCube cube) implements ArmorCube {
        @Override
        public float x() {
            return cube.x();
        }

        @Override
        public float y() {
            return cube.y();
        }

        @Override
        public float z() {
            return cube.z();
        }

        @Override
        public float width() {
            return cube.width();
        }

        @Override
        public float height() {
            return cube.height();
        }

        @Override
        public float depth() {
            return cube.depth();
        }

        @Override
        public float getU(int face, int vertex) {
            return cube.getU(face, vertex);
        }

        @Override
        public float getV(int face, int vertex) {
            return cube.getV(face, vertex);
        }

        @Override
        public boolean isEmptyFace(int face) {
            return cube.isEmptyFace(face);
        }

        @Override
        public Matrix4f transformedPose(Matrix4f pose) {
            return pose;
        }

        @Override
        public Vector3f transformedNormal(Direction direction, Matrix3f normalMatrix) {
            return new Vector3f(direction.step()).mul(normalMatrix);
        }
    }

    private record TreeArmorCube(ICube cube) implements ArmorCube {
        @Override
        public float x() {
            return cube.x();
        }

        @Override
        public float y() {
            return cube.y();
        }

        @Override
        public float z() {
            return cube.z();
        }

        @Override
        public float width() {
            return cube.width();
        }

        @Override
        public float height() {
            return cube.height();
        }

        @Override
        public float depth() {
            return cube.depth();
        }

        @Override
        public float getU(int face, int vertex) {
            if (cube instanceof CubeBox box) {
                return box.uv(box.uvOrder(face, vertex == 0 || vertex == 3 ? 1 : 0));
            }
            if (cube instanceof CubePerFace perFace) {
                return perFace.faceUv(face)[vertex * 2];
            }
            return 0.0F;
        }

        @Override
        public float getV(int face, int vertex) {
            if (cube instanceof CubeBox box) {
                return box.uv(box.uvOrder(face, vertex <= 1 ? 2 : 3));
            }
            if (cube instanceof CubePerFace perFace) {
                return perFace.faceUv(face)[vertex * 2 + 1];
            }
            return 0.0F;
        }

        @Override
        public boolean isEmptyFace(int face) {
            return cube instanceof CubePerFace perFace && perFace.isEmptyFace(face);
        }

        @Override
        public Matrix4f transformedPose(Matrix4f pose) {
            if (!cube.hasRotation()) {
                return pose;
            }
            Matrix4f transformed = new Matrix4f(pose);
            float[] pivot = cube.pivot();
            Quaternionf rotation = cube.rotation();
            transformed.translate(pivot[0], pivot[1], pivot[2]);
            transformed.rotate(rotation);
            transformed.translate(-pivot[0], -pivot[1], -pivot[2]);
            return transformed;
        }

        @Override
        public Vector3f transformedNormal(Direction direction, Matrix3f normalMatrix) {
            Vector3f normal = new Vector3f(direction.step());
            if (cube.hasRotation()) {
                normal.rotate(cube.rotation());
                if (normal.lengthSquared() > 1.0E-12f) normal.normalize();
            }
            normal.mul(normalMatrix);
            if (normal.lengthSquared() > 1.0E-12f) normal.normalize();
            return normal;
        }
    }

    /**
     * Triangulate a quad (4 vertices) into two triangles, matching EpicFight's winding order.
     * Copied from HumanoidModelTransformer.PartTransformer.triangluatePolygon (package-private).
     */
    static void triangulatePolygon(Map<MeshPartDefinition, IntList> indices, MeshPartDefinition partDefinition, IndexCounter indexCounter) {
        IntList list = indices.computeIfAbsent(partDefinition, k -> new IntArrayList());
        int base = indexCounter.index;

        // First triangle: 0, 1, 3
        list.add(base);
        list.add(base);
        list.add(base);

        list.add(base + 1);
        list.add(base + 1);
        list.add(base + 1);

        list.add(base + 3);
        list.add(base + 3);
        list.add(base + 3);

        // Second triangle: 3, 1, 2
        list.add(base + 3);
        list.add(base + 3);
        list.add(base + 3);

        list.add(base + 1);
        list.add(base + 1);
        list.add(base + 1);

        list.add(base + 2);
        list.add(base + 2);
        list.add(base + 2);

        indexCounter.index += 4;
    }

    // ========== Local vertex type (replaces package-private ModelPart.Vertex) ==========

    static class PosTexVertex {
        final Vector3f pos;
        final float u;
        final float v;

        PosTexVertex(float x, float y, float z, float u, float v) {
            this.pos = new Vector3f(x, y, z);
            this.u = u;
            this.v = v;
        }

        PosTexVertex(Vector3f pos, float u, float v) {
            this.pos = new Vector3f(pos);
            this.u = u;
            this.v = v;
        }
    }

    static class IndexCounter {
        int index = 0;
    }

    // ========== Cube Transformer hierarchy ==========

    static abstract class CubeTransformer {
        abstract void bakeCube(PoseStack poseStack, MeshPartDefinition partName, ArmorCube cube,
                               List<SingleGroupVertexBuilder> vertices, Map<MeshPartDefinition, IntList> indices,
                               IndexCounter indexCounter);
    }

    static class SimpleTransformer extends CubeTransformer {
        final int jointId;

        SimpleTransformer(int jointId) {
            this.jointId = jointId;
        }

        @Override
        void bakeCube(PoseStack poseStack, MeshPartDefinition partName, ArmorCube cube,
                      List<SingleGroupVertexBuilder> vertices, Map<MeshPartDefinition, IntList> indices,
                      IndexCounter indexCounter) {
            Matrix4f pose = poseStack.last().pose();
            Vector3f[] cubeVerts = computeVertices(pose, cube);

            for (int face = 0; face < BedrockCube.NUM_CUBE_FACES; face++) {
                if (cube.isEmptyFace(face)) continue;

                int[] order = BedrockCube.VERTEX_ORDER[face];
                Direction dir = getFaceDirection(face);
                Vector3f norm = cube.transformedNormal(dir, poseStack.last().normal());

                for (int v = 0; v < 4; v++) {
                    Vector3f pos = cubeVerts[order[v]];
                    vertices.add(new SingleGroupVertexBuilder()
                            .setPosition(new Vec3f(pos.x, pos.y, pos.z))
                            .setNormal(new Vec3f(norm.x(), norm.y(), norm.z()))
                            .setTextureCoordinate(new Vec2f(cube.getU(face, v), cube.getV(face, v)))
                            .setEffectiveJointIDs(new Vec3f(this.jointId, 0, 0))
                            .setEffectiveJointWeights(new Vec3f(1.0F, 0.0F, 0.0F))
                            .setEffectiveJointNumber(1)
                    );
                }

                triangulatePolygon(indices, partName, indexCounter);
            }
        }
    }

    static class ChestPartTransformer extends CubeTransformer {
        static final float X_PLANE = 0.0F;
        static final VertexWeight[] WEIGHT_ALONG_Y = {
                new VertexWeight(13.6666F, 0.230F, 0.770F),
                new VertexWeight(15.8333F, 0.254F, 0.746F),
                new VertexWeight(18.0F, 0.5F, 0.5F),
                new VertexWeight(20.1666F, 0.744F, 0.256F),
                new VertexWeight(22.3333F, 0.770F, 0.230F)
        };

        final SimpleTransformer upperAttachmentTransformer;
        final SimpleTransformer lowerAttachmentTransformer;
        final AABB noneAttachmentArea;
        final float yClipCoord;

        ChestPartTransformer(int upperJoint, int lowerJoint, float yBasis, AABB noneAttachmentArea) {
            this.noneAttachmentArea = noneAttachmentArea;
            this.upperAttachmentTransformer = new SimpleTransformer(upperJoint);
            this.lowerAttachmentTransformer = new SimpleTransformer(lowerJoint);
            this.yClipCoord = yBasis;
        }

        @Override
        void bakeCube(PoseStack poseStack, MeshPartDefinition partName, ArmorCube cube,
                      List<SingleGroupVertexBuilder> vertices, Map<MeshPartDefinition, IntList> indices,
                      IndexCounter indexCounter) {
            Vec3 centerOfCube = getCenterOfCube(poseStack, cube);

            if (!this.noneAttachmentArea.contains(centerOfCube)) {
                if (centerOfCube.y < this.yClipCoord) {
                    this.lowerAttachmentTransformer.bakeCube(poseStack, partName, cube, vertices, indices, indexCounter);
                } else {
                    this.upperAttachmentTransformer.bakeCube(poseStack, partName, cube, vertices, indices, indexCounter);
                }
                return;
            }

            Matrix4f matrix = poseStack.last().pose();
            Vector3f[] cubeVerts = computeVertices(matrix, cube);
            List<AnimatedPolygon> xClipPolygons = Lists.newArrayList();
            List<AnimatedPolygon> xyClipPolygons = Lists.newArrayList();

            for (int face = 0; face < BedrockCube.NUM_CUBE_FACES; face++) {
                if (cube.isEmptyFace(face)) continue;

                int[] order = BedrockCube.VERTEX_ORDER[face];
                PosTexVertex pos0 = makeVertex(cubeVerts[order[0]], cube.getU(face, 0), cube.getV(face, 0));
                PosTexVertex pos1 = makeVertex(cubeVerts[order[1]], cube.getU(face, 1), cube.getV(face, 1));
                PosTexVertex pos2 = makeVertex(cubeVerts[order[2]], cube.getU(face, 2), cube.getV(face, 2));
                PosTexVertex pos3 = makeVertex(cubeVerts[order[3]], cube.getU(face, 3), cube.getV(face, 3));
                Direction direction = getFaceDirection(face);
                VertexWeight pos0Weight = getYClipWeight(pos0.pos.y());
                VertexWeight pos1Weight = getYClipWeight(pos1.pos.y());
                VertexWeight pos3Weight = getYClipWeight(pos3.pos.y());

                if (pos1.pos.x() > X_PLANE != pos2.pos.x() > X_PLANE) {
                    float distance = pos2.pos.x() - pos1.pos.x();
                    float textureU = pos1.u + (pos2.u - pos1.u) * ((X_PLANE - pos1.pos.x()) / distance);
                    PosTexVertex pos4 = new PosTexVertex(X_PLANE, pos0.pos.y(), pos0.pos.z(), textureU, pos0.v);
                    PosTexVertex pos5 = new PosTexVertex(X_PLANE, pos1.pos.y(), pos1.pos.z(), textureU, pos1.v);

                    xClipPolygons.add(new AnimatedPolygon(new AnimatedVertex[]{
                            new AnimatedVertex(pos0, 8, 7, 0, pos0Weight.chestWeight, pos0Weight.torsoWeight, 0),
                            new AnimatedVertex(pos4, 8, 7, 0, pos0Weight.chestWeight, pos0Weight.torsoWeight, 0),
                            new AnimatedVertex(pos5, 8, 7, 0, pos1Weight.chestWeight, pos1Weight.torsoWeight, 0),
                            new AnimatedVertex(pos3, 8, 7, 0, pos3Weight.chestWeight, pos3Weight.torsoWeight, 0)
                    }, direction));
                    xClipPolygons.add(new AnimatedPolygon(new AnimatedVertex[]{
                            new AnimatedVertex(pos4, 8, 7, 0, pos0Weight.chestWeight, pos0Weight.torsoWeight, 0),
                            new AnimatedVertex(pos1, 8, 7, 0, pos1Weight.chestWeight, pos1Weight.torsoWeight, 0),
                            new AnimatedVertex(pos2, 8, 7, 0, getYClipWeight(pos2.pos.y()).chestWeight, getYClipWeight(pos2.pos.y()).torsoWeight, 0),
                            new AnimatedVertex(pos5, 8, 7, 0, pos1Weight.chestWeight, pos1Weight.torsoWeight, 0)
                    }, direction));
                } else {
                    VertexWeight pos2Weight = getYClipWeight(pos2.pos.y());
                    xClipPolygons.add(new AnimatedPolygon(new AnimatedVertex[]{
                            new AnimatedVertex(pos0, 8, 7, 0, pos0Weight.chestWeight, pos0Weight.torsoWeight, 0),
                            new AnimatedVertex(pos1, 8, 7, 0, pos1Weight.chestWeight, pos1Weight.torsoWeight, 0),
                            new AnimatedVertex(pos2, 8, 7, 0, pos2Weight.chestWeight, pos2Weight.torsoWeight, 0),
                            new AnimatedVertex(pos3, 8, 7, 0, pos3Weight.chestWeight, pos3Weight.torsoWeight, 0)
                    }, direction));
                }
            }

            for (AnimatedPolygon polygon : xClipPolygons) {
                boolean upsideDown = polygon.animatedVertexPositions[1].pos.y() > polygon.animatedVertexPositions[2].pos.y();
                AnimatedVertex p0 = upsideDown ? polygon.animatedVertexPositions[2] : polygon.animatedVertexPositions[0];
                AnimatedVertex p1 = upsideDown ? polygon.animatedVertexPositions[3] : polygon.animatedVertexPositions[1];
                AnimatedVertex p2 = upsideDown ? polygon.animatedVertexPositions[0] : polygon.animatedVertexPositions[2];
                AnimatedVertex p3 = upsideDown ? polygon.animatedVertexPositions[1] : polygon.animatedVertexPositions[3];
                Direction direction = getFaceDirection(polygon.faceIndex);
                List<VertexWeight> vertexWeights = getMiddleYClipWeights(p1.pos.y(), p2.pos.y());
                List<AnimatedVertex> animatedVertices = Lists.newArrayList();
                animatedVertices.add(p0);
                animatedVertices.add(p1);

                if (!vertexWeights.isEmpty()) {
                    for (VertexWeight vertexWeight : vertexWeights) {
                        float distance = p2.pos.y() - p1.pos.y();
                        float textureV = p1.v + (p2.v - p1.v) * ((vertexWeight.yClipCoord - p1.pos.y()) / distance);
                        Vector3f clipPos1 = getClipPoint(p1.pos, p2.pos, vertexWeight.yClipCoord);
                        Vector3f clipPos2 = getClipPoint(p0.pos, p3.pos, vertexWeight.yClipCoord);
                        PosTexVertex vt4 = new PosTexVertex(clipPos2, p0.u, textureV);
                        PosTexVertex vt5 = new PosTexVertex(clipPos1, p1.u, textureV);
                        animatedVertices.add(new AnimatedVertex(vt4, 8, 7, 0, vertexWeight.chestWeight, vertexWeight.torsoWeight, 0));
                        animatedVertices.add(new AnimatedVertex(vt5, 8, 7, 0, vertexWeight.chestWeight, vertexWeight.torsoWeight, 0));
                    }
                }

                animatedVertices.add(p3);
                animatedVertices.add(p2);

                for (int i = 0; i < (animatedVertices.size() - 2) / 2; i++) {
                    int start = i * 2;
                    AnimatedVertex a0 = animatedVertices.get(start);
                    AnimatedVertex a1 = animatedVertices.get(start + 1);
                    AnimatedVertex a2 = animatedVertices.get(start + 3);
                    AnimatedVertex a3 = animatedVertices.get(start + 2);
                    xyClipPolygons.add(new AnimatedPolygon(new AnimatedVertex[]{
                            new AnimatedVertex(a0, 8, 7, 0, a0.weight.x, a0.weight.y, 0),
                            new AnimatedVertex(a1, 8, 7, 0, a1.weight.x, a1.weight.y, 0),
                            new AnimatedVertex(a2, 8, 7, 0, a2.weight.x, a2.weight.y, 0),
                            new AnimatedVertex(a3, 8, 7, 0, a3.weight.x, a3.weight.y, 0)
                    }, direction));
                }
            }

            for (AnimatedPolygon polygon : xyClipPolygons) {
                Vector3f norm = cube.transformedNormal(getFaceDirection(polygon.faceIndex), poseStack.last().normal());

                for (AnimatedVertex vertex : polygon.animatedVertexPositions) {
                    float weight1 = vertex.weight.x;
                    float weight2 = vertex.weight.y;
                    int joint1 = vertex.jointId.getX();
                    int joint2 = vertex.jointId.getY();
                    int count = weight1 > 0.0F && weight2 > 0.0F ? 2 : 1;

                    if (weight1 <= 0.0F) {
                        joint1 = joint2;
                        weight1 = weight2;
                    }

                    vertices.add(new SingleGroupVertexBuilder()
                            .setPosition(new Vec3f(vertex.pos.x(), vertex.pos.y(), vertex.pos.z()))
                            .setNormal(new Vec3f(norm.x(), norm.y(), norm.z()))
                            .setTextureCoordinate(new Vec2f(vertex.u, vertex.v))
                            .setEffectiveJointIDs(new Vec3f(joint1, joint2, 0))
                            .setEffectiveJointWeights(new Vec3f(weight1, weight2, 0.0F))
                            .setEffectiveJointNumber(count)
                    );
                }

                triangulatePolygon(indices, partName, indexCounter);
            }
        }

        static VertexWeight getYClipWeight(float y) {
            if (y < WEIGHT_ALONG_Y[0].yClipCoord) {
                return new VertexWeight(y, 0.0F, 1.0F);
            }

            int index = -1;
            for (int i = 0; i < WEIGHT_ALONG_Y.length; i++) {
                if (y < WEIGHT_ALONG_Y[i].yClipCoord) {
                    index = i;
                    break;
                }
            }

            if (index > 0) {
                VertexWeight pair = WEIGHT_ALONG_Y[index];
                return new VertexWeight(y, pair.chestWeight, pair.torsoWeight);
            }

            return new VertexWeight(y, 1.0F, 0.0F);
        }

        static List<VertexWeight> getMiddleYClipWeights(float minY, float maxY) {
            List<VertexWeight> cutYs = Lists.newArrayList();
            for (VertexWeight vertexWeight : WEIGHT_ALONG_Y) {
                if (vertexWeight.yClipCoord > minY && maxY >= vertexWeight.yClipCoord) {
                    cutYs.add(vertexWeight);
                }
            }
            return cutYs;
        }

        static class VertexWeight {
            final float yClipCoord;
            final float chestWeight;
            final float torsoWeight;

            VertexWeight(float yClipCoord, float chestWeight, float torsoWeight) {
                this.yClipCoord = yClipCoord;
                this.chestWeight = chestWeight;
                this.torsoWeight = torsoWeight;
            }
        }
    }

    static class LimbPartTransformer extends CubeTransformer {
        final int upperJoint;
        final int lowerJoint;
        final int middleJoint;
        final boolean bendInFront;
        final SimpleTransformer upperAttachmentTransformer;
        final SimpleTransformer lowerAttachmentTransformer;
        final AABB noneAttachmentArea;
        final float yClipCoord;

        LimbPartTransformer(int upperJoint, int lowerJoint, int middleJoint, float yClipCoord,
                            boolean bendInFront, AABB noneAttachmentArea) {
            this.upperJoint = upperJoint;
            this.lowerJoint = lowerJoint;
            this.middleJoint = middleJoint;
            this.bendInFront = bendInFront;
            this.upperAttachmentTransformer = new SimpleTransformer(upperJoint);
            this.lowerAttachmentTransformer = new SimpleTransformer(lowerJoint);
            this.noneAttachmentArea = noneAttachmentArea;
            this.yClipCoord = yClipCoord;
        }

        @Override
        void bakeCube(PoseStack poseStack, MeshPartDefinition partName, ArmorCube cube,
                      List<SingleGroupVertexBuilder> vertices, Map<MeshPartDefinition, IntList> indices,
                      IndexCounter indexCounter) {
            Vec3 centerOfCube = getCenterOfCube(poseStack, cube);

            if (!this.noneAttachmentArea.contains(centerOfCube)) {
                if (centerOfCube.y < this.yClipCoord) {
                    this.lowerAttachmentTransformer.bakeCube(poseStack, partName, cube, vertices, indices, indexCounter);
                } else {
                    this.upperAttachmentTransformer.bakeCube(poseStack, partName, cube, vertices, indices, indexCounter);
                }
                return;
            }

            Matrix4f matrix = poseStack.last().pose();
            Vector3f[] cubeVerts = computeVertices(matrix, cube);
            List<AnimatedPolygon> polygons = Lists.newArrayList();

            for (int face = 0; face < BedrockCube.NUM_CUBE_FACES; face++) {
                if (cube.isEmptyFace(face)) continue;

                int[] order = BedrockCube.VERTEX_ORDER[face];
                PosTexVertex pos0 = makeVertex(cubeVerts[order[0]], cube.getU(face, 0), cube.getV(face, 0));
                PosTexVertex pos1 = makeVertex(cubeVerts[order[1]], cube.getU(face, 1), cube.getV(face, 1));
                PosTexVertex pos2 = makeVertex(cubeVerts[order[2]], cube.getU(face, 2), cube.getV(face, 2));
                PosTexVertex pos3 = makeVertex(cubeVerts[order[3]], cube.getU(face, 3), cube.getV(face, 3));
                Direction direction = getFaceDirection(face);

                if (pos1.pos.y() > this.yClipCoord != pos2.pos.y() > this.yClipCoord) {
                    float distance = pos2.pos.y() - pos1.pos.y();
                    float textureV = pos1.v + (pos2.v - pos1.v) * ((this.yClipCoord - pos1.pos.y()) / distance);
                    Vector3f clipPos1 = getClipPoint(pos1.pos, pos2.pos, this.yClipCoord);
                    Vector3f clipPos2 = getClipPoint(pos0.pos, pos3.pos, this.yClipCoord);
                    PosTexVertex pos4 = new PosTexVertex(clipPos2, pos0.u, textureV);
                    PosTexVertex pos5 = new PosTexVertex(clipPos1, pos1.u, textureV);

                    int upperId, lowerId;
                    if (distance > 0) {
                        upperId = this.lowerJoint;
                        lowerId = this.upperJoint;
                    } else {
                        upperId = this.upperJoint;
                        lowerId = this.lowerJoint;
                    }

                    polygons.add(new AnimatedPolygon(new AnimatedVertex[]{
                            new AnimatedVertex(pos0, upperId), new AnimatedVertex(pos1, upperId),
                            new AnimatedVertex(pos5, upperId), new AnimatedVertex(pos4, upperId)
                    }, direction));
                    polygons.add(new AnimatedPolygon(new AnimatedVertex[]{
                            new AnimatedVertex(pos4, lowerId), new AnimatedVertex(pos5, lowerId),
                            new AnimatedVertex(pos2, lowerId), new AnimatedVertex(pos3, lowerId)
                    }, direction));

                    boolean hasSameZ = pos4.pos.z() < 0.0F == pos5.pos.z() < 0.0F;
                    boolean isFront = hasSameZ && (pos4.pos.z() < 0.0F == this.bendInFront);

                    if (isFront) {
                        polygons.add(new AnimatedPolygon(new AnimatedVertex[]{
                                new AnimatedVertex(pos4, this.middleJoint), new AnimatedVertex(pos5, this.middleJoint),
                                new AnimatedVertex(pos5, this.upperJoint), new AnimatedVertex(pos4, this.upperJoint)
                        }, 0.001F, direction));
                        polygons.add(new AnimatedPolygon(new AnimatedVertex[]{
                                new AnimatedVertex(pos4, this.lowerJoint), new AnimatedVertex(pos5, this.lowerJoint),
                                new AnimatedVertex(pos5, this.middleJoint), new AnimatedVertex(pos4, this.middleJoint)
                        }, 0.001F, direction));
                    } else if (!hasSameZ) {
                        boolean startFront = pos4.pos.z() > 0;
                        int firstJoint = this.lowerJoint;
                        int secondJoint = this.lowerJoint;
                        int thirdJoint = startFront ? this.upperJoint : this.middleJoint;
                        int fourthJoint = startFront ? this.middleJoint : this.upperJoint;
                        int fifthJoint = this.upperJoint;
                        int sixthJoint = this.upperJoint;

                        polygons.add(new AnimatedPolygon(new AnimatedVertex[]{
                                new AnimatedVertex(pos4, firstJoint), new AnimatedVertex(pos5, secondJoint),
                                new AnimatedVertex(pos5, thirdJoint), new AnimatedVertex(pos4, fourthJoint)
                        }, 0.001F, direction));
                        polygons.add(new AnimatedPolygon(new AnimatedVertex[]{
                                new AnimatedVertex(pos4, fourthJoint), new AnimatedVertex(pos5, thirdJoint),
                                new AnimatedVertex(pos5, fifthJoint), new AnimatedVertex(pos4, sixthJoint)
                        }, 0.001F, direction));
                    }
                } else {
                    int jointId = pos0.pos.y() > this.yClipCoord ? this.upperJoint : this.lowerJoint;
                    polygons.add(new AnimatedPolygon(new AnimatedVertex[]{
                            new AnimatedVertex(pos0, jointId), new AnimatedVertex(pos1, jointId),
                            new AnimatedVertex(pos2, jointId), new AnimatedVertex(pos3, jointId)
                    }, direction));
                }
            }

            for (AnimatedPolygon quad : polygons) {
                Vector3f norm = cube.transformedNormal(getFaceDirection(quad.faceIndex), poseStack.last().normal());

                for (AnimatedVertex vertex : quad.animatedVertexPositions) {
                    vertices.add(new SingleGroupVertexBuilder()
                            .setPosition(new Vec3f(vertex.pos.x(), vertex.pos.y(), vertex.pos.z()))
                            .setNormal(new Vec3f(norm.x(), norm.y(), norm.z()))
                            .setTextureCoordinate(new Vec2f(vertex.u, vertex.v))
                            .setEffectiveJointIDs(new Vec3f(vertex.jointId.getX(), 0, 0))
                            .setEffectiveJointWeights(new Vec3f(1.0F, 0.0F, 0.0F))
                            .setEffectiveJointNumber(1)
                    );
                }

                triangulatePolygon(indices, partName, indexCounter);
            }
        }
    }

    // ========== Shared types ==========

    static class AnimatedVertex {
        final Vector3f pos;
        final float u;
        final float v;
        final Vec3i jointId;
        final Vec3f weight;

        AnimatedVertex(PosTexVertex posTexVertex, int jointId) {
            this(posTexVertex, jointId, 0, 0, 1.0F, 0.0F, 0.0F);
        }

        AnimatedVertex(PosTexVertex posTexVertex, int jointId1, int jointId2, int jointId3,
                       float weight1, float weight2, float weight3) {
            this(posTexVertex.pos, posTexVertex.u, posTexVertex.v,
                    new Vec3i(jointId1, jointId2, jointId3), new Vec3f(weight1, weight2, weight3));
        }

        AnimatedVertex(AnimatedVertex other, int jointId1, int jointId2, int jointId3,
                       float weight1, float weight2, float weight3) {
            this(other.pos, other.u, other.v,
                    new Vec3i(jointId1, jointId2, jointId3), new Vec3f(weight1, weight2, weight3));
        }

        AnimatedVertex(AnimatedVertex other, float u, float v, Vec3i ids, Vec3f weights) {
            this(other.pos, u, v, ids, weights);
        }

        AnimatedVertex(Vector3f pos, float u, float v, Vec3i ids, Vec3f weights) {
            this.pos = new Vector3f(pos);
            this.u = u;
            this.v = v;
            this.jointId = ids;
            this.weight = weights;
        }
    }

    static class AnimatedPolygon {
        public final AnimatedVertex[] animatedVertexPositions;
        public final Vector3f normal;
        public final int faceIndex;

        AnimatedPolygon(AnimatedVertex[] positionsIn, Direction directionIn) {
            this.animatedVertexPositions = positionsIn;
            this.normal = directionIn.step();
            this.faceIndex = directionIn.ordinal();
        }

        AnimatedPolygon(AnimatedVertex[] positionsIn, float cor, Direction directionIn) {
            this.animatedVertexPositions = positionsIn;
            positionsIn[0] = new AnimatedVertex(positionsIn[0], positionsIn[0].u, positionsIn[0].v + cor, positionsIn[0].jointId, positionsIn[0].weight);
            positionsIn[1] = new AnimatedVertex(positionsIn[1], positionsIn[1].u, positionsIn[1].v + cor, positionsIn[1].jointId, positionsIn[1].weight);
            positionsIn[2] = new AnimatedVertex(positionsIn[2], positionsIn[2].u, positionsIn[2].v - cor, positionsIn[2].jointId, positionsIn[2].weight);
            positionsIn[3] = new AnimatedVertex(positionsIn[3], positionsIn[3].u, positionsIn[3].v - cor, positionsIn[3].jointId, positionsIn[3].weight);
            this.normal = directionIn.step();
            this.faceIndex = directionIn.ordinal();
        }
    }

    private record BedrockMeshPart(String partName) implements MeshPartDefinition {
        static BedrockMeshPart of(String name) {
            return new BedrockMeshPart(name);
        }

        @Override
        public Mesh.RenderProperties renderProperties() {
            return null;
        }

        @Override
        public Supplier<OpenMatrix4f> getModelPartAnimationProvider() {
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof MeshPartDefinition other) {
                return this.partName.equals(other.partName());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return this.partName.hashCode();
        }
    }
}
