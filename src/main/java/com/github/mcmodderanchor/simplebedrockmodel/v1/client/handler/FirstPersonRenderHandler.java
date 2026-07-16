package com.github.mcmodderanchor.simplebedrockmodel.v1.client.handler;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClock;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClocks;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.animation.IFPAnimationInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.event.SwapItemWithOffHand;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.IFPGeoItemRenderer;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.firstperson.FirstPersonParticleSystem;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.render.CameraStateCache;
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

    /**
     * 单手第一人称渲染状态。主副手各持一份，互不干扰。
     * 收枪过渡（put_away）是跨多帧的异步过程：旧 instance 保留为 {@link #previousInstance}
     * 播放收枪动画，过渡结束后才切到新 instance。
     */
    private static final class HandRenderState {
        /** 该手当前真实物品（切换检测基准）。 */
        ItemStack realItem = ItemStack.EMPTY;
        /** 当前活跃 instance。 */
        IFPAnimationInstance activeInstance = null;
        /** 收枪过渡中的旧 instance。 */
        IFPAnimationInstance previousInstance = null;
        /** 过渡完成后要切入的目标物品。 */
        ItemStack pendingTarget = ItemStack.EMPTY;
        /** 是否处于收枪过渡。 */
        boolean transitioning = false;
        /** 过渡目标是否为自定义物品（驱动 vanilla height 目标值）。 */
        boolean nextIsCustom = false;
        /** 当前活跃 instance 的渲染变体键（{@link IFPGeoItemRenderer#getRenderVariantKey}）。 */
        Object variantKey = null;
        long switchStartTime = 0L;
        long currentSheatheDuration = 0L;

        void reset() {
            realItem = ItemStack.EMPTY;
            activeInstance = null;
            previousInstance = null;
            pendingTarget = ItemStack.EMPTY;
            transitioning = false;
            nextIsCustom = false;
            variantKey = null;
            switchStartTime = 0L;
            currentSheatheDuration = 0L;
        }
    }

    private static final HandRenderState MAIN_STATE = new HandRenderState();
    private static final HandRenderState OFF_STATE = new HandRenderState();

    /** 主手选中槽（仅主手有槽位概念）。 */
    private static int realSelectedSlot = -1;

    // 上一刻「主手是否霸占副手视野」的镜像。由 false→true 时丢弃副手实例（被遮挡，收枪不可见），
    // true→false 时副手按当前物品重新掏枪，避免遮挡解除后副手旧实例原样「秒出」。
    private static boolean mainOccupiedOffhand = false;

    private static boolean forceHandSwapFlag = false;

    private static HandRenderState stateFor(InteractionHand hand) {
        return hand == InteractionHand.OFF_HAND ? OFF_STATE : MAIN_STATE;
    }

    private static InteractionHand handForState(HandRenderState state) {
        return state == OFF_STATE ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    public static void reset() {
        realSelectedSlot = -1;
        forceHandSwapFlag = false;
        mainOccupiedOffhand = false;
        MAIN_STATE.reset();
        OFF_STATE.reset();
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
        ItemStack newOff = player.getOffhandItem();

        // 交换主副手（F 键）：两手物品已对调，强制两手都「先收枪再切枪」。
        boolean swap = forceHandSwapFlag;
        forceHandSwapFlag = false;

        // 主手：选中槽变化 + 物品变化 + 渲染变体键变化检测（槽位是主手独有概念）。
        boolean mainSlotChanged = newSlot != realSelectedSlot;
        boolean mainItemChanged = !isSameItemStacks(MAIN_STATE.realItem, newMain);
        boolean mainVariantChanged = variantKeyChanged(MAIN_STATE, newMain, InteractionHand.MAIN_HAND);

        realSelectedSlot = newSlot;
        // 仅在检测到物品变化时刷新拷贝。存拷贝而非引用：物品被取走时 Minecraft 会原地把槽位
        // ItemStack 的 count 改为 0（isEmpty 变 true），若存引用则 realItem 随之变 0，下一 tick
        // isSameItemStacks 因「同一对象」短路为 true 而漏判「持有物消失」。无变化时旧拷贝仍是有效
        // 冻结快照，无需每 tick 重拷。
        if (mainItemChanged) {
            MAIN_STATE.realItem = newMain.copy();
        }
        // swap（两手对调）与切格子都是「明确的外部切换事件」：即便新旧物品同 id 同变体（值层面无差异），
        // 也确需重建，故强制穿透幂等短路。
        boolean mainForce = swap || mainSlotChanged;
        if (mainForce || mainItemChanged || mainVariantChanged) {
            beginSwitch(MAIN_STATE, newMain, mainForce);
        }

        // 仅在检测到物品变化时刷新副手拷贝（同主手，避免取走物品时引用同变漏判）。
        boolean offRealChanged = !isSameItemStacks(OFF_STATE.realItem, newOff);
        if (offRealChanged) {
            OFF_STATE.realItem = newOff.copy();
        }

        // 主手是否霸占副手视野（主手活跃 instance 判定，过渡期为正在收枪的旧 instance）。
        // 必须在副手切换处理之前计算：霸占期间副手不可见，应完全抑制副手掏枪，
        // 否则副手会在被遮挡时静默掏枪，待霸占解除后凭空「弹出」。
        boolean occupiedNow = mainHandBlocksOffhand();
        boolean occupyFlip = occupiedNow != mainOccupiedOffhand;
        mainOccupiedOffhand = occupiedNow;

        if (occupiedNow) {
            // 主手霸占副手视野：丢弃副手实例（不可见，收枪动画无意义），且不掏新枪。
            if (occupyFlip) {
                discardOffhandInstance();
                // 副手被遮挡：停止副手发射器，不再产出新粒子（已生成粒子继续消亡）。
                PARTICLE_SYSTEM.stopEmitters(InteractionHand.OFF_HAND);
            }
        } else if (occupyFlip) {
            // 霸占刚解除：副手按当前物品全新掏枪（可见的掏枪动画）。
            if (!newOff.isEmpty()) {
                beginSwitch(OFF_STATE, newOff, true);
            }
        } else {
            // 未被霸占的常规路径：副手物品变化 / 渲染变体键变化 / swap 时切换。
            boolean offVariantChanged = variantKeyChanged(OFF_STATE, newOff, InteractionHand.OFF_HAND);
            if (swap || offRealChanged || offVariantChanged) {
                beginSwitch(OFF_STATE, newOff, swap);
            }
        }

        if (MAIN_STATE.activeInstance != null) {
            MAIN_STATE.activeInstance.updateItem(newMain);
        }
        if (OFF_STATE.activeInstance != null) {
            OFF_STATE.activeInstance.updateItem(newOff);
        }

        tickStates(MAIN_STATE, InteractionHand.MAIN_HAND);
        tickStates(OFF_STATE, InteractionHand.OFF_HAND);
    }

    /**
     * 检测某手「渲染变体键」是否相对当前活跃 instance 发生变化（物品同一但呈现形态需切换）。
     * 无活跃 instance 时不算变化（由物品变化路径负责建立）。
     */
    private static boolean variantKeyChanged(HandRenderState state, ItemStack heldItem, InteractionHand hand) {
        if (state.activeInstance == null || heldItem.isEmpty()) {
            return false;
        }
        Object newKey = getRenderVariantKey(heldItem, hand);
        return !java.util.Objects.equals(newKey, state.variantKey);
    }

    private static Object getRenderVariantKey(ItemStack stack, InteractionHand hand) {
        return getRenderer(stack)
                .map(r -> r.getRenderVariantKey(stack, hand))
                .orElse(null);
    }

    /**
     * 切换某只手的物品 / 渲染变体：旧 instance 若为自定义物品，进入收枪过渡；否则直接切到新 instance。
     * <p>
     * 「物品变化」与「渲染变体键变化」共用此通路，统一走 put_away → draw，由本类单一管理实例生命周期。
     *
     * @param force 是否穿透幂等短路（用于 swap 等「值层面无差异但确需重建」的明确外部事件）
     */
    private static void beginSwitch(HandRenderState state, ItemStack newStack, boolean force) {
        InteractionHand hand = handForState(state);
        Object newVariantKey = getRenderVariantKey(newStack, hand);
        state.pendingTarget = newStack;
        state.nextIsCustom = isCustomItem(newStack);

        if (state.transitioning) {
            return;
        }

        // 幂等保护：当前活跃 instance 已代表「同一持有物 + 同一渲染变体」时不重复切换，
        // 避免「物品变化」与「变体变化」在同一目标上重复触发，导致连续掏两次。
        // force=true（如 swap 对调两把同 id 枪）时穿透此短路，强制重建。
        if (!force
                && state.activeInstance != null
                && isSameItemStacks(state.activeInstance.currentItem(), newStack)
                && java.util.Objects.equals(state.variantKey, newVariantKey)) {
            return;
        }

        boolean oldIsCustom = state.activeInstance != null;

        // 切换物品时停止该手旧发射器（已生成粒子继续按生命周期消亡）。
        PARTICLE_SYSTEM.stopEmitters(hand);

        state.previousInstance = state.activeInstance;
        state.activeInstance = createInstance(newStack, hand);
        state.variantKey = newVariantKey;

        if (oldIsCustom) {
            state.transitioning = true;
            state.switchStartTime = CLOCK.nowMillis();
            state.currentSheatheDuration = calculateSheatheDuration(state.previousInstance.currentItem());
            state.previousInstance.triggerPutAway();
        } else {
            state.transitioning = false;
        }
    }

    private static void tickStates(HandRenderState state, InteractionHand hand) {
        if (state.transitioning && getSheatheProgress(state) >= 1.0f) {
            state.transitioning = false;
            // 过渡完成后用该手当前真实物品创建 instance（而非可能过期的 pendingTarget），
            // 并按当前形态记录变体键。
            state.activeInstance = createInstance(state.realItem, hand);
            state.variantKey = getRenderVariantKey(state.realItem, hand);
            state.previousInstance = null;
        }
    }

    /**
     * 直接丢弃副手渲染实例（不播收枪过渡）。用于主手霸占副手视野时——副手被遮挡，
     * 收枪动画不可见，无需过渡；遮挡解除时再按当前物品重新掏枪。
     */
    private static void discardOffhandInstance() {
        OFF_STATE.activeInstance = null;
        OFF_STATE.previousInstance = null;
        OFF_STATE.pendingTarget = ItemStack.EMPTY;
        OFF_STATE.transitioning = false;
        OFF_STATE.variantKey = null;
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

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        tickHandAnimation(MAIN_STATE, InteractionHand.MAIN_HAND, partialTick);
        tickHandAnimation(OFF_STATE, InteractionHand.OFF_HAND, partialTick);
    }

    private static void tickHandAnimation(HandRenderState state, InteractionHand hand, float partialTick) {
        IFPAnimationInstance ani = state.transitioning ? state.previousInstance : state.activeInstance;
        if (ani == null || !canActuallyRenderInHand(ani, hand)) {
            return;
        }

        ani.triggerDraw();
        ani.tick(partialTick);
    }

    private static boolean canActuallyRenderInHand(IFPAnimationInstance ani, InteractionHand hand) {
        if (hand == InteractionHand.OFF_HAND && mainHandBlocksOffhand()) {
            return false;
        }

        ItemStack stack = ani.currentItem();
        if (stack.isEmpty()) {
            return false;
        }

        return getRenderer(stack)
                .map(renderer -> renderer.canRenderInHand(stack, hand))
                .orElse(false);
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        InteractionHand hand = event.getHand();

        // 副手渲染前先看主手是否禁止副手渲染（如主手为双手长枪，会霸占副手）。
        // 这是主手视角的判定，与副手物品自身无关。
        if (hand == InteractionHand.OFF_HAND && mainHandBlocksOffhand()) {
            renderParticlesIfAny(event);
            event.setCanceled(true);
            return;
        }

        IFPAnimationInstance inst = getActiveAnimationInstance(hand);
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

        // 物品自身判定能否在该手渲染（如双手长枪放副手时不在副手渲染）。
        if (!renderer.canRenderInHand(stack, hand)) {
            renderParticlesIfAny(event);
            event.setCanceled(true);
            return;
        }

        ItemDisplayContext transformType = hand == InteractionHand.OFF_HAND
                ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;

        // 更新该手粒子发射器变换（主副手各自在自己的 pass 内捕获基准、绑定枪口）。
        renderer.updateParticleEmitterTransforms(PARTICLE_SYSTEM, event.getPoseStack(), hand);

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
     * 渲染当前 pass 这只手的活跃粒子。
     * <p>
     * 主副手各在自己的 {@code RenderHandEvent} pass 内渲染自己那组发射器——两手 pass 的
     * poseStack 基准不同，必须按手渲染，不可跨手共用基准。
     */
    private static void renderParticlesIfAny(RenderHandEvent event) {
        if (PARTICLE_SYSTEM.getParticleCount() == 0) {
            return;
        }

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float cameraPitchRad = (float) Math.toRadians(camera.getXRot());
        float cameraRollRad = CameraStateCache.getCameraRollRadians();
        Matrix4f cameraRotation = buildCameraRotation(camera, cameraRollRad);

        PARTICLE_SYSTEM.render(
                event.getHand(),
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
        return shouldLockVanilla(InteractionHand.MAIN_HAND);
    }

    /**
     * 该手是否处于收枪过渡（需要钉住 vanilla height、屏蔽原版升降动画）。
     */
    public static boolean shouldLockVanilla(InteractionHand hand) {
        return stateFor(hand).transitioning;
    }

    public static float getTargetHeight() {
        return getTargetHeight(InteractionHand.MAIN_HAND);
    }

    public static float getTargetHeight(InteractionHand hand) {
        return stateFor(hand).nextIsCustom ? 1.0F : 0.0F;
    }

    /**
     * 获取主手当前活跃动画 instance（过渡期返回正在收枪的旧 instance）。
     * 无参版本保持主手语义，兼容现有调用方。
     */
    public static IFPAnimationInstance getActiveAnimationInstance() {
        return getActiveAnimationInstance(InteractionHand.MAIN_HAND);
    }

    /**
     * 获取指定手的当前活跃动画 instance（过渡期返回正在收枪的旧 instance）。
     */
    public static IFPAnimationInstance getActiveAnimationInstance(InteractionHand hand) {
        HandRenderState state = stateFor(hand);
        return state.transitioning ? state.previousInstance : state.activeInstance;
    }

    /**
     * 摄像机使用的当前活跃动画 instance：主手优先，主手不存在时回退副手。
     */
    public static IFPAnimationInstance getActiveAnimationInstanceForCamera() {
        IFPAnimationInstance main = getActiveAnimationInstance(InteractionHand.MAIN_HAND);
        return main != null ? main : getActiveAnimationInstance(InteractionHand.OFF_HAND);
    }

    /**
     * 获取全局第一人称粒子系统。所有自定义物品渲染器通过此方法添加发射器。
     */
    public static FirstPersonParticleSystem getParticleSystem() {
        return PARTICLE_SYSTEM;
    }

    private static IFPAnimationInstance createInstance(ItemStack stack, InteractionHand hand) {
        return getRenderer(stack)
                .map(r -> r.createAnimationInstance(stack, Minecraft.getInstance().getCameraEntity(), hand))
                .orElse(null);
    }

    private static boolean isCustomItem(ItemStack stack) {
        return getRenderer(stack).isPresent();
    }

    /**
     * 该物品是否拥有自定义第一人称渲染器（即由本系统接管渲染的物品，如枪械）。
     * 供 vanilla tick 接管逻辑判断是否套用「NBT 变化不算换物品」的语义。
     */
    public static boolean hasCustomRenderer(ItemStack stack) {
        return getRenderer(stack).isPresent();
    }

    /**
     * 按本系统语义判断两个物品堆是否为「同一持有物」。
     * 对自定义物品（枪械）走渲染器的 {@code isSameItem}（NBT 变化不视为新物品），
     * 否则回退到 vanilla 的 {@code ItemStack.isSameItem}。
     */
    public static boolean isSameHeldItem(ItemStack a, ItemStack b) {
        return isSameItemStacks(a, b);
    }

    private static long calculateSheatheDuration(ItemStack stack) {
        return getRenderer(stack)
                .map(r -> r.getPutAwayDuration(stack))
                .orElse(0L);
    }

    private static float getSheatheProgress(HandRenderState state) {
        if (state.currentSheatheDuration <= 0) {
            return 1.0f;
        }
        long elapsed = CLOCK.nowMillis() - state.switchStartTime;
        return Math.min(1.0f, (float) elapsed / state.currentSheatheDuration);
    }

    /**
     * 主手当前活跃 instance 是否霸占副手第一人称视野（如主手为双手长枪 / 单手枪双手持握）。
     * <p>
     * 取主手当前活跃 instance（过渡期为正在收枪的旧 instance）的 {@link IFPAnimationInstance#occupiesView()}：
     * 该判定绑定实例固定的渲染形态，过渡期间稳定，避免因实时手持物已变导致霸占状态抖动。
     */
    private static boolean mainHandBlocksOffhand() {
        IFPAnimationInstance mainInst = getActiveAnimationInstance(InteractionHand.MAIN_HAND);
        return mainInst != null && mainInst.occupiesView();
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
