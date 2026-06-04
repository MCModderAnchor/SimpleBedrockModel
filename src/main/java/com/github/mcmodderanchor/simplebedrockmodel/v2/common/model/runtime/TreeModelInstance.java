package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.LocatorData;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.TreeBedrockModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.Map;

public class TreeModelInstance extends BoneTreeInstance {
    private final TreeBedrockModel baseModel;

    public TreeModelInstance(TreeBedrockModel baseModel) {
        super(baseModel.bones(), baseModel.getBindPose());
        this.baseModel = baseModel;
    }

    public TreeBedrockModel baseModel() {
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
        Map.Entry<Integer, LocatorData> locator = baseModel.locator(locatorName);
        if (locator == null) return null;
        Matrix4f transform = new Matrix4f(getGlobalTransform(locator.getKey()));
        TreeBedrockModel.applyLocatorLocalTransform(transform, locator.getValue());
        return transform;
    }

    @Nullable
    public Matrix4f getQueryTransform(String queryName) {
        BoneState bone = getBone(queryName);
        return bone == null ? null : getGlobalTransform(bone.index());
    }
}
