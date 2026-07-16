package com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.handler.FirstPersonArmorHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import org.jetbrains.annotations.NotNull;

/**
 * 第一人称盔甲手臂渲染接口。
 * <p>
 * 盔甲渲染器实现可通过实现该接口接入 {@link FirstPersonArmorHandler}，
 * 在第一人称渲染盔甲的手臂部分模型
 */
public interface IFPArmorHandRenderer {

    /**
     * 在第一人称手臂上渲染指定侧的盔甲手臂部分。
     *
     * @param player 当前正在渲染的玩家
     * @param arm 当前正在渲染的玩家手臂
     * @param poseStack 当前渲染矩阵栈
     * @param bufferSource 当前bufferSource
     * @param packedLight 光照值
     */
    void renderFirstPersonArmorArm(@NotNull AbstractClientPlayer player, @NotNull HumanoidArm arm, @NotNull PoseStack poseStack,
                                   @NotNull MultiBufferSource bufferSource, int packedLight);
}
