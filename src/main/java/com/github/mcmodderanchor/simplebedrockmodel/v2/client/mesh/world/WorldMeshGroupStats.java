package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world;

/** 渲染组状态快照；覆盖被拒绝时 warnings 会说明原因及实际使用策略。 */
public record WorldMeshGroupStats(String id, WorldMeshStrategy defaultStrategy, WorldMeshStrategy effectiveStrategy,
                               String policySource, String warnings, int objects, int visibleObjects,
                               int cachedGeometries, long captures, long captureFailures, Section section) {
    public record Section(int batches, int active, int dirty, int pending, int waitingGeometry, long builds, long submittedVertices,
                          long lightChanges, int failures, double prepareMicros) {}
}
