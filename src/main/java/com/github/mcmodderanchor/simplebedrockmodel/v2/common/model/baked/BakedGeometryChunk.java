package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.compat.acceleratedrendering.AcceleratedBedrockGeometryCache;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.ApiStatus;

public final class BakedGeometryChunk {
    private final int attachBoneIndex;
    private final BakedQuadData quads;
    private final BakedVertexData vertices;
    private final String[] sourceBones;

    @OnlyIn(Dist.CLIENT)
    private AcceleratedBedrockGeometryCache cache;

    public BakedGeometryChunk(int attachBoneIndex, BakedQuadData quads, BakedVertexData vertices, String[] sourceBones) {
        this.attachBoneIndex = attachBoneIndex;
        this.quads = quads == null ? BakedQuadData.EMPTY : quads;
        this.vertices = vertices == null ? BakedVertexData.EMPTY : vertices;
        this.sourceBones = sourceBones.clone();
    }

    public int attachBoneIndex() {
        return attachBoneIndex;
    }

    public BakedQuadData quads() {
        return quads;
    }

    public BakedVertexData vertices() {
        return vertices;
    }

    @ApiStatus.Internal
    @OnlyIn(Dist.CLIENT)
    public AcceleratedBedrockGeometryCache getOrCreateCache() {
        if (cache == null) {
            cache = new AcceleratedBedrockGeometryCache();
        }
        return cache;
    }

    public String[] sourceBones() {
        return sourceBones.clone();
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
