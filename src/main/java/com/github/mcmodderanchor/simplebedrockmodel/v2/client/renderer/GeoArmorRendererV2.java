package com.github.mcmodderanchor.simplebedrockmodel.v2.client.renderer;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.model.BedrockArmorModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.BedrockModelRenderTypes;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BedrockArmorInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GeoArmorRendererV2 extends HumanoidModel<LivingEntity> {
    protected final BakedBedrockModel model;
    protected final BedrockArmorInstance instance;
    private final EquipmentSlot armorSlot;
    @Nullable
    private final BedrockArmorModel legacyArmorModelForEpicFight;
    private final ResourceLocation texture;

    @Nullable
    protected LivingEntity livingEntity;
    @Nullable
    protected ItemStack itemStack;
    @Nullable
    protected EquipmentSlot equipmentSlot;
    @Nullable
    protected HumanoidModel<?> original;

    public GeoArmorRendererV2(BakedBedrockModel model, EquipmentSlot armorSlot, ResourceLocation texture) {
        this(model, new BedrockArmorInstance(model), armorSlot, null, texture);
    }

    public GeoArmorRendererV2(BakedBedrockModel model, EquipmentSlot armorSlot,
                              BedrockArmorModel legacyArmorModelForEpicFight, ResourceLocation texture) {
        this(model, new BedrockArmorInstance(model), armorSlot, legacyArmorModelForEpicFight, texture);
    }

    public GeoArmorRendererV2(BakedBedrockModel model, BedrockArmorInstance instance, EquipmentSlot armorSlot, ResourceLocation texture) {
        this(model, instance, armorSlot, null, texture);
    }

    public GeoArmorRendererV2(BakedBedrockModel model, BedrockArmorInstance instance, EquipmentSlot armorSlot,
                              @Nullable BedrockArmorModel legacyArmorModelForEpicFight, ResourceLocation texture) {
        super(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        this.armorSlot = armorSlot;
        this.legacyArmorModelForEpicFight = legacyArmorModelForEpicFight;
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
    public void renderToBuffer(PoseStack poseStack, @NotNull VertexConsumer buffer, int light, int overlay,
                               float r, float g, float b, float a) {
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource bufferSource = mc.renderBuffers().bufferSource();

        float partialTick = mc.getFrameTime();

        poseStack.pushPose();
        if (this.livingEntity != null && this.equipmentSlot != null && this.original != null) {
            scaleModelForBaby(poseStack, this.livingEntity, partialTick, this.equipmentSlot, this.original);
        }

        this.instance.renderToBuffer(poseStack, bufferSource, getRenderType(this.texture), BedrockModelRenderTypes.polyMeshCutout(this.texture), light, overlay, r, g, b, a);
        poseStack.popPose();

        afterRender(poseStack, buffer, light, overlay, r, g, b, a);
    }

    public void afterRender(PoseStack poseStack, VertexConsumer buffer, int light, int overlay,
                            float r, float g, float b, float a) {
        this.livingEntity = null;
        this.itemStack = null;
        this.equipmentSlot = null;
        this.original = null;
    }

    public RenderType getRenderType(ResourceLocation texture) {
        return RenderType.armorCutoutNoCull(texture);
    }

    public ResourceLocation getTexture() {
        return this.texture;
    }

    @Nullable
    public BedrockArmorModel getLegacyArmorModelForEpicFight() {
        return this.legacyArmorModelForEpicFight;
    }

    public BakedBedrockModel getModel() {
        return this.model;
    }

    public BedrockArmorInstance getInstance() {
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
