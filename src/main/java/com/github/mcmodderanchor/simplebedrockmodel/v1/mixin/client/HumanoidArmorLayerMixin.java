package com.github.mcmodderanchor.simplebedrockmodel.v1.mixin.client;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.ICustomArmorRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public class HumanoidArmorLayerMixin {

    @Inject(
            method = "renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ArmorItem;Lnet/minecraft/client/model/Model;ZFFFLnet/minecraft/resources/ResourceLocation;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void sbm$renderMultiBufferArmor(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                            ArmorItem armorItem, Model model, boolean withGlint,
                                            float red, float green, float blue, ResourceLocation armorResource,
                                            CallbackInfo ci) {
        if (model instanceof ICustomArmorRenderer renderer) {
            renderer.renderArmorToBuffer(poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY, red, green, blue, 1.0F);
            ci.cancel();
        }
    }

    @Inject(
            method = "renderTrim(Lnet/minecraft/world/item/ArmorMaterial;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/armortrim/ArmorTrim;Lnet/minecraft/client/model/Model;Z)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void sbm$skipVanillaTrim(ArmorMaterial armorMaterial, PoseStack poseStack, MultiBufferSource bufferSource,
                                     int packedLight, ArmorTrim trim, Model model, boolean innerTexture,
                                     CallbackInfo ci) {
        if (model instanceof ICustomArmorRenderer renderer) {
            renderer.renderArmorTrimToBuffer(armorMaterial, poseStack, bufferSource, packedLight, trim, innerTexture);
            if (!renderer.shouldRenderVanillaTrim()) {
                ci.cancel();
            }
        }
    }

    @Inject(
            method = "renderGlint(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/model/Model;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void sbm$skipVanillaGlint(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                      Model model, CallbackInfo ci) {
        if (model instanceof ICustomArmorRenderer renderer) {
            renderer.renderArmorGlintToBuffer(poseStack, bufferSource, packedLight);
            if (!renderer.shouldRenderVanillaGlint()) {
                ci.cancel();
            }
        }
    }
}
