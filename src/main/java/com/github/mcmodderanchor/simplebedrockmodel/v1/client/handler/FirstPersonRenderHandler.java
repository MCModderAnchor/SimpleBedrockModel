package com.github.mcmodderanchor.simplebedrockmodel.v1.client.handler;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClock;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClocks;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.animation.IFPAnimationInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.event.SwapItemWithOffHand;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.IFPGeoItemRenderer;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.firstperson.FirstPersonParticleSystem;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.render.CameraStateCache;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.joml.Matrix4f;

import java.util.Optional;

@EventBusSubscriber(Dist.CLIENT)
public class FirstPersonRenderHandler {
    private static final AnimationClock CLOCK = AnimationClocks.client();

    /** 全局第一人称粒子系统。所有自定义物品共享此实例，统一管理粒子生命周期和渲染。 */
    private static final FirstPersonParticleSystem PARTICLE_SYSTEM = new FirstPersonParticleSystem();
    private static long lastParticleTickNanos = 0L;

    private static int realSelectedSlot = -1;
    private static ItemStack realMainHand = ItemStack.EMPTY;

    private static boolean transitioning = false;

    private static IFPAnimationInstance activeInstance = null;
    private static IFPAnimationInstance previousInstance = null;

    private static ItemStack pendingTarget = ItemStack.EMPTY;

    private static long switchStartTime = 0L;
    private static long currentSheatheDuration = 0L;

    private static boolean lockVanilla = false;
    private static boolean nextIsCustom = false;

    private static boolean forceHandSwapFlag = false;

    @SubscribeEvent
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    public static void reset() {
        realSelectedSlot = -1;
        realMainHand = ItemStack.EMPTY;
        transitioning = false;
        activeInstance = null;
        previousInstance = null;
        pendingTarget = ItemStack.EMPTY;
        lockVanilla = false;
        nextIsCustom = false;
        forceHandSwapFlag = false;
        switchStartTime = 0L;
        currentSheatheDuration = 0L;
        PARTICLE_SYSTEM.clear();
        lastParticleTickNanos = 0L;
    }

    @SubscribeEvent
    public static void onRenderHand(SwapItemWithOffHand event) {
        forceHandSwapFlag = true;
    }

    @SubscribeEvent
    public static void onClientTick(RenderFrameEvent.Pre event) {
        if (!CLOCK.shouldTick()) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        int newSlot = player.getInventory().selected;
        ItemStack newMain = player.getMainHandItem();

        boolean slotChanged = newSlot != realSelectedSlot || forceHandSwapFlag;
        boolean itemChanged = !isSameItemStacks(realMainHand, newMain);
        forceHandSwapFlag = false;

        realSelectedSlot = newSlot;
        realMainHand = newMain;

        if (slotChanged) {
            onSlotChanged(newMain);
        } else if (itemChanged) {
            onItemChangedInSameSlot(newMain);
        }

        if (activeInstance != null) {
            activeInstance.updateItem(newMain);
        }

        tickStates();
    }

    private static void onSlotChanged(ItemStack newStack) {
        pendingTarget = newStack;
        nextIsCustom = isCustomItem(newStack);

        if (transitioning) {
            return;
        }

        boolean oldIsCustom = activeInstance != null;

        // 切换物品时停止旧发射器（不再产生新粒子，已有粒子继续存活至自然消亡）
        stopOldEmitters();

        previousInstance = activeInstance;
        activeInstance = createInstance(newStack);

        if (oldIsCustom) {
            transitioning = true;
            lockVanilla = true;
            switchStartTime = CLOCK.nowMillis();
            currentSheatheDuration = calculateSheatheDuration(previousInstance.currentItem());
            previousInstance.triggerPutAway();
        } else {
            transitioning = false;
            lockVanilla = false;
        }
    }

    private static void onItemChangedInSameSlot(ItemStack newStack) {
        pendingTarget = newStack;
        nextIsCustom = isCustomItem(newStack);

        if (transitioning) {
            return;
        }

        boolean oldIsCustom = activeInstance != null;

        // 切换物品时停止旧发射器（不再产生新粒子，已有粒子继续存活至自然消亡）
        stopOldEmitters();

        previousInstance = activeInstance;
        activeInstance = createInstance(newStack);

        if (oldIsCustom) {
            transitioning = true;
            lockVanilla = true;
            switchStartTime = CLOCK.nowMillis();
            currentSheatheDuration = calculateSheatheDuration(previousInstance.currentItem());
            previousInstance.triggerPutAway();
        } else {
            lockVanilla = false;
        }
    }

    private static void tickStates() {
        if (transitioning && getSheatheProgress() >= 1.0f) {
            transitioning = false;
            lockVanilla = false;
            activeInstance = createInstance(pendingTarget);
            previousInstance = null;
        }
    }

