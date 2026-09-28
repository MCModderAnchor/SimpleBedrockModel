package example.client.worldmesh;

import com.github.mcmodderanchor.simplebedrockmodel.v2.event.RegisterV2BedrockResourcesEvent;
import example.init.ExampleModRegister;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 压测用模型：从 TACZ 默认枪包拷进来的 24 组 gun geo + 对应贴图
 * （{@code assets/example/models/bedrock/stress/gun_XX.json} 与 {@code assets/example/textures/stress/gun_XX.png}）。
 * 这些资源属于 example 命名空间，打包时被 {@code jar} 任务排除。
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ExampleStressResources {
    public static final int COUNT = 24;

    private ExampleStressResources() {
    }

    public static ResourceLocation model(int index) {
        return ExampleModRegister.modLoc("stress/gun_" + twoDigits(index));
    }

    public static ResourceLocation texture(int index) {
        return ExampleModRegister.modLoc("textures/stress/gun_" + twoDigits(index) + ".png");
    }

    private static String twoDigits(int index) {
        return index < 10 ? "0" + index : Integer.toString(index);
    }

    @SubscribeEvent
    public static void register(RegisterV2BedrockResourcesEvent event) {
        for (int i = 0; i < COUNT; i++) {
            event.treeModel(model(i)).register();
        }
    }
}
