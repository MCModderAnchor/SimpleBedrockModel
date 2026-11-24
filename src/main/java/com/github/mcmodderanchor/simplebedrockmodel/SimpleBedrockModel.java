package com.github.mcmodderanchor.simplebedrockmodel;

import com.github.mcmodderanchor.simplebedrockmodel.v1.network.NetworkHandler;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world.WorldParticleConfig;
import net.minecraft.resources.ResourceLocation;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.config.WorldMeshConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(SimpleBedrockModel.MOD_ID)
public class SimpleBedrockModel {
    public static final String MOD_ID = "simplebedrockmodel";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public SimpleBedrockModel() {
        NetworkHandler.init();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, WorldMeshConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, WorldParticleConfig.SPEC,
                MOD_ID + "-world-particles-client.toml");
    }

    @SuppressWarnings("removal")
    public static ResourceLocation modLoc(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
