package example.client.event;

import example.client.render.blockentity.TestBlockEntityRenderer;
import example.client.render.blockentity.V2TestBlockEntityRenderer;
import example.client.render.entity.ZtiRenderer;
import example.init.ExampleModRegister;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RegistryHandler {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ExampleModRegister.TEST_BLOCK_ENTITY_TYPE, V2TestBlockEntityRenderer::new);
        event.registerEntityRenderer(ExampleModRegister.ZTI_ENTITY_TYPE, ZtiRenderer::new);
    }
}
