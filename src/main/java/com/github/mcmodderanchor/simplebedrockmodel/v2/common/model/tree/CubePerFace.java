package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.Arrays;

public record CubePerFace(
        float x,
        float y,
        float z,
        float width,
        float height,
        float depth,
        float inflate,
        float[][] uvs,
        int emptyFacesMask,
        float @Nullable [] pivot,
        @Nullable Quaternionf rotation
) implements ICube {
    public CubePerFace {
        float[][] uvCopy = new float[6][8];
        if (uvs != null) {
            int faceCount = Math.min(uvs.length, uvCopy.length);
            for (int i = 0; i < faceCount; i++) {
                if (uvs[i] != null) System.arraycopy(uvs[i], 0, uvCopy[i], 0, Math.min(uvs[i].length, 8));
            }
        }
        uvs = uvCopy;
        pivot = pivot == null ? null : Arrays.copyOf(pivot, 3);
        rotation = rotation == null ? null : new Quaternionf(rotation);
    }

    @Override
    public float[][] uvs() {
        float[][] copy = new float[uvs.length][];
        for (int i = 0; i < uvs.length; i++) copy[i] = Arrays.copyOf(uvs[i], uvs[i].length);
        return copy;
    }

    public float[] faceUv(int face) {
        return uvs[face];
    }

    public boolean isEmptyFace(int face) {
        return (emptyFacesMask & (1 << face)) != 0;
    }
}
