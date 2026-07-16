package com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.epicfight;

import net.neoforged.fml.ModList;
import yesman.epicfight.api.client.model.transformer.HumanoidModelBaker;

public class EpicFightCompat {
    private static final String EPIC_FIGHT = "epicfight";
    private static boolean LOADED = false;

    public static void init() {
        if (ModList.get().isLoaded(EPIC_FIGHT)) {
            LOADED = true;
            EpicFightRegister.register();
        }
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static class EpicFightRegister {
        private static void register() {
            try {
                HumanoidModelBaker.registerNewTransformer(new BedrockArmorTransformer());

                // TODO EpicFight这个Event并非继承自NeoForge的Event，如何注册？
//                NeoForge.EVENT_BUS.addListener(BedrockArmorTransformer::getBedrockArmorTexturePath);
            } catch (Exception e) {
                throw new RuntimeException("Failed to register Epic Fight compatibility", e);
            }
        }
    }

}
