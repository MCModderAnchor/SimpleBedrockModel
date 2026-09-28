package com.github.mcmodderanchor.simplebedrockmodel.v2.client.world;

/** 按需获取的不可变统计快照；库不负责聊天文本或日志格式。 */
public record WorldMeshStats(boolean enabled, int groups, int meshes, Frame frame, Totals totals, Pool pool) {
    /**
     * 上次绘制阶段的数据；intervalMillis 为最多 120 次阶段间隔均值，cpuMicros 不是 GPU 计时。
     * lightRangeUploads/Bytes 只统计 MUTABLE 脏范围的实际 GL 上传，不含创建时的整流分配。
     */
    public record Frame(int visible, int draws, int materials, int culled, int pending, int failed,
                        int lightRangeUploads, long lightRangeBytes,
                        double intervalMillis, double cpuMicros) {
    }

    /** 累计计数不随失效清零；lightBuffers 是当前分配数，其余光照模式数为累计提交数。 */
    public record Totals(int submits, int releases, int lightBuffers,
                         int fixedMeshes, int uniformMeshes, int mutableMeshes,
                         long lightRangeUploads, long lightRangeBytes, int formatChanges) {
    }

    /** created/reused 为累计计数，idle 为当前空闲缓冲数。 */
    public record Pool(int created, int reused, int idle) {
    }
}
