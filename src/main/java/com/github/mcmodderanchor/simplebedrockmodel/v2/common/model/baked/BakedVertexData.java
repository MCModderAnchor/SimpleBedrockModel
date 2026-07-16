package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked;

public record BakedVertexData(
        float[] positions,
        float[] normals,
        float[] uvs,
        int vertexCount
) {
    public static final int POSITION_STRIDE = 3;
    public static final int NORMAL_STRIDE = 3;
    public static final int UV_STRIDE = 2;

    public static final BakedVertexData EMPTY = new BakedVertexData(new float[0], new float[0], new float[0], 0);

    public BakedVertexData {
        positions = positions.clone();
        normals = normals.clone();
        uvs = uvs.clone();
    }
}
