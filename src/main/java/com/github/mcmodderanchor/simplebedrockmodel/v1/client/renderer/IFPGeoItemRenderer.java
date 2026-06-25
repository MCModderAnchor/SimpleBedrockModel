package com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.animation.IFPAnimationInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.firstperson.FirstPersonParticleSystem;
import com.maydaymemory.mae.basic.YXZRotationView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ViewportEvent;
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

    /**
     * 使用该渲染器的物品会阻止原版的 viewBobbing，以便应用自定义的跑步/走路动画。
     *
     * @return 是否阻止原版 viewBobbing
     */
    default boolean blockViewBobbing() {
        return true;
    }

    /**
     * 应用摄像机动画对世界的变换（只有旋转生效）。
     */
    default void applyLevelCameraAnimation(ViewportEvent.ComputeCameraAngles event, ItemStack stack, Quaternionf animateRot, float partialTicks) {
        Quaternionf initialRotation = new Quaternionf().rotateYXZ(-event.getYaw(), -event.getPitch(), -event.getRoll());
        YXZRotationView rotationView = new YXZRotationView(initialRotation.mul(animateRot));
        Vector3fc eulerAngle = rotationView.asEulerAngle();
        event.setYaw(-eulerAngle.y());
        event.setPitch(-eulerAngle.x());
        event.setRoll(-eulerAngle.z());
    }

    /**
     * 应用摄像机动画对手持物品的变换（只有旋转生效）。
     */
    default void applyItemInHandCameraAnimation(PoseStack poseStack, ItemStack stack, Quaternionf animateRot, float partialTicks) {
        poseStack.mulPose(animateRot);
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