    @SubscribeEvent
    public static void tickAnimation(RenderFrameEvent.Pre event) {
        if (!CLOCK.shouldTick()) {
            return;
        }

        // 统一 tick 所有第一人称粒子（不依赖具体物品）
        long now = CLOCK.nowNanos();
        float dt = lastParticleTickNanos == 0L ? 0f : (now - lastParticleTickNanos) / 1_000_000_000f;
        dt = Math.min(dt, 0.1f);
        lastParticleTickNanos = now;
        PARTICLE_SYSTEM.tick(dt);

        IFPAnimationInstance ani = getActiveAnimationInstance();
        if (ani != null) {
            ani.triggerDraw();
            ani.tick(event.getPartialTick().getGameTimeDeltaPartialTick(true));
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        IFPAnimationInstance inst = getActiveAnimationInstance();
        if (inst == null) {
            renderParticlesIfAny(event);
            return;
        }

        ItemStack stack = inst.currentItem();
        if (stack.isEmpty()) {
            renderParticlesIfAny(event);
            return;
        }

        Optional<IFPGeoItemRenderer> optRenderer = getRenderer(stack);
        if (optRenderer.isEmpty()) {
            renderParticlesIfAny(event);
            return;
        }

        IFPGeoItemRenderer renderer = optRenderer.get();
        if (event.getHand() == InteractionHand.OFF_HAND && renderer.blockOffhandRender()) {
            event.setCanceled(true);
            event.setCanceled(true);
            return;
        }

        ItemDisplayContext transformType = ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;

        // 在渲染物品之前，让渲染器更新粒子发射器的变换矩阵
        renderer.updateParticleEmitterTransforms(PARTICLE_SYSTEM, event.getPoseStack());

        renderer.renderFirstPerson(
                player,
                stack,
                transformType,
                event.getPoseStack(),
                event.getMultiBufferSource(),
                event.getPackedLight(),
                event.getPartialTick()
        );
        event.setCanceled(true);

        renderParticlesIfAny(event);
    }

    /**
     * 如果有活跃粒子，在 PoseStack 上下文中渲染它们。
     */
    private static void renderParticlesIfAny(RenderHandEvent event) {
        if (event.getHand() == InteractionHand.OFF_HAND || PARTICLE_SYSTEM.getParticleCount() == 0) {
            return;
        }

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float cameraPitchRad = (float) Math.toRadians(camera.getXRot());
        float cameraRollRad = CameraStateCache.getCameraRollRadians();
        Matrix4f cameraRotation = buildCameraRotation(camera, cameraRollRad);

        PARTICLE_SYSTEM.render(
                event.getPoseStack(),
                event.getMultiBufferSource(),
                event.getPackedLight(),
                event.getPartialTick(),
                cameraPitchRad, cameraRollRad, cameraRotation
        );
    }

    /**
     * 用摄像机的 pitch/yaw/roll 构建视图旋转矩阵（与 Minecraft 内部一致）。
     * Minecraft 的视图矩阵构建顺序：先绕 X 旋转 pitch，再绕 Y 旋转 (yaw + 180)，最后绕 Z 旋转 roll。
     */
    private static Matrix4f buildCameraRotation(Camera camera, float rollRadians) {
        return new Matrix4f()
                .rotationX((float) Math.toRadians(camera.getXRot()))
                .rotateY((float) Math.toRadians(camera.getYRot() + 180f))
                .rotateZ(rollRadians);
    }

    public static boolean shouldLockVanilla() {
        return lockVanilla;
    }

    public static float getTargetHeight() {
        return nextIsCustom ? 1.0F : 0.0F;
    }

    public static IFPAnimationInstance getActiveAnimationInstance() {
        return transitioning ? previousInstance : activeInstance;
    }

    /**
     * 获取全局第一人称粒子系统。所有自定义物品渲染器通过此方法添加发射器。
     */
    public static FirstPersonParticleSystem getParticleSystem() {
        return PARTICLE_SYSTEM;
    }

    /**
     * 停止所有旧发射器（设置 removed 状态），使它们不再生成新粒子。
     * 已有粒子继续按其生命周期 tick，直到自然消亡。
     */
    private static void stopOldEmitters() {
        for (ParticleEmitterInstance emitter : PARTICLE_SYSTEM.getEmitters()) {
            emitter.setRemoved(true);
        }
    }

    private static IFPAnimationInstance createInstance(ItemStack stack) {
        return getRenderer(stack)
                .map(r -> r.createAnimationInstance(stack, Minecraft.getInstance().getCameraEntity()))
                .orElse(null);
    }

    private static boolean isCustomItem(ItemStack stack) {
        return getRenderer(stack).isPresent();
    }

    private static long calculateSheatheDuration(ItemStack stack) {
        return getRenderer(stack)
                .map(r -> r.getPutAwayDuration(stack))
                .orElse(0L);
    }

    private static float getSheatheProgress() {
        if (currentSheatheDuration <= 0) {
            return 1.0f;
        }
        long elapsed = CLOCK.nowMillis() - switchStartTime;
        return Math.min(1.0f, (float) elapsed / currentSheatheDuration);
    }

    private static Optional<IFPGeoItemRenderer> getRenderer(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        if (IClientItemExtensions.of(stack.getItem()).getCustomRenderer() instanceof IFPGeoItemRenderer renderer) {
            return Optional.of(renderer);
        }
        return Optional.empty();
    }

    private static boolean isSameItemStacks(ItemStack oldStack, ItemStack newStack) {
        if (oldStack == newStack) {
            return true;
        }
        if (oldStack.isEmpty() && newStack.isEmpty()) {
            return true;
        }
        if (oldStack.isEmpty() || newStack.isEmpty()) {
            return false;
        }

        return getRenderer(oldStack)
                .map(r -> r.isSameItem(oldStack, newStack))
                .orElseGet(() -> ItemStack.isSameItem(oldStack, newStack));
    }
}
