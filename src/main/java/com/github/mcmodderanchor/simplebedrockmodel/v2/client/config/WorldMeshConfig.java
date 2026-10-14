package com.github.mcmodderanchor.simplebedrockmodel.v2.client.config;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world.StrategyOverride;
import net.neoforged.neoforge.common.ModConfigSpec;

/** 无客户端游戏类依赖，允许在模组构造阶段注册 CLIENT 配置。 */
public final class WorldMeshConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<StrategyOverride> STRATEGY;
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("worldMesh");
        STRATEGY = builder.comment("Strategy for supported world mesh groups: AUTO, INSTANCE, SECTION. Commands update this setting.")
                .defineEnum("strategy", StrategyOverride.AUTO);
        builder.pop();
        SPEC = builder.build();
    }
    private WorldMeshConfig() {}
}
