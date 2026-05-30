package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.LocatorData;
import com.maydaymemory.mae.basic.ArrayPoseBuilder;
import com.maydaymemory.mae.basic.BoneTransform;
import com.maydaymemory.mae.basic.Pose;
import com.maydaymemory.mae.basic.PoseBuilder;
import com.maydaymemory.mae.basic.Skeleton;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3fc;

import java.lang.Math;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 运行时模型对象，是动画应用的目标
 */
public class BedrockModelInstance implements Skeleton {
    private final BakedBedrockModel baseModel;
    private final BoneState[] bones;

    public BedrockModelInstance(BakedBedrockModel baseModel) {
        this.baseModel = baseModel;
        this.bones = new BoneState[baseModel.boneCount()];
        for (int i = 0; i < bones.length; i++) {
            bones[i] = new BoneState(baseModel.bone(i));
        }
    }

    public BakedBedrockModel baseModel() {
        return baseModel;
    }

    @OnlyIn(Dist.CLIENT)
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay) {
        baseModel.renderToBuffer(this, poseStack, buffer, packedLight, packedOverlay);
    }

    @OnlyIn(Dist.CLIENT)
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        baseModel.renderToBuffer(this, poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @OnlyIn(Dist.CLIENT)
    public void renderToBuffer(PoseStack poseStack, MultiBufferSource bufferSource, RenderType quadRenderType,
                               RenderType triangleRenderType, int packedLight, int packedOverlay) {
        baseModel.renderToBuffer(this, poseStack, bufferSource, quadRenderType, triangleRenderType, packedLight, packedOverlay);
    }

    @OnlyIn(Dist.CLIENT)
    public void renderToBuffer(PoseStack poseStack, MultiBufferSource bufferSource, RenderType quadRenderType,
                               RenderType triangleRenderType, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        baseModel.renderToBuffer(this, poseStack, bufferSource, quadRenderType, triangleRenderType, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Nullable
    public BoneState getBone(String name) {
        int index = getIndex(name);
        return index >= 0 ? bones[index] : null;
    }

    @Nullable
    public BoneState getBone(int index) {
        return index >= 0 && index < bones.length ? bones[index] : null;
    }

    public BoneState[] getBoneIndexes() {
        return bones.clone();
    }

    public void resetPose() {
        for (BoneState bone : bones) {
            bone.reset();
        }
    }

    public Matrix4f getGlobalTransform(int index) {
        if (index < 0 || index >= bones.length) return new Matrix4f();
        ArrayList<BoneState> chain = new ArrayList<>();
        BoneState bone = bones[index];
        while (bone != null) {
            chain.add(bone);
            int parentIndex = bone.parentIndex();
            bone = parentIndex >= 0 && parentIndex < bones.length ? bones[parentIndex] : null;
        }
        Matrix4f matrix = new Matrix4f();
        for (int i = chain.size() - 1; i >= 0; i--) {
            matrix.mul(chain.get(i).getLocalTransform());
        }
        return matrix;
    }

    @Override
    public Collection<Integer> getChildren(int i) {
        BoneState bone = getBone(i);
        if (bone == null) {
            return List.of();
        }
        int[] children = bone.children();
        ArrayList<Integer> result = new ArrayList<>(children.length);
        for (int child : children) {
            result.add(child);
        }
        return result;
    }

    @Override
    public int getFather(int i) {
        BoneState bone = getBone(i);
        return bone == null ? -1 : bone.parentIndex();
    }

    @Override
    public void applyPose(Pose pose) {
        for (BoneTransform boneTransform : pose.getBoneTransforms()) {
            BoneState bone = getBone(boneTransform.boneIndex());
            if (bone == null) {
                continue;
            }
            Vector3fc translation = boneTransform.translation();
            Quaternionf rotation = new Quaternionf(boneTransform.rotation().asQuaternion());
            Vector3fc scale = boneTransform.scale();
            bone.x = translation.x();
            bone.y = translation.y();
            bone.z = translation.z();
            bone.rotation.set(rotation);
            bone.xScale = scale.x();
            bone.yScale = scale.y();
            bone.zScale = scale.z();
        }
    }

    @Override
    public Pose getPose() {
        PoseBuilder poseBuilder = new ArrayPoseBuilder();
        for (BoneState bone : bones) {
            poseBuilder.addBoneTransform(bone.getBoneTransform());
        }
        return poseBuilder.toPose();
    }

    @Override
    public Pose getBindPose() {
        return baseModel.getBindPose();
    }

    public int getIndex(String boneName) {
        return baseModel.getIndex(boneName);
    }

    @Nullable
    public Matrix4f getLocatorTransform(String locatorName) {
        BoneLocator boneLocator = baseModel.locator(locatorName);
        return boneLocator == null ? null : resolveLocatorTransform(boneLocator);
    }

    @Nullable
    public Matrix4f getQueryTransform(String queryName) {
        QueryTransform queryTransform = baseModel.queryTransform(queryName);
        return queryTransform == null ? null : resolveQueryTransform(queryTransform);
    }

    @Nullable
    private Matrix4f resolveLocatorTransform(BoneLocator boneLocator) {
        BoneState bone = getBone(boneLocator.boneIndex());
        if (bone == null) {
            return null;
        }
        Matrix4f transform = new Matrix4f(getGlobalTransform(bone.index()));
        applyLocatorLocalTransform(transform, boneLocator.data());
        return transform;
    }

    private Matrix4f resolveQueryTransform(QueryTransform queryTransform) {
        Matrix4f transform = queryTransform.attachBoneIndex() >= 0
                ? new Matrix4f(getGlobalTransform(queryTransform.attachBoneIndex()))
                : new Matrix4f();
        return transform.mul(queryTransform.localTransform());
    }

    private static void applyLocatorLocalTransform(Matrix4f transform, LocatorData locator) {
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
    }
}
