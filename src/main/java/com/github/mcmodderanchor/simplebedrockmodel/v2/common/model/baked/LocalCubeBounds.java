package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;

import java.util.Objects;

/**
 * An axis-aligned cube bounds in a bone or attachment-local coordinate space.
 */
public record LocalCubeBounds(
        float minX, float minY, float minZ,
        float maxX, float maxY, float maxZ
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;

        public Builder include(float x, float y, float z) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            return this;
        }

        public Builder includeCube(float x, float y, float z, float width, float height, float depth, Matrix4fc transform) {
            Objects.requireNonNull(transform, "transform");
            float maxCubeX = x + width;
            float maxCubeY = y + height;
            float maxCubeZ = z + depth;
            includeTransformed(x, y, z, transform);
            includeTransformed(maxCubeX, y, z, transform);
            includeTransformed(maxCubeX, maxCubeY, z, transform);
            includeTransformed(x, maxCubeY, z, transform);
            includeTransformed(x, y, maxCubeZ, transform);
            includeTransformed(maxCubeX, y, maxCubeZ, transform);
            includeTransformed(maxCubeX, maxCubeY, maxCubeZ, transform);
            includeTransformed(x, maxCubeY, maxCubeZ, transform);
            return this;
        }

        @Nullable
        public LocalCubeBounds build() {
            return minX == Float.POSITIVE_INFINITY ? null : new LocalCubeBounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        private void includeTransformed(float x, float y, float z, Matrix4fc transform) {
            include(
                    transform.m00() * x + transform.m10() * y + transform.m20() * z + transform.m30(),
                    transform.m01() * x + transform.m11() * y + transform.m21() * z + transform.m31(),
                    transform.m02() * x + transform.m12() * y + transform.m22() * z + transform.m32()
            );
        }
    }
}
