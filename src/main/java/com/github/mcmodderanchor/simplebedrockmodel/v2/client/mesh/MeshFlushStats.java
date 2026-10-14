package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh;

/** 单次 flush 的增量统计；endBatch 只返回最后一次排空的统计。 */
public record MeshFlushStats(int queuedItems, int drawnParts, int materialSetups,
                             int bufferBinds, int discardedItems) {}
