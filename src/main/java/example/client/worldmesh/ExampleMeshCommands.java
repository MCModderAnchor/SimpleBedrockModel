package example.client.worldmesh;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.world.StrategyOverride;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.world.WorldMeshRenderer;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.world.WorldMeshStats;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/** 示例侧按需诊断入口；命令和聊天格式不属于库 API。 */
@Mod.EventBusSubscriber(modid = SimpleBedrockModel.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ExampleMeshCommands {
    private ExampleMeshCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> stress = Commands.literal("stress")
                .then(Commands.literal("clear").executes(context -> {
                    ExampleStressMeshGroup.clear();
                    return reply(context, "Stress instances cleared.");
                }));
        for (ExampleStressMeshGroup.Mode mode : ExampleStressMeshGroup.Mode.values()) {
            stress.then(Commands.literal(mode.name().toLowerCase(Locale.ROOT))
                    .executes(context -> stress(context, mode, 48))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 512))
                            .executes(context -> stress(context, mode, IntegerArgumentType.getInteger(context, "count")))));
        }
        stress.then(Commands.literal("light")
                .then(Commands.literal("start").executes(context -> lightCycle(context, 20, 0))
                        .then(Commands.argument("intervalTicks", IntegerArgumentType.integer(1, 1200))
                                .executes(context -> lightCycle(context,
                                        IntegerArgumentType.getInteger(context, "intervalTicks"), 0))
                                .then(Commands.argument("batchSize", IntegerArgumentType.integer(1, 512))
                                        .executes(context -> lightCycle(context,
                                                IntegerArgumentType.getInteger(context, "intervalTicks"),
                                                IntegerArgumentType.getInteger(context, "batchSize"))))))
                .then(Commands.literal("stop").executes(context -> {
                    ExampleStressMeshGroup.stopLightCycle();
                    return reply(context, "Light cycle stopped; current light retained.");
                }))
                .then(Commands.literal("reset").executes(context -> {
                    ExampleStressMeshGroup.resetLightCycle();
                    return reply(context, "Light cycle stopped; spawn-time light restored.");
                })));
        event.getDispatcher().register(Commands.literal("sbmmesh")
                .executes(context -> reply(context, "/sbmmesh enabled <true|false> | path <auto|instance|section>"
                        + " | stats [detail] | cache clear | stress <same|models|all|dynamic> [count] | stress clear"
                        + " | stress light start [intervalTicks] [batchSize] | stress light stop|reset"))
                .then(Commands.literal("enabled")
                        .then(Commands.argument("value", BoolArgumentType.bool()).executes(context -> {
                            boolean value = BoolArgumentType.getBool(context, "value");
                            WorldMeshRenderer.setEnabled(value);
                            return reply(context, "World mesh enabled=" + value);
                        })))
                .then(Commands.literal("path")
                        .then(Commands.literal("auto").executes(context -> path(context, StrategyOverride.AUTO)))
                        .then(Commands.literal("instance").executes(context -> path(context, StrategyOverride.INSTANCE)))
                        .then(Commands.literal("section").executes(context -> path(context, StrategyOverride.SECTION))))
                .then(Commands.literal("stats").executes(context -> report(context, false))
                        .then(Commands.literal("detail").executes(context -> report(context, true))))
                .then(Commands.literal("cache").then(Commands.literal("clear").executes(context -> {
                    WorldMeshRenderer.clearCaches();
                    return reply(context, "Mesh caches invalidated; group objects retained.");
                })))
                .then(stress));
    }

    private static int stress(CommandContext<CommandSourceStack> context, ExampleStressMeshGroup.Mode mode, int count) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            context.getSource().sendFailure(Component.literal("No client player."));
            return 0;
        }
        // 生成静态或动态对照都启用世界层，保证生命周期与阶段间隔统计继续工作。
        WorldMeshRenderer.setEnabled(true);
        int spawned = ExampleStressMeshGroup.spawn(mode, count, player);
        return reply(context, "Stress " + mode.name().toLowerCase(Locale.ROOT) + ": " + spawned + "/" + count);
    }

    private static int lightCycle(CommandContext<CommandSourceStack> context, int interval, int batchSize) {
        if (!ExampleStressMeshGroup.startLightCycle(interval, batchSize)) {
            context.getSource().sendFailure(Component.literal("Spawn stress instances before starting the light cycle."));
            return 0;
        }
        return reply(context, "Light cycle: every " + interval + " client ticks, "
                + (batchSize == 0 ? "all instances" : batchSize + " instances per round") + ".");
    }

    private static int path(CommandContext<CommandSourceStack> context, StrategyOverride strategy) {
        WorldMeshRenderer.setEnabled(true);
        WorldMeshRenderer.setStrategy(strategy);
        reply(context, "Configured strategy=" + strategy);
        for (var group : WorldMeshRenderer.groupStats()) {
            reply(context, group.id() + ": " + group.effectiveStrategy() + " (" + group.policySource() + ") " + group.warnings());
        }
        return 1;
    }

    private static int report(CommandContext<CommandSourceStack> context, boolean detail) {
        WorldMeshStats stats = WorldMeshRenderer.stats();
        WorldMeshStats.Frame frame = stats.frame();
        reply(context, String.format(Locale.ROOT,
                "World mesh enabled=%s, groups=%d, meshes=%d | draws=%d, materials=%d, culled=%d, pending=%d, failed=%d"
                        + " | frameMs=%.2f, cpuUs=%.1f, rangeUploads=%d, rangeBytes=%d",
                stats.enabled(), stats.groups(), stats.meshes(), frame.draws(), frame.materials(), frame.culled(),
                frame.pending(), frame.failed(), frame.intervalMillis(), frame.cpuMicros(),
                frame.lightRangeUploads(), frame.lightRangeBytes()));
        if (detail) {
            reply(context, "Lifetime " + stats.totals() + " " + stats.pool());
            for (var group : WorldMeshRenderer.groupStats()) reply(context, group.toString());
            reply(context, ExampleStressMeshGroup.stats());
        }
        return 1;
    }

    private static int reply(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }
}
