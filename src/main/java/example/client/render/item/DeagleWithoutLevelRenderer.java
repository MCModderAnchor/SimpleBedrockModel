package example.client.render.item;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.animation.IFPAnimationInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.handler.FirstPersonRenderHandler;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.AbstractGeoItemRenderer;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockBone;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.ParticleEffectData;
import com.github.mcmodderanchor.simplebedrockmodel.v1.event.RegisterBedrockModelReloadListenerEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleEffectDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.firstperson.FirstPersonParticleSystem;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.render.CameraStateCache;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.resource.ParticleDefinitionLoader;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import example.animation.DeagleAnimationGraph;
import example.animation.GunAnimationGraph;
import example.capability.FPGunAnimationCapability;
import example.resource.KnownResources;
import com.maydaymemory.mae.basic.Pose;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeagleWithoutLevelRenderer extends AbstractGeoItemRenderer<BedrockModel> {
    private static final Material MATERIAL = new Material(TextureAtlas.LOCATION_BLOCKS, KnownResources.DEAGLE.withPrefix("item/"));
    private static final Map<ParticleEmitterInstance, String> EMITTER_LOCATOR_MAP = new HashMap<>();

    private static BedrockModel model;

    // 暂时只能想到这么丑的办法
    @EventBusSubscriber(value = Dist.CLIENT)
    public static class ModelReloadListenerRegister {
        @SubscribeEvent
        public static void onModelReloadListenerRegister(RegisterBedrockModelReloadListenerEvent event) {
            event.register(map -> {
                model = map.get(KnownResources.DEAGLE);
                BedrockBone leftHandBone = model.getBone("lefthand_pos");
                BedrockBone rightHandBone = model.getBone("righthand_pos");
                if (leftHandBone != null) {
                    leftHandBone.visible = false;
                }
                if (rightHandBone != null) {
                    rightHandBone.visible = false;
                }
                FirstPersonRenderHandler.getParticleSystem().clear();
                EMITTER_LOCATOR_MAP.clear();
            });
        }
    }

    public DeagleWithoutLevelRenderer() {
    }

    @Override
    public void applyLevelCameraAnimation(ViewportEvent.ComputeCameraAngles event, ItemStack stack, Quaternionf animateRot, float partialTicks) {

    }


    @Override
    @Nullable
    public Pair<BedrockModel, RenderType> getModelAndRenderType(ItemStack stack) {
        if (model == null) return null;
        return Pair.of(model, RenderType.entityCutout(KnownResources.DEAGLE.withPrefix("item/")));
    }

    @Override
    @Nullable
    public Pair<BedrockModel, RenderType> getLodModelAndRenderType(ItemStack stack) {
        return null;
    }

    @Override
    @Nullable
    public ResourceLocation getSlotTexture(ItemStack stack) {
        return KnownResources.DEAGLE.withPrefix("item/");
    }


    @Override
    @Nullable
    public IFPAnimationInstance createAnimationInstance(ItemStack stack, Entity entity) {
        if (!(entity instanceof Player)) return null;
        return new DeagleAnimationInstance(stack);
    }

    private static class DeagleAnimationInstance implements IFPAnimationInstance {
        private ItemStack currentStack;
        private final Quaternionf cameraRotation = new Quaternionf();
        private Pose cachedPose;

        DeagleAnimationInstance(ItemStack stack) {
            this.currentStack = stack;
        }

        @Override
        public ItemStack currentItem() {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                ItemStack held = player.getMainHandItem();
                if (!held.isEmpty()) return held;
            }
            return currentStack;
        }

        @Override
        public Pose getPose() {
            if (cachedPose == null) {
                cachedPose = computePose();
            }
            return cachedPose;
        }

        private Pose computePose() {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                var cap = FPGunAnimationCapability.get(player);
                GunAnimationGraph graph = cap.getAnimationInstance().getAnimationGraph();
                if (graph != null) {
                    return graph.getPose();
                }
            }
            return model.getBindPose();
        }

        @Override
        public void tick(float partialTicks) {
            // ClientTicker 已统一处理动画 tick
        }

        @Override
        public Quaternionf getCameraRotation() {
            return cameraRotation;
        }

        @Override
        public void setCameraRotation(Quaternionf cameraRotation) {
            this.cameraRotation.set(cameraRotation);
        }

        @Override
        public Pose getCachedPose() {
            return cachedPose;
        }

        @Override
        public void updateItem(ItemStack stack) {
            this.currentStack = stack;
        }

        @Override
        public void triggerDraw() {
            // ClientTicker.onPlayerChangeSelect 已处理 notifyDraw
        }

        @Override
        public void triggerPutAway() {
            // Deagle 暂无收枪动画
        }
    }

    // ==================== IFPGeoItemRenderer: 粒子发射器变换更新 ====================

    /**
     * 用摄像机的 pitch/yaw/roll 构建视图旋转矩阵（与 Minecraft 内部一致）。
     */
    private static Matrix4f buildCameraRotation(Camera camera, float rollRadians) {
        return new Matrix4f()
                .rotationX((float) Math.toRadians(camera.getXRot()))
                .rotateY((float) Math.toRadians(camera.getYRot() + 180f))
                .rotateZ(rollRadians);
    }

    @Override
    public void updateParticleEmitterTransforms(FirstPersonParticleSystem system, PoseStack poseStack, InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        DeagleAnimationGraph deagleGraph = null;

        // 从 AnimationInstance 中获取 AnimationGraph，apply 当前帧 Pose
        if (mc.getCameraEntity() instanceof Player player) {
            var capability = FPGunAnimationCapability.get(player);
            GunAnimationGraph animationGraph = capability.getAnimationInstance().getAnimationGraph();
            if (animationGraph != null) {
                model.applyPose(animationGraph.getPose());
            }
            if (animationGraph instanceof DeagleAnimationGraph dag) {
                deagleGraph = dag;
            }
        }

        // 消费待发射的粒子关键帧，创建发射器
        List<ParticleEmitterInstance> newEmitters = new ArrayList<>();
        if (deagleGraph != null) {
            List<ParticleEffectData> pendingParticles = deagleGraph.consumePendingParticles();
            for (ParticleEffectData data : pendingParticles) {
                ParticleEffectDefinition def = ParticleDefinitionLoader.getInstance().getDefinition(data.effect());
                if (def != null) {
                    ParticleEmitterInstance emitter = system.addEmitter(def, hand);
                    String locator = data.locator();
                    if (locator != null && !locator.isEmpty()) {
                        EMITTER_LOCATOR_MAP.put(emitter, locator);
                    }
                    newEmitters.add(emitter);
                }
            }
        }

        // 构建模型空间到世界对齐空间的变换矩阵（包含 Bob + 手持位移）
        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(true);
        Matrix4f modelTransform = new Matrix4f();
        if (mc.options.bobView().get() && mc.getCameraEntity() instanceof Player player2) {
            float f = player2.walkDist - player2.walkDistO;
            float f1 = -(player2.walkDist + f * partialTick);
            float f2 = Mth.lerp(partialTick, player2.oBob, player2.bob);
            modelTransform.rotate(Axis.XN.rotationDegrees(Math.abs(Mth.cos(f1 * (float) Math.PI - 0.2F) * f2) * 5.0F));
            modelTransform.rotate(Axis.ZN.rotationDegrees(Mth.sin(f1 * (float) Math.PI) * f2 * 3.0F));
            modelTransform.translate(-Mth.sin(f1 * (float) Math.PI) * f2 * 0.5F, (float) Math.abs(Mth.cos(f1 * (float) Math.PI) * f2), 0.0F);
        }
        modelTransform.translate(0.125f, -0.5f, -1.03125f);

        Camera camera = mc.gameRenderer.getMainCamera();
        float cameraRollRad = CameraStateCache.getCameraRollRadians();
        Matrix4f cameraRotation = buildCameraRotation(camera, cameraRollRad);
        Matrix4f cameraRotationInv = new Matrix4f(cameraRotation).invert();
        Matrix4f poseInitial = new Matrix4f(poseStack.last().pose());
        Matrix4f toWorldAligned = new Matrix4f(cameraRotationInv).mul(poseInitial).mul(modelTransform);

        // 为所有关联了 locator 的发射器设置变换矩阵
        for (ParticleEmitterInstance emitter : system.getEmitters()) {
            String locatorName = EMITTER_LOCATOR_MAP.get(emitter);
            Matrix4f transform = null;
            if (locatorName != null) {
                transform = model.getLocatorTransform(locatorName);
            }
            if (transform == null) {
                BedrockBone bone = locatorName != null ? model.getBone(locatorName) : null;
                if (bone != null) {
                    transform = bone.getGlobalTransform();
                }
            }
            if (transform != null) {
                Matrix4f worldTransform = new Matrix4f(toWorldAligned).mul(transform);
                // emitterTransform 使用 modelTransform * locatorTransform（仅模型空间），
                // 不含相机矩阵。rendering 时 BillboardHelper 会再乘 poseStack 的视图矩阵，
                // 最终 = V * modelTransform * locatorTransform * localPos，与模型渲染一致。
                Matrix4f modelSpaceTransform = new Matrix4f(modelTransform).mul(transform);
                emitter.setEmitterTransform(modelSpaceTransform, worldTransform);
            }
        }

        EMITTER_LOCATOR_MAP.keySet().removeIf(ParticleEmitterInstance::isFinished);

        // 立即对新创建的 emitter 执行一次 dt=0 的 tick，
        // 触发 EmitterRateInstant 等组件在同帧生成粒子
        for (ParticleEmitterInstance emitter : newEmitters) {
            emitter.tick(0f);
        }
    }

    // ==================== IFPGeoItemRenderer: 第一人称渲染 ====================

    @Override
    public void renderFirstPerson(LocalPlayer player, ItemStack stack, ItemDisplayContext ctx,
                                  PoseStack poseStack, MultiBufferSource bufferSource,
                                  int light, float partialTick) {
        Minecraft mc = Minecraft.getInstance();

        poseStack.pushPose();

        // 视角晃动（Bob）
        if (mc.options.bobView().get() && mc.getCameraEntity() instanceof Player p) {
            float f = p.walkDist - p.walkDistO;
            float f1 = -(p.walkDist + f * partialTick);
            float f2 = Mth.lerp(partialTick, p.oBob, p.bob);
            poseStack.mulPose(Axis.XN.rotationDegrees(Math.abs(Mth.cos(f1 * (float) Math.PI - 0.2F) * f2) * 5.0F));
            poseStack.mulPose(Axis.ZN.rotationDegrees(Mth.sin(f1 * (float) Math.PI) * f2 * 3.0F));
            poseStack.translate(-Mth.sin(f1 * (float) Math.PI) * f2 * 0.5F, Math.abs(Mth.cos(f1 * (float) Math.PI) * f2), 0.0F);
        }
        poseStack.translate(0.125, -0.5, -1.03125);

        // 渲染枪模型
        VertexConsumer buffer = MATERIAL.buffer(bufferSource, RenderType::entityCutout);
        model.renderToBuffer(poseStack, buffer, light, OverlayTexture.NO_OVERLAY);

        // 渲染玩家手部模型
        if (mc.getCameraEntity() instanceof AbstractClientPlayer abstractClientPlayer) {
            BedrockBone leftHandBone = model.getBone("lefthand_pos");
            BedrockBone rightHandBone = model.getBone("righthand_pos");
            RenderSystem.setShaderTexture(0, abstractClientPlayer.getSkin().texture());
            PlayerRenderer playerRenderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(abstractClientPlayer);
            if (leftHandBone != null) {
                Matrix4f globalTransform = leftHandBone.getGlobalTransform();
                poseStack.pushPose();
                poseStack.last().pose().mul(globalTransform);
                playerRenderer.renderLeftHand(poseStack, bufferSource, light, abstractClientPlayer);
                poseStack.popPose();
            }
            if (rightHandBone != null) {
                Matrix4f globalTransform = rightHandBone.getGlobalTransform();
                poseStack.pushPose();
                poseStack.last().pose().mul(globalTransform);
                playerRenderer.renderRightHand(poseStack, bufferSource, light, abstractClientPlayer);
                poseStack.popPose();
            }
        }

        poseStack.popPose();

        // 恢复到 bind pose，避免动画污染后续渲染
        model.applyPose(model.getBindPose());
    }

    // ==================== 非第一人称渲染 ====================

    @ParametersAreNonnullByDefault
    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource bufferSource, int light, int overlay) {
        if (ctx.firstPerson()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        VertexConsumer buffer = MATERIAL.buffer(bufferSource, RenderType::entityCutout);
        model.renderToBuffer(poseStack, buffer, light, overlay);
        poseStack.popPose();
    }
}
