package com.github.mcmodderanchor.simplebedrockmodel.v2.resource;

import com.github.mcmodderanchor.simplebedrockmodel.v1.resource.RawResourceLoader;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record BedrockAnimationEntry(
        RawResourceLoader rawLoader,
        @Nullable ResourceLocation modelId,
        @Nullable BedrockAnimationFactory factory,
        boolean lazy,
        boolean createRuntimeAnimations
) {
}
