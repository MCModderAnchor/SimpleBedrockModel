package com.github.mcmodderanchor.simplebedrockmodel.v1.resource;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.event.RegisterBedrockAnimationEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.event.RegisterBedrockAnimationReloadListenerEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.event.RegisterBedrockModelEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.event.RegisterBedrockModelReloadListenerEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.resource.ParticleDefinitionLoader;
import com.github.mcmodderanchor.simplebedrockmodel.v2.event.RegisterV2BedrockResourcesEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.BedrockAnimationResources;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.BedrockModelResources;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.common.Mod;

public class ReloadListenersRegister {
    @OnlyIn(Dist.CLIENT)
    @Mod.EventBusSubscriber(modid = SimpleBedrockModel.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class BedrockModelClientRegister {
        @SubscribeEvent
        public static void onRegisterReloadListener(RegisterClientReloadListenersEvent event) {
            RegisterBedrockModelEvent event1 = new RegisterBedrockModelEvent(Dist.CLIENT);
            ModLoader.get().postEvent(event1);
            RegisterBedrockModelReloadListenerEvent event2 = new RegisterBedrockModelReloadListenerEvent();
            ModLoader.get().postEvent(event2);
            BedrockModelResourceSet.INSTANCE = new BedrockModelResourceSet(event1.getModelRegistry(), event2.getListeners());


            RegisterBedrockAnimationEvent event3 = new RegisterBedrockAnimationEvent(Dist.CLIENT);
            ModLoader.get().postEvent(event3);
            RegisterBedrockAnimationReloadListenerEvent event4 = new RegisterBedrockAnimationReloadListenerEvent();
            ModLoader.get().postEvent(event4);
            BedrockAnimationResourceSet.INSTANCE = new BedrockAnimationResourceSet(event3.getAnimationRegistry(), event4.getListeners());


            RegisterV2BedrockResourcesEvent event5 = new RegisterV2BedrockResourcesEvent(Dist.CLIENT);
            ModLoader.get().postEvent(event5);
            BedrockAnimationResources.INSTANCE = new BedrockAnimationResources(event5.getAnimationRegistry());
            BedrockModelResources.INSTANCE = new BedrockModelResources(event5.getModelRegistry(), event5.getReloadListeners());


            event.registerReloadListener(BedrockModelResourceSet.INSTANCE);
            event.registerReloadListener(BedrockAnimationResourceSet.INSTANCE);
            event.registerReloadListener(BedrockAnimationResources.INSTANCE);
            event.registerReloadListener(BedrockModelResources.INSTANCE);
            event.registerReloadListener(ParticleDefinitionLoader.getInstance());
        }
    }

    @OnlyIn(Dist.DEDICATED_SERVER)
    @Mod.EventBusSubscriber(modid = SimpleBedrockModel.MOD_ID, value = Dist.DEDICATED_SERVER, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class BedrockModelServerRegister {
        @SubscribeEvent
        public static void onRegisterReloadListener(AddReloadListenerEvent event) {
            RegisterBedrockModelEvent event1 = new RegisterBedrockModelEvent(Dist.DEDICATED_SERVER);
            ModLoader.get().postEvent(event1);
            RegisterBedrockModelReloadListenerEvent event2 = new RegisterBedrockModelReloadListenerEvent();
            ModLoader.get().postEvent(event2);
            BedrockModelResourceSet.INSTANCE = new BedrockModelResourceSet(event1.getModelRegistry(), event2.getListeners());


            RegisterBedrockAnimationEvent event3 = new RegisterBedrockAnimationEvent(Dist.DEDICATED_SERVER);
            ModLoader.get().postEvent(event3);
            RegisterBedrockAnimationReloadListenerEvent event4 = new RegisterBedrockAnimationReloadListenerEvent();
            ModLoader.get().postEvent(event4);
            BedrockAnimationResourceSet.INSTANCE = new BedrockAnimationResourceSet(event3.getAnimationRegistry(), event4.getListeners());


            RegisterV2BedrockResourcesEvent event5 = new RegisterV2BedrockResourcesEvent(Dist.DEDICATED_SERVER);
            ModLoader.get().postEvent(event5);
            BedrockAnimationResources.INSTANCE = new BedrockAnimationResources(event5.getAnimationRegistry());
            BedrockModelResources.INSTANCE = new BedrockModelResources(event5.getModelRegistry(), event5.getReloadListeners());


            event.addListener(BedrockModelResourceSet.INSTANCE);
            event.addListener(BedrockAnimationResourceSet.INSTANCE);
            event.addListener(BedrockAnimationResources.INSTANCE);
            event.addListener(BedrockModelResources.INSTANCE);
        }
    }
}
