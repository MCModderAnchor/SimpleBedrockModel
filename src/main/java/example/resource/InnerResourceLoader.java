package example.resource;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.model.BedrockArmorModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.model.EntityModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.event.RegisterBedrockAnimationEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.event.RegisterBedrockModelEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.event.RegisterBedrockModelReloadListenerEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.resource.RawResourceLoaders;
import example.client.render.entity.ZtiRenderer;
import example.init.ExampleModRegister;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class InnerResourceLoader {

    public static final ResourceLocation DEFENDER = new ResourceLocation(ExampleModRegister.MOD_ID, "defender.geo");
    public static BedrockArmorModel DEFENDER_MODEL;

    @SubscribeEvent
    public static void onAnimationRegister(RegisterBedrockAnimationEvent event) {
        event.register(ZtiRenderer.ANIMATION, ZtiRenderer.MODEL, RawResourceLoaders.COMMON_LOADER);
    }

    @SubscribeEvent
    public static void onModelRegister(RegisterBedrockModelEvent event) {
        event.register(ZtiRenderer.MODEL, RawResourceLoaders.COMMON_LOADER, EntityModel::new);
        event.register(DEFENDER, RawResourceLoaders.COMMON_LOADER, BedrockArmorModel::new);
    }

    @SubscribeEvent
    public static void onModelLoaded(RegisterBedrockModelReloadListenerEvent event) {
        event.register(map -> {
            DEFENDER_MODEL = (BedrockArmorModel) map.get(DEFENDER);
        });
    }
}
