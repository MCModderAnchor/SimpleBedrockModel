package com.github.mcmodderanchor.simplebedrockmodel.v2.resource;

import com.github.mcmodderanchor.simplebedrockmodel.v1.resource.RawResourceLoader;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.bake.BakerOptions;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Function;

public record BedrockModelEntry(
        RawResourceLoader rawLoader,
        Function<BedrockModelBakeContext, BakerOptions> optionsFactory,
        List<ResourceLocation> animationSourceIds,
        boolean lazy,
        boolean preserveLegacyArmorCopy
) {
    public BedrockModelEntry {
        animationSourceIds = List.copyOf(animationSourceIds);
    }
}
