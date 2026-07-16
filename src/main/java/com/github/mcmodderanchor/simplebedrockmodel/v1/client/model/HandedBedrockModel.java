package com.github.mcmodderanchor.simplebedrockmodel.v1.client.model;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockBone;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockModelPOJO;
import com.github.mcmodderanchor.simplebedrockmodel.v1.util.RenderHelper;
import com.github.mcmodderanchor.simplebedrockmodel.v1.util.math.MathUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 带有双臂渲染的基岩模型，用于第一人称视角下的武器等物品的渲染。
 */
@Deprecated
public class HandedBedrockModel extends BedrockModelBase {
    private boolean renderHand = true;
    private final BedrockBone leftHandBone;
    private final BedrockBone rightHandBone;

    public HandedBedrockModel(BedrockModelPOJO pojo, @Nullable TransformScale scales) {
        super(pojo, scales);
        leftHandBone = getBone(this.getLeftHandBoneName());
        rightHandBone = getBone(this.getRightHandBoneName());
        if (leftHandBone != null) {
            leftHandBone.visible = false;
        }
        if (rightHandBone != null) {
            rightHandBone.visible = false;
        }
    }

    @NotNull
    public String getLeftHandBoneName() {
        return "lefthand_pos";
    }

    @NotNull
    public String getRightHandBoneName() {
        return "righthand_pos";
    }

    @ParametersAreNonnullByDefault
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay) {
        // 渲染枪械
        super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay);
        // 渲染双臂
        if (renderHand) {
            renderHands(poseStack, buffer, packedLight, packedOverlay);
        }
    }

    public void renderHands(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay) {
        if (leftHandBone != null) {
            Matrix4f transform = leftHandBone.getGlobalTransform();
            poseStack.pushPose();
            MathUtil.mulMatrix(poseStack, transform);
            RenderHelper.renderFirstPersonArm(Minecraft.getInstance().player, HumanoidArm.LEFT, poseStack, packedLight);
            poseStack.popPose();
        }
        if (rightHandBone != null) {
            Matrix4f transform = rightHandBone.getGlobalTransform();
            poseStack.pushPose();
            MathUtil.mulMatrix(poseStack, transform);
            RenderHelper.renderFirstPersonArm(Minecraft.getInstance().player, HumanoidArm.RIGHT, poseStack, packedLight);
            poseStack.popPose();
        }
    }

    public boolean isRenderHand() {
        return renderHand;
    }

    public void setRenderHand(boolean renderHand) {
        this.renderHand = renderHand;
    }
}
