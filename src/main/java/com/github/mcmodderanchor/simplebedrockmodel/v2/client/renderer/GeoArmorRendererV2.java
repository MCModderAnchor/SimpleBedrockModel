package com.github.mcmodderanchor.simplebedrockmodel.v2.client.renderer;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.BedrockModelRenderTypes;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.ICustomArmorRenderer;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.IFPArmorHandRenderer;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BoneState;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.TreeArmorModelInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.TreeBedrockModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
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

/**
 * 基于 Tree 模型的实例盔甲渲染器。
 */
public class GeoArmorRendererV2 extends HumanoidModel<LivingEntity> implements IFPArmorHandRenderer, ICustomArmorRenderer {
    protected final TreeBedrockModel model;
    protected final TreeArmorModelInstance instance;
    private final EquipmentSlot armorSlot;
    private final ResourceLocation texture;

    @Nullable
    protected LivingEntity livingEntity;
    @Nullable
    protected ItemStack itemStack;
    @Nullable
    protected EquipmentSlot equipmentSlot;
    @Nullable
    protected HumanoidModel<?> original;

    public GeoArmorRendererV2(TreeBedrockModel model, EquipmentSlot armorSlot, ResourceLocation texture) {
        this(model, new TreeArmorModelInstance(model), armorSlot, texture);
    }

    public GeoArmorRendererV2(TreeBedrockModel model, TreeArmorModelInstance instance, EquipmentSlot armorSlot, ResourceLocation texture) {
        super(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        this.armorSlot = armorSlot;
        this.model = model;
        this.instance = instance;
        this.texture = texture;
    }

    public void preparePose(LivingEntity livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, HumanoidModel<?> original) {
        this.instance.preparePose(livingEntity, itemStack, equipmentSlot, original);
        this.livingEntity = livingEntity;
        this.itemStack = itemStack;
        this.equipmentSlot = equipmentSlot;
        this.original = original;
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

    @Override
    public void renderToBuffer(PoseStack poseStack, @NotNull VertexConsumer buffer, int light, int overlay, int color) {
        float red = FastColor.ARGB32.red(color) / 255.0F;
        float green = FastColor.ARGB32.green(color) / 255.0F;
        float blue = FastColor.ARGB32.blue(color) / 255.0F;
        float alpha = FastColor.ARGB32.alpha(color) / 255.0F;
        renderArmorToBuffer(poseStack, Minecraft.getInstance().renderBuffers().bufferSource(), light, overlay, red, green, blue, alpha);
        afterRender(poseStack, buffer, light, overlay, red, green, blue, alpha);
    }

    @Override
    public void renderArmorToBuffer(PoseStack poseStack, MultiBufferSource bufferSource, int light, int overlay,
                                    float red, float green, float blue, float alpha) {
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);

        poseStack.pushPose();
        if (this.livingEntity != null && this.equipmentSlot != null && this.original != null) {
            scaleModelForBaby(poseStack, this.livingEntity, partialTick, this.equipmentSlot, this.original);
        }
        this.instance.renderToBuffer(
                poseStack,
                bufferSource,
                getRenderType(this.texture),
                BedrockModelRenderTypes.polyMeshCutout(this.texture),
                light,
                overlay,
                red,
                green,
                blue,
                alpha
        );
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
        BoneState armBone = arm == HumanoidArm.RIGHT
                ? this.instance.getArmorRightArm()
                : this.instance.getArmorLeftArm();
        if (armBone == null) {
            return;
        }

        this.instance.renderSingleBone(poseStack, armBone.index(), bufferSource, getRenderType(getTexture()),
                BedrockModelRenderTypes.polyMeshCutout(getTexture()), packedLight, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, 1.0F, true);
    }

    public RenderType getRenderType(ResourceLocation texture) {
        return RenderType.armorCutoutNoCull(texture);
    }

    public ResourceLocation getTexture() {
        return this.texture;
    }

    public TreeBedrockModel getModel() {
        return this.model;
    }

    public TreeArmorModelInstance getInstance() {
        return this.instance;
    }

    public EquipmentSlot getArmorSlot() {
        return this.armorSlot;
    }

    @Nullable
    public EquipmentSlot getCurrentSlot() {
        return this.equipmentSlot;
    }
}
