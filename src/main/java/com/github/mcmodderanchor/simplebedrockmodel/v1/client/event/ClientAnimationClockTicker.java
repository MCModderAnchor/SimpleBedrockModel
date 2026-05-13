package com.github.mcmodderanchor.simplebedrockmodel.v1.client.event;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClock;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.animation.PausedClientAnimationClock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;


@EventBusSubscriber(modid = SimpleBedrockModel.MOD_ID, value = Dist.CLIENT)
public final class ClientAnimationClockTicker {
    private ClientAnimationClockTicker() {}

    public static AnimationClock getAnimationClock() {
        return PausedClientAnimationClock.getInstance();
    }

    @SubscribeEvent
    public static void onRenderTick(RenderFrameEvent.Pre event) {
        PausedClientAnimationClock.getInstance().update();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PausedClientAnimationClock.getInstance().reset();
    }
}
