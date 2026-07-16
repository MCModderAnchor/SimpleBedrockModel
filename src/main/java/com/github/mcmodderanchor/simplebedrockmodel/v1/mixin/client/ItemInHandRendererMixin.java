package com.github.mcmodderanchor.simplebedrockmodel.v1.mixin.client;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.handler.FirstPersonRenderHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemInHandRenderer.class, priority = 2000)
public class ItemInHandRendererMixin {

    @Shadow
    private float mainHandHeight;

    @Shadow
    private float oMainHandHeight;

    @Shadow
    private float offHandHeight;

    @Shadow
    private float oOffHandHeight;

    /**
     * 把 vanilla 的 {@code ItemStack.matches}（NBT 精确匹配）替换为本系统的「同一持有物」语义。
     * 枪械每开一发 NBT 即变，vanilla 会因此判定为不同物品而拒绝刷新缓存堆，导致弹药数等
     * NBT 变化无法即时反映到第一人称渲染。自定义物品需按 NBT 容忍语义判定为同一物品。
     * 非自定义物品回退到 vanilla {@code matches} 行为。
     */
    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;matches(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z")
    )
    private boolean sbm$matchesHeldItem(ItemStack a, ItemStack b, Operation<Boolean> original) {
        if (FirstPersonRenderHandler.hasCustomRenderer(a) || FirstPersonRenderHandler.hasCustomRenderer(b)) {
            return FirstPersonRenderHandler.isSameHeldItem(a, b);
        }
        return original.call(a, b);
    }

    /**
     * 把 vanilla 的重新装备（下沉）判定替换为 NBT 容忍语义。
     * 自定义物品只要是同一持有物就不触发重装下沉动画（避免开火 NBT 变化引发抖动）；
     * 非自定义物品回退到 Forge 原判定。
     */
    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/client/ClientHooks;shouldCauseReequipAnimation(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;I)Z",
                    remap = false
            )
    )
    private boolean sbm$shouldReequip(ItemStack cached, ItemStack current, int slot, Operation<Boolean> original) {
        if (FirstPersonRenderHandler.hasCustomRenderer(cached) || FirstPersonRenderHandler.hasCustomRenderer(current)) {
            return !FirstPersonRenderHandler.isSameHeldItem(cached, current);
        }
        return original.call(cached, current, slot);
    }

    /**
     * 收枪过渡期间钉住 vanilla 物品升降高度，屏蔽原版升降动画，让自定义收枪动画独占表现。
     * 按手分别处理：仅钉住正在过渡的那只手，另一只手保留 vanilla {@code tick()} 的正常推进。
     * 在 TAIL 覆盖最终结果——此时 matches/reequip 的副作用已被上面的 WrapOperation 修正，
     * 不会污染缓存物品，故 TAIL 钉高度是安全的。
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void sbm$pinHeightDuringSheathe(CallbackInfo ci) {
        if (FirstPersonRenderHandler.shouldLockVanilla(InteractionHand.MAIN_HAND)) {
            this.mainHandHeight = 1.0F;
            this.oMainHandHeight = 1.0F;
        }
        if (FirstPersonRenderHandler.shouldLockVanilla(InteractionHand.OFF_HAND)) {
            this.offHandHeight = 1.0F;
            this.oOffHandHeight = 1.0F;
        }
    }
}
