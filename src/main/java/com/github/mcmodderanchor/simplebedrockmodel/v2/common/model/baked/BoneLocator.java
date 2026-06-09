package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.LocatorData;

public record BoneLocator(
        String name,
        int boneIndex,
        LocatorData data
) {
}
