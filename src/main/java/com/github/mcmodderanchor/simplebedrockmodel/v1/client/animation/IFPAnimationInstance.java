package com.github.mcmodderanchor.simplebedrockmodel.v1.client.animation;

import com.maydaymemory.mae.basic.Pose;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

/**
 * Used for centralized management of first-person item rendering-related context.
 */
public interface IFPAnimationInstance {
    ItemStack currentItem();

    /**
     * Get the blended pose from all active animations.
     * @return the current pose
     */
    Pose getPose();

    /**
     * should be called every frame to update the animation state or other logic.
     * @param partialTicks the partial ticks
     */
    void tick(float partialTicks);

    /**
     * Get the camera rotation quaternion for first-person rendering.
     * @return the camera rotation
     */
    @NotNull
    Quaternionf getCameraRotation();

    /**
     * Set the camera rotation quaternion for first-person rendering.<br/>
     * You should call this method at a proper time every frame to update the camera rotation,
     * such as preparing to render the first-person item.
     *
     * @param cameraRotation the camera rotation
     */
    void setCameraRotation(@NotNull Quaternionf cameraRotation);

    /**
     * Get the cached pose for this frame. Should store the result of {@link #getPose()} firstly at the start of each frame.
     * @return the cached pose
     */
    Pose getCachedPose();

    void updateItem(ItemStack stack);

    void triggerDraw();

    void triggerPutAway();

    /**
     * 本实例（当前固定的渲染形态）是否霸占整个第一人称视野，从而禁止副手渲染。
     * <p>
     * 与 {@code IFPGeoItemRenderer.blockOffhandRender(ItemStack)} 的区别：此判定绑定到<b>具体实例</b>，
     * 其渲染形态在创建时即固定（如双手 / 单手变体），过渡期间稳定不变，故 SBM 用它判断主手是否霸占
     * 副手时，在掏枪 / 收枪过渡中不会因「实时手持物已变」而抖动。默认 {@code true}。
     *
     * @return 是否霸占视野禁止副手渲染
     */
    default boolean occupiesView() {
        return true;
    }

    default boolean shouldRenderHand() {
        return false;
    }
}