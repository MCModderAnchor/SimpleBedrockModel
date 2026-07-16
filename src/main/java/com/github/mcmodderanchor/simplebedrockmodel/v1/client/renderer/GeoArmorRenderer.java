package com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.model.BedrockArmorModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockBone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

// 说是模型，实际上是一个适配器，用来敷衍原版的）
public class GeoArmorRenderer extends HumanoidModel implements IFPArmorHandRenderer, ICustomArmorRenderer {
    protected final BedrockArmorModel model;
    private final ResourceLocation texture;

    @Nullable
    protected LivingEntity livingEntity;
    @Nullable
    protected ItemStack itemStack;
    @Nullable
    protected EquipmentSlot equipmentSlot;
    @Nullable
    protected HumanoidModel<?> original;

    public GeoArmorRenderer(BedrockArmorModel origin, ResourceLocation texture) {
        super(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        this.model = origin;
        this.texture = texture;
    }

    public void preparePose(LivingEntity livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, HumanoidModel<?> original) {
        model.applyPose(model.getBindPose());

        copyModelPart(original.head, model.getArmorHead(), 0, 24, 0);
        copyModelPart(original.body, model.getArmorBody(), 0, 24, 0);
        copyModelPart(original.rightArm, model.getArmorRightArm(), 5, 22, 0);
        copyModelPart(original.leftArm, model.getArmorLeftArm(), -5, 22, 0);
        copyModelPart(original.rightLeg, model.getArmorRightLeg(), 1.9f, 12, 0);
        copyModelPart(original.leftLeg, model.getArmorLeftLeg(), -1.9f, 12, 0);
        copyModelPart(original.rightLeg, model.getArmorRightBoot(), 1.9f, 12, 0);
        copyModelPart(original.leftLeg, model.getArmorLeftBoot(), -1.9f, 12, 0);

        setVisibilityBySlot(equipmentSlot);

        this.livingEntity = livingEntity;
        this.itemStack = itemStack;
        this.equipmentSlot = equipmentSlot;
        this.original = original;
    }

    public void copyModelPart(ModelPart part, BedrockBone bone, float initX, float initY, float initZ) {
        if (bone != null) {
            float deltaX = part.x - initX;
            float deltaY = part.y - initY;
            float deltaZ = part.z - initZ;

            bone.x += deltaX;
            bone.y += deltaY;
            bone.z += deltaZ;

            bone.rotation.rotationZYX(part.zRot, part.yRot, part.xRot);

            bone.xScale = -part.xScale;
            bone.yScale = -part.yScale;
            bone.zScale = part.zScale;
            bone.visible = part.visible;
        }
    }

    public void setVisibilityBySlot(EquipmentSlot slot) {
        setBoneVisible(model.getArmorHead(), slot == EquipmentSlot.HEAD);
        setBoneVisible(model.getArmorBody(), slot == EquipmentSlot.CHEST);
        setBoneVisible(model.getArmorRightArm(), slot == EquipmentSlot.CHEST);
        setBoneVisible(model.getArmorLeftArm(), slot == EquipmentSlot.CHEST);
        setBoneVisible(model.getArmorRightLeg(), slot == EquipmentSlot.LEGS);
        setBoneVisible(model.getArmorLeftLeg(), slot == EquipmentSlot.LEGS);
        setBoneVisible(model.getArmorRightBoot(), slot == EquipmentSlot.FEET);
        setBoneVisible(model.getArmorLeftBoot(), slot == EquipmentSlot.FEET);
    }

    public void setBoneVisible(BedrockBone bone, boolean visible) {
        if (bone != null) {
            bone.visible = visible;
        }
    }

    public void scaleModelForBaby(PoseStack poseStack, LivingEntity livingEntity, float partialTick, EquipmentSlot slot,
                                  HumanoidModel<?> original) {
        if (!this.young) {
            return;
        }

        if (slot == EquipmentSlot.HEAD) {
            if (original.scaleHead) {
                float headScale = 1.5f / original.babyHeadScale;
                poseStack.scale(headScale, headScale, headScale);
            }

            poseStack.translate(0, original.babyYHeadOffset / 16f, original.babyZHeadOffset / 16f);
        } else {
            float bodyScale = 1 / original.babyBodyScale;
            poseStack.scale(bodyScale, bodyScale, bodyScale);
            poseStack.translate(0, original.bodyYOffset / 16f, 0);
        }
    }

    /**
     * 非原版盔甲层直接调用时使用的后备渲染路径。
     */
    @Override
    public void renderToBuffer(PoseStack poseStack, @NotNull VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        float red = FastColor.ARGB32.red(color) / 255.0F;
        float green = FastColor.ARGB32.green(color) / 255.0F;
        float blue = FastColor.ARGB32.blue(color) / 255.0F;
        float alpha = FastColor.ARGB32.alpha(color) / 255.0F;
        renderArmorToBuffer(poseStack, Minecraft.getInstance().renderBuffers().bufferSource(), packedLight, packedOverlay, red, green, blue, alpha);
        afterRender(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void renderArmorToBuffer(PoseStack poseStack, MultiBufferSource bufferSource, int light, int overlay,
                                    float red, float green, float blue, float alpha) {
        VertexConsumer vertexConsumer = bufferSource.getBuffer(getRenderType(getTexture()));
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);

        poseStack.pushPose();
        if (this.livingEntity != null && this.equipmentSlot != null && this.original != null) {
            scaleModelForBaby(poseStack, this.livingEntity, partialTick, this.equipmentSlot, this.original);
        }
        model.renderToBuffer(poseStack, vertexConsumer, light, overlay, red, green, blue, alpha);
        poseStack.popPose();
    }

    public void afterRender(PoseStack poseStack, VertexConsumer buffer, int light, int overlay,
                            float red, float green, float blue, float alpha) {
        this.livingEntity = null;
        this.itemStack = null;
        this.equipmentSlot = null;
        this.original = null;
    }

    @Override
    public void renderFirstPersonArmorArm(@NotNull AbstractClientPlayer player, @NotNull HumanoidArm arm, @NotNull PoseStack poseStack,
                                          @NotNull MultiBufferSource bufferSource, int packedLight) {
        BedrockBone armBone = arm == HumanoidArm.RIGHT
                ? this.model.getArmorRightArm()
                : this.model.getArmorLeftArm();
        if (armBone == null) {
            return;
        }

        VertexConsumer consumer = bufferSource.getBuffer(getRenderType(getTexture()));

        poseStack.pushPose();
        poseStack.mulPose(getGlobalTransform(armBone));
        armBone.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private static Matrix4f getGlobalTransform(@NotNull BedrockBone targetBone) {
        Matrix4f matrix = new Matrix4f();
        for (BedrockBone bone = targetBone.parent; bone != null; bone = bone.parent) {
            matrix.scaleLocal(bone.xScale, bone.yScale, bone.zScale);
            matrix.rotateLocal(bone.rotation);
            matrix.translateLocal(bone.x / 16.0F, bone.y / 16.0F, bone.z / 16.0F);
        }
        return matrix;
    }

    public RenderType getRenderType(ResourceLocation texture) {
        return RenderType.armorCutoutNoCull(texture);
    }

    public ResourceLocation getTexture() {
        return this.texture;
    }

    @Nullable
    public EquipmentSlot getEquipmentSlot() {
        return this.equipmentSlot;
    }

    public BedrockArmorModel getModel() {
        return this.model;
    }
}
