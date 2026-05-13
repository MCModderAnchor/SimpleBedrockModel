package example.item;

import example.animation.DeagleAnimationGraph;
import example.animation.FPGunAnimationInstance;
import example.animation.GunAnimationGraph;
import example.client.render.item.DeagleWithoutLevelRenderer;
import example.init.ExampleModRegister;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.jetbrains.annotations.NotNull;

import javax.annotation.ParametersAreNonnullByDefault;

@EventBusSubscriber
public class DeagleItem extends Item implements GunItem {
    public DeagleItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    @ParametersAreNonnullByDefault
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand) {
//        entity.getCapability(ModCapability.FPGUN_ANIMATION_CAPABILITY).ifPresent(capability -> {
//            capability.getAnimationInstance().trigger();
//        });
        return true;
    }

//    @Override
//    public boolean onBlockStartBreak(ItemStack itemstack, BlockPos pos, Player player) {
//        return true;
//    }

    @Override
    @ParametersAreNonnullByDefault
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return false;
    }

    @Override
    @ParametersAreNonnullByDefault
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        return true;
    }

    @SubscribeEvent
    public static void initializeClient(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            public static final DeagleWithoutLevelRenderer render = new DeagleWithoutLevelRenderer();

            @Override
            public @NotNull BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return render;
            }
        }, ExampleModRegister.DEAGLE_ITEM);
    }

    @Override
    public GunAnimationGraph getAnimationGraph(FPGunAnimationInstance animationInstance) {
        return new DeagleAnimationGraph(animationInstance);
    }

    @Override
    public boolean hasMagInstalled(ItemStack itemStack) {
//        CompoundTag nbt = itemStack.get();
//        if (nbt.contains("HasMagInstalled")) {
//            return nbt.getBoolean("HasMagInstalled");
//        }
        return false;
    }

    @Override
    public int getAmmoInMag(ItemStack itemStack) {
//        CompoundTag nbt = itemStack.getOrCreateTag();
//        if (nbt.contains("AmmoInMag")) {
//            return nbt.getInt("AmmoInMag");
//        }
        return 99;
    }

    @Override
    public int getAmmoInGun(ItemStack itemStack) {
//        CompoundTag nbt = itemStack.getOrCreateTag();
//        if (nbt.contains("AmmoInGun")) {
//            return nbt.getInt("AmmoInGun");
//        }
        return 99;
    }

    @Override
    public void setMagInstalled(ItemStack itemStack, boolean installed) {
//        CompoundTag nbt = itemStack.getOrCreateTag();
//        nbt.putBoolean("HasMagInstalled", installed);
    }

    @Override
    public void setAmmoInMag(ItemStack itemStack, int ammo) {
//        CompoundTag nbt = itemStack.getOrCreateTag();
//        nbt.putInt("AmmoInMag", ammo);
    }

    @Override
    public void setAmmoInGun(ItemStack itemStack, int ammo) {
//        CompoundTag nbt = itemStack.getOrCreateTag();
//        nbt.putInt("AmmoInGun", ammo);
    }
}
