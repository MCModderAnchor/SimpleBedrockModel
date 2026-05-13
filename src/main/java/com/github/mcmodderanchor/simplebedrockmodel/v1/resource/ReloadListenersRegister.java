package com.github.mcmodderanchor.simplebedrockmodel.v1.resource;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.event.RegisterBedrockAnimationEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.event.RegisterBedrockAnimationReloadListenerEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.event.RegisterBedrockModelEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.event.RegisterBedrockModelReloadListenerEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.resource.ParticleDefinitionLoader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.common.EventBusSubscriber;

public class ReloadListenersRegister {
    @OnlyIn(Dist.CLIENT)
    @EventBusSubscriber(modid = SimpleBedrockModel.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static class BedrockModelClientRegister {
        @SubscribeEvent
        public static void onRegisterReloadListener(RegisterClientReloadListenersEvent event) {
            RegisterBedrockModelEvent event1 = new RegisterBedrockModelEvent(Dist.CLIENT);
            ModLoader.postEvent(event1);
            RegisterBedrockModelReloadListenerEvent event2 = new RegisterBedrockModelReloadListenerEvent();
            ModLoader.postEvent(event2);
            BedrockModelResourceSet.INSTANCE = new BedrockModelResourceSet(event1.getModelRegistry(), event2.getListeners());


            RegisterBedrockAnimationEvent event3 = new RegisterBedrockAnimationEvent(Dist.CLIENT);
            ModLoader.postEvent(event3);
            RegisterBedrockAnimationReloadListenerEvent event4 = new RegisterBedrockAnimationReloadListenerEvent();
            ModLoader.postEvent(event4);
            BedrockAnimationResourceSet.INSTANCE = new BedrockAnimationResourceSet(event3.getAnimationRegistry(), event4.getListeners());


            event.registerReloadListener(BedrockModelResourceSet.INSTANCE);
            event.registerReloadListener(BedrockAnimationResourceSet.INSTANCE);
            event.registerReloadListener(ParticleDefinitionLoader.getInstance());
        }
    }

    @OnlyIn(Dist.DEDICATED_SERVER)
    @EventBusSubscriber(modid = SimpleBedrockModel.MOD_ID, value = Dist.DEDICATED_SERVER, bus = EventBusSubscriber.Bus.GAME)
    public static class BedrockModelServerRegister {
        @SubscribeEvent
        public static void onRegisterReloadListener(AddReloadListenerEvent event) {
            RegisterBedrockModelEvent event1 = new RegisterBedrockModelEvent(Dist.DEDICATED_SERVER);
            ModLoader.postEvent(event1);
            RegisterBedrockModelReloadListenerEvent event2 = new RegisterBedrockModelReloadListenerEvent();
            ModLoader.postEvent(event2);
            BedrockModelResourceSet.INSTANCE = new BedrockModelResourceSet(event1.getModelRegistry(), event2.getListeners());


            RegisterBedrockAnimationEvent event3 = new RegisterBedrockAnimationEvent(Dist.DEDICATED_SERVER);
            ModLoader.postEvent(event3);
            RegisterBedrockAnimationReloadListenerEvent event4 = new RegisterBedrockAnimationReloadListenerEvent();
            ModLoader.postEvent(event4);
            BedrockAnimationResourceSet.INSTANCE = new BedrockAnimationResourceSet(event3.getAnimationRegistry(), event4.getListeners());


            event.addListener(BedrockModelResourceSet.INSTANCE);
            event.addListener(BedrockAnimationResourceSet.INSTANCE);
        }
    }
}
