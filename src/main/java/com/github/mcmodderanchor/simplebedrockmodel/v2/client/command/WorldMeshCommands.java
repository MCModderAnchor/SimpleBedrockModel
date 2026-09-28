package com.github.mcmodderanchor.simplebedrockmodel.v2.client.command;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.world.WorldMeshGroupStats;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.world.WorldMeshRenderer;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.world.StrategyOverride;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.ApiStatus;

import java.util.Locale;

/** 库随包提供的客户端配置快捷入口，与 example 压测命令独立。 */
@ApiStatus.Internal
@Mod.EventBusSubscriber(modid = SimpleBedrockModel.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldMeshCommands {
    private WorldMeshCommands() {}

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        var strategy = Commands.literal("strategy");
        for (StrategyOverride value : StrategyOverride.values()) {
            strategy.then(Commands.literal(value.name().toLowerCase(Locale.ROOT)).executes(context -> {
                WorldMeshRenderer.setStrategy(value);
                context.getSource().sendSuccess(() -> Component.literal("Configured strategy=" + value), false);
                for (WorldMeshGroupStats group : WorldMeshRenderer.groupStats()) {
                    String message = group.id() + ": " + group.effectiveStrategy() + " (" + group.policySource() + ") " + group.warnings();
                    context.getSource().sendSuccess(() -> Component.literal(message), false);
                }
                return 1;
            }));
        }
        event.getDispatcher().register(Commands.literal("sbmrender").then(strategy)
                .then(Commands.literal("groups").executes(context -> {
                    for (WorldMeshGroupStats group : WorldMeshRenderer.groupStats()) {
                        context.getSource().sendSuccess(() -> Component.literal(group.toString()), false);
                    }
                    return 1;
                })));
    }
}
