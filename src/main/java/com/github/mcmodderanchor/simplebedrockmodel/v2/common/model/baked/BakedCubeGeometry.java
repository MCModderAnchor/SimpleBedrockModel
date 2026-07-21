package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Retained cube geometry associated with one folded runtime attachment bone.
 */
public record BakedCubeGeometry(
        int attachBoneIndex,
        @Nullable LocalCubeBounds bounds,
        BakedCube[] cubes
) {
    public BakedCubeGeometry {
        cubes = Objects.requireNonNull(cubes, "cubes").clone();
        if (cubes.length > 0 && bounds == null) {
            throw new IllegalArgumentException("bounds is required when cubes are present");
        }
    }

    @Override
    public BakedCube[] cubes() {
        return cubes.clone();
    }
}
