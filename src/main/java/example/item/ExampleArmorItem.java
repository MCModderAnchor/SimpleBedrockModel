package example.item;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.renderer.GeoArmorRendererV2;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.TreeBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.BedrockModelResources;
import example.resource.InnerResourceLoader;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class ExampleArmorItem extends ArmorItem {

    public ExampleArmorItem(ArmorItem.Type type) {
        super(ArmorMaterials.DIAMOND, type, new Item.Properties().stacksTo(1));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private GeoArmorRendererV2 renderer;

            @Override
            public @NotNull HumanoidModel<?> getHumanoidArmorModel(LivingEntity livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, HumanoidModel<?> original) {
                if (this.renderer == null) {
                    BedrockModelResources resources = BedrockModelResources.getInstance();
                    TreeBedrockModel model = resources.getTreeModel(InnerResourceLoader.DEFENDER);
                    this.renderer = new GeoArmorRendererV2(
                            model,
                            ExampleArmorItem.this.getEquipmentSlot(),
                            new ResourceLocation("example", "textures/armor/defender.png")
                    );
                }

                this.renderer.preparePose(livingEntity, itemStack, equipmentSlot, original);

                return this.renderer;
            }
        });
    }
}
