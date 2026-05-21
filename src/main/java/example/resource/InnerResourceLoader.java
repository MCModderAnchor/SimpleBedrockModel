package example.resource;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.model.BedrockArmorModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.event.RegisterBedrockModelEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.event.RegisterBedrockModelReloadListenerEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.resource.RawResourceLoaders;
import example.init.ExampleModRegister;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber
public class InnerResourceLoader {

    public static final ResourceLocation DEFENDER = ResourceLocation.fromNamespaceAndPath(ExampleModRegister.MOD_ID, "defender.geo");
    public static BedrockArmorModel DEFENDER_MODEL;

    @SubscribeEvent
    public static void onModelRegister(RegisterBedrockModelEvent event) {
        event.register(DEFENDER, RawResourceLoaders.COMMON_LOADER, BedrockArmorModel::new);
    }

    @SubscribeEvent
    public static void onModelLoaded(RegisterBedrockModelReloadListenerEvent event) {
        event.register(map -> {
            DEFENDER_MODEL = (BedrockArmorModel) map.get(DEFENDER);
        });
    }
}
