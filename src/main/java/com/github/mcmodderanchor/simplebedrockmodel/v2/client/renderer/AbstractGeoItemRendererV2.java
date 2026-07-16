package com.github.mcmodderanchor.simplebedrockmodel.v2.client.renderer;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.model.SlotModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.IFPGeoItemRenderer;
import com.github.mcmodderanchor.simplebedrockmodel.v1.util.RenderDistance;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 第一人称 v2 基岩模型的抽象 BEWLR。
 * <p>
 * 与 v1 的 {@code AbstractGeoItemRenderer} 不同，本基类不绑定 v1 {@code BedrockModel}，
 * 实际几何渲染交由子类通过 {@link #renderModel} 钩子，使用 v2 的
 * {@code TreeModelInstance} / {@code BakedModelInstance} 完成。
 * <p>
 * 相机动画、晃动、定位组、LOD 等通用逻辑沿用 {@link IFPGeoItemRenderer} 的默认实现，
 * 与 v1 路径行为保持一致。
 */
public abstract class AbstractGeoItemRendererV2 extends BlockEntityWithoutLevelRenderer implements IFPGeoItemRenderer {
    public static final String FP_CAMERA_BONE_NAME = "camera";
    private static final SlotModel SLOT_MODEL = new SlotModel();

    public AbstractGeoItemRendererV2() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    /**
     * 物品栏 / GUI 中显示的 2D 纹理。返回 {@code null} 时回退到 {@link MissingTextureAtlasSprite}。
     */
    @Nullable
    public abstract ResourceLocation getSlotTexture(ItemStack stack);

    /**
     * 判断当前物品是否有可渲染的 3D 模型。返回 {@code false} 时走 GUI slot 渲染路径。
     */
    public abstract boolean hasModel(ItemStack stack);

    /**
     * 实际渲染 3D 模型。子类在此应用动画 pose 并调用 v2 instance 的 renderToBuffer。
     * 仅在确认 {@link #hasModel} 为 true 且非 GUI 上下文时调用。
     */
    protected abstract void renderModel(PoseStack poseStack, ItemDisplayContext ctx, ItemStack stack,
                                        MultiBufferSource bufferSource, int light, int overlay, float partialTicks);

    /**
     * 带法向可见性剔除控制的模型渲染钩子。子类可覆写该方法，并将参数传给 v2 instance 的 renderToBuffer。
     */
    protected void renderModel(PoseStack poseStack, ItemDisplayContext ctx, ItemStack stack,
                               MultiBufferSource bufferSource, int light, int overlay, float partialTicks,
                               boolean skipNormalVisibilityCull) {
        renderModel(poseStack, ctx, stack, bufferSource, light, overlay, partialTicks);
    }

    /**
     * 渲染模型前调用。默认应用 GROUND / 非第一人称的中心偏移。可用于施加动画影响。
     */
    protected void beforeRender(PoseStack poseStack, ItemDisplayContext ctx, ItemStack stack, float partialTicks) {
        if (ctx == ItemDisplayContext.GROUND) {
            poseStack.translate(0.5, 0.3125, 0.5);
        } else if (!ctx.firstPerson()) {
            poseStack.translate(0.5, 0.5, 0.5);
        }
    }

    /**
     * 渲染模型后调用。默认什么都不做。
     */
    protected void afterRender(PoseStack poseStack, ItemDisplayContext ctx, ItemStack stack, MultiBufferSource bufferSource,
                               int light, float partialTicks) {
    }

    /**
     * 是否跳过基于法向的简单可见性剔除。第一人称模型靠近相机时容易处在判断临界值附近，默认跳过以避免闪烁。
     */
    protected boolean skipNormalVisibilityCull(ItemDisplayContext ctx, ItemStack stack) {
        return ctx.firstPerson();
    }

    @Override
    public void renderFirstPerson(LocalPlayer player, ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource bufferSource,
                                  int light, float partialTick) {
        // 默认的左右手位移
        int i = ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND ? 1 : -1;
        poseStack.translate((float) i * 0.5F, -0.75F, -0.75F);
        render(stack, ctx, poseStack, bufferSource, light, OverlayTexture.NO_OVERLAY, partialTick);
    }

    @ParametersAreNonnullByDefault
    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource bufferSource,
                             int light, int overlay) {
        if (ctx.firstPerson()) {
            return;
        }
        render(stack, ctx, poseStack, bufferSource, light, overlay, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
    }

    protected void render(ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource bufferSource,
                          int light, int overlay, float partialTicks) {
        boolean hasModel = hasModel(stack);
        if (ctx == ItemDisplayContext.GUI || !hasModel) {
            renderSlot(stack, poseStack, bufferSource, light, overlay, hasModel);
            return;
        }
        poseStack.pushPose();
        beforeRender(poseStack, ctx, stack, partialTicks);
        renderModel(poseStack, ctx, stack, bufferSource, light, overlay, partialTicks, skipNormalVisibilityCull(ctx, stack));
        afterRender(poseStack, ctx, stack, bufferSource, light, partialTicks);
        poseStack.popPose();
    }

    public void renderSlot(ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource, int light, int overlay, boolean hasModel) {
        ResourceLocation slotTexture = getSlotTexture(stack);
        if (slotTexture != null) {
            poseStack.pushPose();
            poseStack.translate(0.5, 0.5, 0);
            SLOT_MODEL.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.entityTranslucent(slotTexture)), light, overlay, 0xFFFFFFFF);
            poseStack.popPose();
        } else if (!hasModel) {
            // 模型和 gui texture 都不存在，渲染 missing texture
            poseStack.pushPose();
            poseStack.translate(0.5, 0.5, 0);
            RenderType renderType = RenderType.entityTranslucent(MissingTextureAtlasSprite.getLocation());
            SLOT_MODEL.renderToBuffer(poseStack, bufferSource.getBuffer(renderType), light, overlay, 0xFFFFFFFF);
            poseStack.popPose();
        }
    }

    /**
     * 是否在高模渲染距离内（供子类做 LOD 判断）。
     */
    protected boolean inRenderHighPolyModelDistance(PoseStack poseStack, double distance) {
        return RenderDistance.inRenderHighPolyModelDistance(poseStack, distance);
    }
}
