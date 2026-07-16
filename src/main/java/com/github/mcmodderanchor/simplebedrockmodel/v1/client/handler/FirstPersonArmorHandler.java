package com.github.mcmodderanchor.simplebedrockmodel.v1.client.handler;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.IFPArmorHandRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * 通用的第一人称盔甲手臂渲染处理器。
 * 监听 RenderArmEvent，在玩家手臂上叠加渲染 Bedrock 盔甲模型的手臂部分。
 */
@EventBusSubscriber(Dist.CLIENT)
public class FirstPersonArmorHandler {

    private static HumanoidModel<?> defaultModel;

    private static HumanoidModel<?> getDefaultModel() {
        if (defaultModel == null) {
            defaultModel = new HumanoidModel<>(
                    Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)
            );
        }
        return defaultModel;
    }

    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        AbstractClientPlayer player = event.getPlayer();
        HumanoidArm arm = event.getArm();

        ItemStack chestStack = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chestStack.isEmpty()) return;

        IClientItemExtensions ext = IClientItemExtensions.of(chestStack.getItem());
        var model = ext.getHumanoidArmorModel(player, chestStack, EquipmentSlot.CHEST, getDefaultModel());
        if (!(model instanceof IFPArmorHandRenderer armorRenderer)) return;

        armorRenderer.renderFirstPersonArmorArm(
                player,
                arm,
                event.getPoseStack(),
                event.getMultiBufferSource(),
                event.getPackedLight()
        );
    }
}
