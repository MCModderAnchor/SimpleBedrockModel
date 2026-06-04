package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

public sealed interface ICube permits CubeBox, CubePerFace {
    float x();

    float y();

    float z();

    float width();

    float height();

    float depth();

    float inflate();

    float @Nullable [] pivot();

    @Nullable Quaternionf rotation();

    default boolean hasRotation() {
        return pivot() != null && rotation() != null;
    }
}
