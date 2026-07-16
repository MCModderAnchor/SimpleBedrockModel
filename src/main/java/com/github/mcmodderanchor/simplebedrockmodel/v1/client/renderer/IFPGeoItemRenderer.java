package com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.animation.IFPAnimationInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.firstperson.FirstPersonParticleSystem;
import com.maydaymemory.mae.basic.YXZRotationView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3fc;

public interface IFPGeoItemRenderer {

    default boolean isSameItem(ItemStack oldStack, ItemStack newStack) {
        return ItemStack.isSameItem(oldStack, newStack);
    }

    @Nullable
    default IFPAnimationInstance createAnimationInstance(ItemStack stack, Entity entity) {
        return null;
    }

    @Nullable
    default IFPAnimationInstance createAnimationInstance(ItemStack stack, Entity entity, InteractionHand hand) {
        return createAnimationInstance(stack, entity);
    }

    default long getPutAwayDuration(ItemStack stack) {
        return 0;
    }

    /**
     * 当该物品被持于主手时，是否禁止副手的第一人称渲染。
     * 用于双手长枪等会霸占整个视野的物品：主手持有时副手不渲染。
     * 注意这是主手视角的判定，与副手物品自身无关。
     *
     * @return 持于主手时是否禁止副手渲染
     */
    default boolean blockOffhandRender() {
        return false;
    }

    default boolean blockOffhandRender(ItemStack stack) {
        return blockOffhandRender();
    }

    /**
     * 该物品当前是否允许在指定手进行第一人称渲染。
     * <p>
     * 用于「物品本身决定能否在某只手呈现」的场景：例如双手长枪放入副手时不应在副手渲染。
     * 与 {@link #blockOffhandRender(ItemStack)} 区分——后者是「主手物品霸占视野从而禁止副手」的
     * 主手视角判定；本方法是被渲染物品对自身所在手的判定。
     *
     * @param stack 待渲染物品
     * @param hand  渲染所在手
     * @return 允许渲染返回 {@code true}（主手默认），否则 {@code false}
     */
    default boolean canRenderInHand(ItemStack stack, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND;
    }

    /**
     * 渲染变体键：标识「同一物品」当前应使用哪一套渲染变体（模型骨架 / 动画 / 控制器）。
     * <p>
     * 适用于物品未变、但呈现形态需要切换的场景（例如同一把枪因双持 / 单持切换需在双手骨架与
     * 单手骨架变体之间切换）。SBM 每 tick 轮询此键，键变化时与「物品变化」走同一条
     * put_away → draw 通路重建渲染实例，由 SBM 统一管理生命周期；实现方<b>不应</b>自行在
     * 动画实例内部重建运行时，以免与 SBM 的实例切换时序冲突。
     * <p>
     * 默认返回 {@code null}（无变体，仅靠物品变化驱动切换）。返回值需可用 {@code equals} 比较，
     * 且对同一形态稳定。
     *
     * @param stack 当前物品
     * @param hand  所在手
     * @return 当前变体键；{@code null} 表示无变体
     */
    @Nullable
    default Object getRenderVariantKey(ItemStack stack, InteractionHand hand) {
        return null;
    }

    /**
     * 使用该渲染器的物品会阻止原版的 viewBobbing，以便应用自定义的跑步/走路动画。
     *
     * @return 是否阻止原版 viewBobbing
     */
    default boolean blockViewBobbing() {
        return true;
    }

    /**
     * 应用摄像机动画对世界的变换（只有旋转生效）。默认什么都不做。
     */
    default void applyLevelCameraAnimation(ViewportEvent.ComputeCameraAngles event, ItemStack stack, Quaternionf animateRot, float partialTicks) {
    }

    /**
     * 应用摄像机动画对手持物品的变换（只有旋转生效）。默认什么都不做。
     */
    default void applyItemInHandCameraAnimation(PoseStack poseStack, ItemStack stack, Quaternionf animateRot, float partialTicks) {
    }

    /**
     * 更新粒子发射器的变换矩阵。由 {@code FirstPersonRenderHandler} 在渲染物品前调用。
     * 具体渲染器应当覆写此方法，为其注册的发射器设置 emitterTransform 和 worldTransform。
     * <p>
     * 默认空实现（无粒子效果的物品无需实现）。
     *
     * @param system    全局粒子系统实例
     * @param poseStack 当前渲染使用的 PoseStack
     * @param hand      当前渲染的手（主手 / 副手），用于按手隔离发射器绑定状态
     */
    default void updateParticleEmitterTransforms(FirstPersonParticleSystem system, PoseStack poseStack, InteractionHand hand) {
        // 默认空实现，由有粒子效果的渲染器覆写
    }

    void renderFirstPerson(LocalPlayer player, ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource bufferSource,
                                  int light, float partialTick);
}
