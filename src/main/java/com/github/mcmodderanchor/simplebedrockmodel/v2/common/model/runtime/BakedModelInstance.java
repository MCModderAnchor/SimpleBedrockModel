package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.LocatorData;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BoneLocator;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.QueryTransform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * 运行时模型对象，是动画应用的目标。
 */
public class BakedModelInstance extends BoneTreeInstance {
    private final BakedBedrockModel baseModel;

    public BakedModelInstance(BakedBedrockModel baseModel) {
        super(baseModel.bones(), baseModel.getBindPose());
        this.baseModel = baseModel;
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

    @Override
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
