package com.github.mcmodderanchor.simplebedrockmodel.v1.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

@OnlyIn(Dist.CLIENT)
public final class RenderHelper {
    public static void blit(PoseStack poseStack, float x, float y, float uOffset, float vOffset, float pWidth, float height, float textureWidth, float textureHeight) {
        blit(poseStack, x, y, pWidth, height, uOffset, vOffset, pWidth, height, textureWidth, textureHeight);
    }

    private static void blit(PoseStack poseStack, float x, float y, float pWidth, float height, float uOffset, float vOffset, float uWidth, float vHeight, float textureWidth, float textureHeight) {
        innerBlit(poseStack, x, x + pWidth, y, y + height, 0, uWidth, vHeight, uOffset, vOffset, textureWidth, textureHeight);
    }

    private static void innerBlit(PoseStack poseStack, float x1, float x2, float y1, float y2, float blitOffset, float uWidth, float vHeight, float uOffset, float vOffset, float textureWidth, float textureHeight) {
        innerBlit(poseStack.last().pose(), x1, x2, y1, y2, blitOffset, (uOffset + 0.0F) / textureWidth, (uOffset + uWidth) / textureWidth, (vOffset + 0.0F) / textureHeight, (vOffset + vHeight) / textureHeight);
    }

    private static void innerBlit(Matrix4f matrix, float x1, float x2, float y1, float y2, float blitOffset, float minU, float maxU, float minV, float maxV) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        BufferBuilder bufferbuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.addVertex(matrix, x1, y2, blitOffset).setUv(minU, maxV);
        bufferbuilder.addVertex(matrix, x2, y2, blitOffset).setUv(maxU, maxV);
        bufferbuilder.addVertex(matrix, x2, y1, blitOffset).setUv(maxU, minV);
        bufferbuilder.addVertex(matrix, x1, y1, blitOffset).setUv(minU, minV);
        BufferUploader.draw(bufferbuilder.buildOrThrow());
    }

//    public static void enableItemEntityStencilTest() {
//        RenderSystem.assertOnRenderThread();
//        if (OptifineCompat.isOptifineInstalled()) {
//            // 以下代码用于应对 使用 optifine 的场景
//            int depthTextureId = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
//            int stencilTextureId = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
//            if (depthTextureId != GL30.GL_NONE && stencilTextureId == GL30.GL_NONE) {
//                GL30.glBindTexture(GL30.GL_TEXTURE_2D, depthTextureId);
//                int dataType = GL30.glGetTexLevelParameteri(GL30.GL_TEXTURE_2D, 0, GL30.GL_TEXTURE_DEPTH_TYPE);
//                if (dataType == GL30.GL_UNSIGNED_NORMALIZED) {
//                    int width = GL30.glGetTexLevelParameteri(GL30.GL_TEXTURE_2D, 0, GL30.GL_TEXTURE_WIDTH);
//                    int height = GL30.glGetTexLevelParameteri(GL30.GL_TEXTURE_2D, 0, GL30.GL_TEXTURE_HEIGHT);
//                    GlStateManager._texImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_DEPTH24_STENCIL8, width, height, 0, GL30.GL_DEPTH_STENCIL, GL30.GL_UNSIGNED_INT_24_8, null);
//                    GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, 3553, depthTextureId, 0);
//                }
//            }
//        } else {
//            Minecraft.getInstance().getMainRenderTarget().enableStencil();
//        }
//        GL11.glEnable(GL11.GL_STENCIL_TEST);
//    }

    public static void disableItemEntityStencilTest() {
        RenderSystem.assertOnRenderThread();
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }

    /**
     * @deprecated 第一人称渲染时，优先使用传入hand buffer的版本{@link #renderFirstPersonArm(LocalPlayer, MultiBufferSource, HumanoidArm, PoseStack, int)}，否则可能有兼容性问题
     */
    @Deprecated
    public static void renderFirstPersonArm(LocalPlayer player, HumanoidArm hand, PoseStack matrixStack, int combinedLight) {
        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher renderManager = mc.getEntityRenderDispatcher();
        PlayerRenderer renderer = (PlayerRenderer) renderManager.getRenderer(player);
        MultiBufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();

        if (hand == HumanoidArm.RIGHT) {
            renderer.renderRightHand(matrixStack, buffer, combinedLight, player);
        } else {
            renderer.renderLeftHand(matrixStack, buffer, combinedLight, player);
        }
    }

    public static void renderFirstPersonArm(LocalPlayer player, MultiBufferSource bufferSource, HumanoidArm hand, PoseStack matrixStack, int combinedLight) {
        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher renderManager = mc.getEntityRenderDispatcher();
        PlayerRenderer renderer = (PlayerRenderer) renderManager.getRenderer(player);

        if (hand == HumanoidArm.RIGHT) {
            renderer.renderRightHand(matrixStack, bufferSource, combinedLight, player);
        } else {
            renderer.renderLeftHand(matrixStack, bufferSource, combinedLight, player);
        }
    }

    /**
     * 将第一人称模型空间中的局部坐标转换为 Minecraft 绝对世界坐标。
     * <p>
     * 典型用法：传入骨骼的 globalTransform 变换后的坐标（或直接传 locator 坐标），
     * 得到该点在 Minecraft 世界中的绝对位置。
     *
     * @param localX         模型空间中的 X 坐标
     * @param localY         模型空间中的 Y 坐标
     * @param localZ         模型空间中的 Z 坐标
     * @param modelTransform 第一人称模型变换矩阵（antibob × gunOffset），即 identity → 视图空间的变换
     * @param poseInitial    RenderHandEvent 的 poseStack 初始 pose
     * @param camera         当前摄像机
     * @return 绝对世界坐标
     */
    public static Vec3 firstPersonToWorld(float localX, float localY, float localZ,
                                          Matrix4f modelTransform, Matrix4f poseInitial,
                                          Camera camera) {
        // modelTransform × localPos → 视图空间
        // cameraRotInv × poseInitial × 视图空间坐标 → 以摄像机为原点的世界对齐坐标
        // + camera.getPosition() → 绝对世界坐标
        Matrix4f cameraRotInv = new Matrix4f(buildCameraRotation(camera)).invert();
        Matrix4f toWorldAligned = new Matrix4f(cameraRotInv).mul(poseInitial).mul(modelTransform);

        float wx = toWorldAligned.m00() * localX + toWorldAligned.m10() * localY + toWorldAligned.m20() * localZ + toWorldAligned.m30();
        float wy = toWorldAligned.m01() * localX + toWorldAligned.m11() * localY + toWorldAligned.m21() * localZ + toWorldAligned.m31();
        float wz = toWorldAligned.m02() * localX + toWorldAligned.m12() * localY + toWorldAligned.m22() * localZ + toWorldAligned.m32();

        Vec3 camPos = camera.getPosition();
        return new Vec3(wx + camPos.x, wy + camPos.y, wz + camPos.z);
    }

    /**
     * 用摄像机的 pitch/yaw 构建视图旋转矩阵（与 Minecraft 内部一致）。
     * Minecraft 的视图矩阵构建顺序：先绕 X 旋转 pitch，再绕 Y 旋转 (yaw + 180)。
     */
    public static Matrix4f buildCameraRotation(Camera camera) {
        return new Matrix4f()
                .rotationX((float) Math.toRadians(camera.getXRot()))
                .rotateY((float) Math.toRadians(camera.getYRot() + 180f));
    }
}
