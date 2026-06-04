package com.github.mcmodderanchor.simplebedrockmodel.v2.resource;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation.BedrockAnimation;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockAnimationFile;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedBedrockModel;

import java.util.List;

@FunctionalInterface
public interface BedrockAnimationFactory {
    List<BedrockAnimation> create(BedrockAnimationFile animationFile, BakedBedrockModel model);
}
