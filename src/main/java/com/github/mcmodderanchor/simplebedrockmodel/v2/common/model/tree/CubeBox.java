package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.Arrays;

public record CubeBox(
        float x,
        float y,
        float z,
        float width,
        float height,
        float depth,
        float inflate,
        float[] uvs,
        int[][] uvOrder,
        float @Nullable [] pivot,
        @Nullable Quaternionf rotation
) implements ICube {
    public CubeBox {
        uvs = uvs == null ? new float[9] : Arrays.copyOf(uvs, 9);
        int[][] orderCopy = new int[6][4];
        if (uvOrder != null) {
            int faceCount = Math.min(uvOrder.length, orderCopy.length);
            for (int i = 0; i < faceCount; i++) {
                if (uvOrder[i] != null) System.arraycopy(uvOrder[i], 0, orderCopy[i], 0, Math.min(uvOrder[i].length, 4));
            }
        }
        uvOrder = orderCopy;
        pivot = pivot == null ? null : Arrays.copyOf(pivot, 3);
        rotation = rotation == null ? null : new Quaternionf(rotation);
    }

    @Override
    public float[] uvs() {
        return Arrays.copyOf(uvs, uvs.length);
    }

    @Override
    public int[][] uvOrder() {
        int[][] copy = new int[uvOrder.length][];
        for (int i = 0; i < uvOrder.length; i++) copy[i] = Arrays.copyOf(uvOrder[i], uvOrder[i].length);
        return copy;
    }

    public float uv(int index) {
        return uvs[index];
    }

    public int uvOrder(int face, int index) {
        return uvOrder[face][index];
    }
}
