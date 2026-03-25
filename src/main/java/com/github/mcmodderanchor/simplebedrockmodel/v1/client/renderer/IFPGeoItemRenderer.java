package com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.animation.IFPAnimationInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.firstperson.FirstPersonParticleSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface IFPGeoItemRenderer {

    default boolean isSameItem(ItemStack oldStack, ItemStack newStack) {
        return ItemStack.isSameItem(oldStack, newStack);
    }

    @Nullable
    default IFPAnimationInstance createAnimationInstance(ItemStack stack, Entity entity) {
        return null;
    }

    default long getPutAwayDuration(ItemStack stack) {
        return 0;
    }

    default boolean blockOffhandRender() {
        return false;
    }

    /**
     * 更新粒子发射器的变换矩阵。由 {@code FirstPersonRenderHandler} 在渲染物品前调用。
     * 具体渲染器应当覆写此方法，为其注册的发射器设置 emitterTransform 和 worldTransform。
     * <p>
     * 默认空实现（无粒子效果的物品无需实现）。
     *
     * @param system    全局粒子系统实例
     * @param poseStack 当前渲染使用的 PoseStack
     */
    default void updateParticleEmitterTransforms(FirstPersonParticleSystem system, PoseStack poseStack) {
        // 默认空实现，由有粒子效果的渲染器覆写
    }

    void renderFirstPerson(LocalPlayer player, ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource bufferSource,
                                  int light, float partialTick);
}
