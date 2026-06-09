package com.github.mcmodderanchor.simplebedrockmodel.v2.resource;

import com.github.mcmodderanchor.simplebedrockmodel.v1.resource.RawResourceLoader;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakerOptions;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Function;

public record BedrockModelEntry(
        RawResourceLoader rawLoader,
        ResourceLocation sourceId,
        ModelType kind,
        Function<BedrockModelBakeContext, BakerOptions> optionsFactory,
        List<ResourceLocation> animationSourceIds,
        boolean lazy
) {
    public BedrockModelEntry {
        animationSourceIds = List.copyOf(animationSourceIds);
    }
}
