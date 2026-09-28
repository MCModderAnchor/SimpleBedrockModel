package com.github.mcmodderanchor.simplebedrockmodel.v2.client.world;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.ApiStatus;

/** 资源重载的 apply 阶段运行于客户端主线程；世界卸载监听由渲染器的注解订阅器处理。 */
@ApiStatus.Internal
@Mod.EventBusSubscriber(modid = SimpleBedrockModel.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class WorldMeshLifecycle {
    private WorldMeshLifecycle() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            WorldMeshRenderer.clearCaches();
            ImmediateStaticMeshRenderer.clear();
        });
    }
}
