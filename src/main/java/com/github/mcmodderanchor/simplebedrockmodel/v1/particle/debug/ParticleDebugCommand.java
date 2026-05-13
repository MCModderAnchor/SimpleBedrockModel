package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.debug;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleEffectDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.resource.ParticleDefinitionLoader;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world.WorldEmitterManager;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

@EventBusSubscriber(modid = SimpleBedrockModel.MOD_ID, value = Dist.CLIENT)
public class ParticleDebugCommand {
    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        var particleCommand = literal("particle")
                .then(literal("spawn")
                    .then(argument("effect", ResourceLocationArgument.id())
                        .suggests(ParticleDebugCommand::suggestParticleEffects)
                        .then(argument("pos", Vec3Argument.vec3(false))
                            .executes(ParticleDebugCommand::spawnEmitter)
                            .then(argument("velocity", Vec3Argument.vec3(false))
                                .executes(ParticleDebugCommand::spawnEmitter)))))
                .then(literal("stress")
                    .then(argument("effect", ResourceLocationArgument.id())
                        .suggests(ParticleDebugCommand::suggestParticleEffects)
                        .then(argument("pos", Vec3Argument.vec3(false))
                            .then(argument("count", IntegerArgumentType.integer(1))
                                .executes(ParticleDebugCommand::spawnEmitterStress)
                                .then(argument("spacing", DoubleArgumentType.doubleArg(0.0D))
                                    .executes(ParticleDebugCommand::spawnEmitterStress)
                                    .then(argument("velocity", Vec3Argument.vec3(false))
                                        .executes(ParticleDebugCommand::spawnEmitterStress)))))));

        event.getDispatcher().register(literal("sbm").then(particleCommand));
    }

    private static CompletableFuture<Suggestions> suggestParticleEffects(CommandContext<?> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(
                ParticleDefinitionLoader.getInstance().getAllDefinitions().keySet(),
                builder
        );
    }

    private static int spawnEmitter(CommandContext<CommandSourceStack> context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            sendMessage(Component.literal("[SBM] 当前没有可用的客户端世界"), true);
            return 0;
        }

        ParticleEffectDefinition definition = resolveDefinition(context);
        if (definition == null) {
            return 0;
        }

        ResourceLocation effectId = ResourceLocationArgument.getId(context, "effect");
        Vec3 pos = Vec3Argument.getVec3(context, "pos");
        Vec3 velocity = hasArgument(context, "velocity")
                ? Vec3Argument.getVec3(context, "velocity")
                : Vec3.ZERO;

        WorldEmitterManager.getInstance().addEmitter(mc.level, pos, velocity, definition);
        sendMessage(Component.literal("[SBM] 已生成粒子发射器: " + effectId + " @ "
                + formatVec3(pos) + " vel=" + formatVec3(velocity)), false);
        return 1;
    }

    private static int spawnEmitterStress(CommandContext<CommandSourceStack> context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            sendMessage(Component.literal("[SBM] 当前没有可用的客户端世界"), true);
            return 0;
        }

        ParticleEffectDefinition definition = resolveDefinition(context);
        if (definition == null) {
            return 0;
        }

        ResourceLocation effectId = ResourceLocationArgument.getId(context, "effect");
        Vec3 center = Vec3Argument.getVec3(context, "pos");
        int count = IntegerArgumentType.getInteger(context, "count");
        double spacing = hasArgument(context, "spacing")
                ? DoubleArgumentType.getDouble(context, "spacing")
                : 1.0D;
        Vec3 velocity = hasArgument(context, "velocity")
                ? Vec3Argument.getVec3(context, "velocity")
                : Vec3.ZERO;

        int side = (int) Math.ceil(Math.cbrt(count));
        double centerOffset = (side - 1) / 2.0D;
        WorldEmitterManager emitterManager = WorldEmitterManager.getInstance();

        for (int i = 0; i < count; i++) {
            int xIndex = i % side;
            int zIndex = (i / side) % side;
            int yIndex = i / (side * side);

            Vec3 emitterPos = center.add(
                    (xIndex - centerOffset) * spacing,
                    (yIndex - centerOffset) * spacing,
                    (zIndex - centerOffset) * spacing
            );
            emitterManager.addEmitter(mc.level, emitterPos, velocity, definition);
        }

        sendMessage(Component.literal("[SBM] 已为性能测试生成 " + count + " 个粒子发射器: " + effectId
                + " center=" + formatVec3(center)
                + " spacing=" + String.format(Locale.ROOT, "%.3f", spacing)
                + " vel=" + formatVec3(velocity)), false);
        return count;
    }

    private static ParticleEffectDefinition resolveDefinition(CommandContext<CommandSourceStack> context) {
        ResourceLocation effectId = ResourceLocationArgument.getId(context, "effect");
        if (effectId == null) {
            sendMessage(Component.literal("[SBM] 无效的粒子效果 ID: " + effectId), true);
            return null;
        }

        ParticleEffectDefinition definition = ParticleDefinitionLoader.getInstance().getDefinition(effectId);
        if (definition == null) {
            sendMessage(Component.literal("[SBM] 找不到粒子效果定义: " + effectId), true);
            return null;
        }
        return definition;
    }

    private static boolean hasArgument(CommandContext<CommandSourceStack> context, String name) {
        return context.getNodes().stream().anyMatch(node -> name.equals(node.getNode().getName()));
    }

    private static String formatVec3(Vec3 vec) {
        return String.format(Locale.ROOT, "%.3f, %.3f, %.3f", vec.x, vec.y, vec.z);
    }

    private static void sendMessage(Component message, boolean overlay) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(message, overlay);
        }
    }
}
