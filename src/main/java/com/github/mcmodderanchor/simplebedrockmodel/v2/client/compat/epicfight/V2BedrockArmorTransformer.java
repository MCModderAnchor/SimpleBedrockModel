package com.github.mcmodderanchor.simplebedrockmodel.v2.client.compat.epicfight;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.epicfight.BedrockArmorTransformer;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.model.BedrockArmorModel;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.renderer.GeoArmorRendererV2;
import net.minecraft.client.model.HumanoidModel;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import yesman.epicfight.api.client.forgeevent.AnimatedArmorTextureEvent;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.client.model.transformer.HumanoidModelTransformer;

public class V2BedrockArmorTransformer extends HumanoidModelTransformer {
    public static void getBedrockArmorTexturePath(AnimatedArmorTextureEvent event) {
        IClientItemExtensions customRenderProperties = IClientItemExtensions.of(event.getItemstack());
        if (customRenderProperties == null) {
            return;
        }
        HumanoidModel<?> extensionRenderer = customRenderProperties.getHumanoidArmorModel(
                event.getLivingEntity(), event.getItemstack(), event.getEquipmentSlot(), event.getOriginalModel());
        if (extensionRenderer instanceof GeoArmorRendererV2 geoArmorRendererV2) {
            event.setResultLocation(geoArmorRendererV2.getTexture());
        }
    }

    @Override
    public SkinnedMesh transformArmorModel(HumanoidModel<?> humanoidModel) {
        if (!(humanoidModel instanceof GeoArmorRendererV2 renderer)) {
            return null;
        }

        BedrockArmorModel legacyArmor = renderer.getLegacyArmorModelForEpicFight();
        if (legacyArmor == null) {
            return null;
        }

        return BedrockArmorTransformer.transformArmorModel(legacyArmor, renderer.getArmorSlot());
    }
}
