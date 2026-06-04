package com.github.mcmodderanchor.simplebedrockmodel.v2.resource;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockAnimationFile;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockModelPOJO;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakerOptions;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record BedrockModelBakeContext(
        ResourceLocation modelId,
        BedrockModelPOJO modelPojo,
        List<BedrockAnimationFile> animationFiles
) {
    public BedrockModelBakeContext {
        animationFiles = List.copyOf(animationFiles);
    }

    public Set<String> collectAnimatedBones() {
        LinkedHashSet<String> bones = new LinkedHashSet<>();
        for (BedrockAnimationFile animationFile : animationFiles) {
            bones.addAll(BakerOptions.collectAnimatedBones(animationFile));
        }
        return Set.copyOf(bones);
    }

    public BakerOptions optionsFromAnimations() {
        Collection<String> animatedBones = collectAnimatedBones();
        if (animatedBones.isEmpty()) {
            return BakerOptions.defaults();
        }
        return BakerOptions.ofAnimatedBones(Set.copyOf(animatedBones));
    }
}
