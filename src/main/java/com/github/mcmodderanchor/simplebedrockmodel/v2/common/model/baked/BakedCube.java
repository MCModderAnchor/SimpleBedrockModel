package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked;

import org.joml.Matrix4f;

import java.util.Objects;

/**
 * Retained local cube geometry for a baked attachment group.
 */
public record BakedCube(
        float x, float y, float z,
        float width, float height, float depth,
        Matrix4f localTransform
) {
    public BakedCube {
        localTransform = new Matrix4f(Objects.requireNonNull(localTransform, "localTransform"));
    }

    @Override
    public Matrix4f localTransform() {
        return new Matrix4f(localTransform);
    }
}
