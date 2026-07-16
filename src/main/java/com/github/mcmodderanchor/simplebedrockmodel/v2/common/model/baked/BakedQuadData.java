package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked;

public record BakedQuadData(
        float[] positions,
        float[] normals,
        float[] uvs,
        int quadCount
) {
    public static final int POSITION_STRIDE = 12;
    public static final int NORMAL_STRIDE = 3;
    public static final int UV_STRIDE = 8;

    public static final BakedQuadData EMPTY = new BakedQuadData(new float[0], new float[0], new float[0], 0);

    public BakedQuadData {
        positions = positions.clone();
        normals = normals.clone();
        uvs = uvs.clone();
    }
}
