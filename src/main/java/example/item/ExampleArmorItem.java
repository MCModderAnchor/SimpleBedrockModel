package example.item;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.renderer.GeoArmorRendererV2;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.TreeBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.BedrockModelResources;
import example.init.ExampleModRegister;
import example.resource.InnerResourceLoader;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@EventBusSubscriber
public class ExampleArmorItem extends ArmorItem {

    public ExampleArmorItem(ArmorItem.Type type) {
        super(ArmorMaterials.DIAMOND, type, new Item.Properties().stacksTo(1));
    }

    @SubscribeEvent
    public static void initializeClient(RegisterClientExtensionsEvent event) {
        for (var item : List.of(
                ExampleModRegister.DEFENDER_ARMOR_BOOTS,
                ExampleModRegister.DEFENDER_ARMOR_CHESTPLATE,
                ExampleModRegister.DEFENDER_ARMOR_HELMET,
                ExampleModRegister.DEFENDER_ARMOR_LEGGINGS
        )) {
            event.registerItem(new IClientItemExtensions() {
                private GeoArmorRendererV2 renderer;

                @Override
                @ParametersAreNonnullByDefault
                public @NotNull HumanoidModel<?> getHumanoidArmorModel(
                        LivingEntity livingEntity,
                        ItemStack itemStack,
                        EquipmentSlot equipmentSlot,
                        HumanoidModel<?> original
                ) {
                    if (this.renderer == null) {
                        TreeBedrockModel model = BedrockModelResources.getInstance().getTreeModel(InnerResourceLoader.DEFENDER);
                        this.renderer = new GeoArmorRendererV2(
                                model,
                                item.getEquipmentSlot(),
                                ResourceLocation.fromNamespaceAndPath("example", "textures/armor/defender.png")
                        );
                    }

                    this.renderer.preparePose(livingEntity, itemStack, equipmentSlot, original);
                    return this.renderer;
                }
            }, item);
        }
    }

    @Override
    public @Nullable ResourceLocation getArmorTexture(
            ItemStack stack,
            Entity entity,
            EquipmentSlot slot,
            ArmorMaterial.Layer layer,
            boolean innerModel
    ) {
        return ResourceLocation.fromNamespaceAndPath("example", "textures/armor/defender.png");
    }
}
