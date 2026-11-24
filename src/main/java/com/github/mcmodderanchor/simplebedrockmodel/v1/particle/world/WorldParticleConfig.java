package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client settings without dependencies on Minecraft client classes. */
public class WorldParticleConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue COMPUTE;

    static {
        var builder = new ForgeConfigSpec.Builder();
        COMPUTE = builder.comment("Expand supported world particle batches with an OpenGL compute shader.")
                .define("compute", false);
        SPEC = builder.build();
    }

    private WorldParticleConfig() {}
}
