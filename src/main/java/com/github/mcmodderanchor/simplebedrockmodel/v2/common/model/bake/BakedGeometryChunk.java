package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.bake;

import java.util.concurrent.atomic.AtomicInteger;

// 应该用底下那个自动分配id的，不要调这个默认构造器
public record BakedGeometryChunk(
        int attachBoneIndex,
        BakedQuadData quads,
        BakedVertexData vertices,
        String[] sourceBones,
        int id
) {
    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    public BakedGeometryChunk(int attachBoneIndex, BakedQuadData quads, BakedVertexData vertices, String[] sourceBones) {
        this(attachBoneIndex, quads, vertices, sourceBones, NEXT_ID.getAndIncrement());
    }

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
