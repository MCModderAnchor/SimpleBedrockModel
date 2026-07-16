package com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.armortrim.ArmorTrim;

/**
 * 允许盔甲模型获取当前 {@link MultiBufferSource} 进行渲染
 * <p>
 * 只在通过{@link HumanoidArmorLayer}进行渲染时有效，默认不处理trim和glint
 */
public interface ICustomArmorRenderer {

    void renderArmorToBuffer(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay,
                             float red, float green, float blue, float alpha);

    default void renderArmorTrimToBuffer(Holder<ArmorMaterial> armorMaterial, PoseStack poseStack, MultiBufferSource bufferSource,
                                         int packedLight, ArmorTrim trim, boolean innerTexture) {
    }

    default void renderArmorGlintToBuffer(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
    }

    default boolean shouldRenderVanillaTrim() {
        return false;
    }

    default boolean shouldRenderVanillaGlint() {
        return false;
    }
}
