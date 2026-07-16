package example.client.event;

import example.client.render.blockentity.TreeTestBlockEntityRenderer;
import example.client.render.entity.ZtiRenderer;
import example.init.ExampleModRegister;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;


@EventBusSubscriber(value = Dist.CLIENT)
public class RegistryHandler {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ExampleModRegister.TEST_BLOCK_ENTITY_TYPE, TreeTestBlockEntityRenderer::new);
        event.registerEntityRenderer(ExampleModRegister.ZTI_ENTITY_TYPE, ZtiRenderer::new);
    }
}
