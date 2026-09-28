package com.github.mcmodderanchor.simplebedrockmodel.v2.client.config;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.world.StrategyOverride;
import net.minecraftforge.common.ForgeConfigSpec;

/** 无客户端游戏类依赖，允许在模组构造阶段注册 CLIENT 配置。 */
public final class WorldMeshConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<StrategyOverride> STRATEGY;
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("worldMesh");
        STRATEGY = builder.comment("Strategy for supported world mesh groups: AUTO, INSTANCE, SECTION. Commands update this setting.")
                .defineEnum("strategy", StrategyOverride.AUTO);
        builder.pop();
        SPEC = builder.build();
    }
    private WorldMeshConfig() {}
}
