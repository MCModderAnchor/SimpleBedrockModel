package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.bake;

public record BakedGeometryChunk(
        int attachBoneIndex,
        BakedQuadData quads,
        BakedVertexData vertices,
        String[] sourceBones
) {
    public BakedGeometryChunk {
        quads = quads == null ? BakedQuadData.EMPTY : quads;
        vertices = vertices == null ? BakedVertexData.EMPTY : vertices;
        sourceBones = sourceBones.clone();
    }

    public int quadCount() {
        return quads.quadCount();
    }

    public int vertexCount() {
        return quads.quadCount() * 4 + vertices.vertexCount();
    }

    public boolean hasQuads() {
        return quads.quadCount() > 0;
    }

    public boolean hasVertices() {
        return vertices.vertexCount() > 0;
    }

    public boolean isRootAttached() {
        return attachBoneIndex < 0;
    }
}
