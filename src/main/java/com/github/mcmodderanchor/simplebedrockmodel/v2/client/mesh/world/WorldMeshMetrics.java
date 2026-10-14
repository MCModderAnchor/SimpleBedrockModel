package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.VertexBufferPool;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.WorldMeshPart;

/** 渲染线程上的可变计数与帧采样；对外只返回不可变的 {@link WorldMeshStats} 快照。 */
public class WorldMeshMetrics {
    private static final int FRAME_SAMPLES = 120;

    private int submits;
    private int releases;
    private int lightBuffers;
    private int fixedMeshes;
    private int uniformMeshes;
    private int mutableMeshes;
    private long lightRangeUploads;
    private long lightRangeBytes;
    private int formatChanges;

    private int visible;
    private int draws;
    private int materials;
    private int culled;
    private int pending;
    private int failed;
    private int lightRangeUploadsThisFrame;
    private long lightRangeBytesThisFrame;
    private double cpuMicros;

    private final long[] frameDeltas = new long[FRAME_SAMPLES];
    private int frameSampleIndex;
    private int frameSampleCount;
    private long lastFrameNanos;

    public void recordSubmit(WorldMeshPart.LightMode mode) {
        this.submits++;
        switch (mode) {
            case FIXED -> this.fixedMeshes++;
            case UNIFORM -> this.uniformMeshes++;
            case MUTABLE -> this.mutableMeshes++;
        }
    }

    public void recordRelease() { this.releases++; }
    public void recordLightBufferCreated() { this.lightBuffers++; }
    public void recordLightBufferReleased() { this.lightBuffers--; }
    public void recordFormatChange() { this.formatChanges++; }

    public void recordLightRangeUpload(int bytes) {
        this.lightRangeUploads++;
        this.lightRangeUploadsThisFrame++;
        this.lightRangeBytes += bytes;
        this.lightRangeBytesThisFrame += bytes;
    }

    public void beginFrame(long now) {
        if (this.lastFrameNanos != 0L) {
            this.frameDeltas[this.frameSampleIndex] = now - this.lastFrameNanos;
            this.frameSampleIndex = (this.frameSampleIndex + 1) % FRAME_SAMPLES;
            if (this.frameSampleCount < FRAME_SAMPLES) this.frameSampleCount++;
        }
        this.lastFrameNanos = now;
        this.cpuMicros = 0.0;
        this.lightRangeUploadsThisFrame = 0;
        this.lightRangeBytesThisFrame = 0;
    }

    public void recordPrepared(int visible, int culled, int pending, int failed) {
        this.visible = visible;
        this.culled = culled;
        this.pending = pending;
        this.failed = failed;
    }

    public void endFrame(int draws, int materials, long passStartNanos) {
        this.draws = draws;
        this.materials = materials;
        this.cpuMicros = (System.nanoTime() - passStartNanos) / 1000.0;
    }

    /** 失效时只清帧数据；累计计数与当前光照缓冲数保留。 */
    public void clearFrame() {
        this.visible = this.draws = this.materials = this.culled = this.pending = this.failed = 0;
        this.lightRangeUploadsThisFrame = 0;
        this.lightRangeBytesThisFrame = 0;
        this.frameSampleIndex = this.frameSampleCount = 0;
        this.lastFrameNanos = 0L;
        this.cpuMicros = 0.0;
    }

    public WorldMeshStats snapshot(boolean enabled, int groups, int meshes, VertexBufferPool pool) {
        return new WorldMeshStats(enabled, groups, meshes,
                new WorldMeshStats.Frame(this.visible, this.draws, this.materials, this.culled,
                        this.pending, this.failed, this.lightRangeUploadsThisFrame,
                        this.lightRangeBytesThisFrame, averageFrameMillis(), this.cpuMicros),
                new WorldMeshStats.Totals(this.submits, this.releases, this.lightBuffers,
                        this.fixedMeshes, this.uniformMeshes, this.mutableMeshes,
                        this.lightRangeUploads, this.lightRangeBytes, this.formatChanges),
                new WorldMeshStats.Pool(pool.created(), pool.reused(), pool.idleCount()));
    }

    /** 相邻两次 AFTER_BLOCK_ENTITIES 的间隔均值，不是 GPU 时间。 */
    public double averageFrameMillis() {
        if (this.frameSampleCount == 0) return 0.0;
        long total = 0L;
        for (int i = 0; i < this.frameSampleCount; i++) total += this.frameDeltas[i];
        return total / 1_000_000.0 / this.frameSampleCount;
    }
}
