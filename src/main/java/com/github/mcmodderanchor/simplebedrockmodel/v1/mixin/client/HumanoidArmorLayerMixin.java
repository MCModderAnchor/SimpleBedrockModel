package com.github.mcmodderanchor.simplebedrockmodel.v1.mixin.client;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.ICustomArmorRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.armortrim.ArmorTrim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public class HumanoidArmorLayerMixin {

    @Inject(
            method = "renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/model/Model;ILnet/minecraft/resources/ResourceLocation;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sbm$renderMultiBufferArmor(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                            Model model, int color, ResourceLocation armorResource,
                                            CallbackInfo ci) {
        if (model instanceof ICustomArmorRenderer renderer) {
            renderer.renderArmorToBuffer(
                    poseStack,
                    bufferSource,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    FastColor.ARGB32.red(color) / 255.0F,
                    FastColor.ARGB32.green(color) / 255.0F,
                    FastColor.ARGB32.blue(color) / 255.0F,
                    FastColor.ARGB32.alpha(color) / 255.0F
            );
            ci.cancel();
        }
    }

    @Inject(
            method = "renderTrim(Lnet/minecraft/core/Holder;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/armortrim/ArmorTrim;Lnet/minecraft/client/model/Model;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sbm$skipVanillaTrim(Holder<ArmorMaterial> armorMaterial, PoseStack poseStack, MultiBufferSource bufferSource,
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
            cancellable = true
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
