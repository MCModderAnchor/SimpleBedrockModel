package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world.GeometryCache;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/** 一个 GPU 网格的内部所有权记录；共享引用只在 GeometryCache.Entry 计数，句柄只释放一次。 */
@OnlyIn(Dist.CLIENT)
public class WorldMeshPart {
    private final WorldMeshBuffers owner;
    /**
     * 这个 shard 的光照怎么给。
     *
     * <ul>
     *   <li>{@link LightMode#FIXED}：所有顶点都是烘焙时的实际光照，UV2 直接读几何 buffer；</li>
     *   <li>{@link LightMode#UNIFORM}：所有顶点都跟随实例光照，UV2 从共享整数参数表读取，divisor=1；</li>
     *   <li>{@link LightMode#MUTABLE}：实际光照独立存储，由调用方按顶点范围更新。</li>
     * </ul>
     */
    public enum LightMode {
        FIXED,
        UNIFORM,
        MUTABLE
    }

    private static int nextId;

    private final int id;
    private final String ownerId;
    private final VertexBuffer buffer;
    private final VertexFormat format;
    private final RenderType material;
    private final AABB localBounds;
    private final long generation;
    private final CompletableFuture<Void> upload;
    /** 光照独立流；-1 表示无需额外缓冲（FIXED 或 UNIFORM）。 */
    private final int lightBufferId;
    /** MUTABLE 光照流的 CPU 暂存内容。 */
    private final ByteBuffer lightScratch;
    private final LightMode lightMode;
    private final MeshIntegerAttributes.LightState uniformLightState = new MeshIntegerAttributes.LightState();
    private final LightRangeUpdates lightUpdates;

    private boolean retired;
    private boolean dead;
    private boolean recycled;
    /**
     * 本句柄的几何 VAO 上，固定／可变 UV2 是否已按实际上传格式挂好。
     *
     * <p>几何上传（{@code VertexBuffer.upload} 在格式变化时会重新 setupBufferState）与池回收复用
     * 都会让这个前置条件失效，所以标志必须挂在句柄上、初值为 false。</p>
     */
    private boolean lightStreamAttached;

    public WorldMeshPart(WorldMeshBuffers owner, String ownerId, VertexBuffer buffer, VertexFormat format, RenderType material, AABB localBounds,
                long generation, CompletableFuture<Void> upload,
                int lightBufferId, ByteBuffer lightScratch, LightMode lightMode) {
        this.owner = owner;
        this.id = nextId++;
        this.ownerId = ownerId;
        this.buffer = buffer;
        this.format = format;
        this.material = material;
        this.localBounds = localBounds;
        this.generation = generation;
        this.upload = upload;
        this.lightBufferId = lightBufferId;
        this.lightScratch = lightScratch;
        this.lightMode = lightMode;
        this.lightUpdates = lightMode == LightMode.MUTABLE ? new LightRangeUpdates(lightScratch) : null;
    }

    /** 单调递增的句柄号，供库内排序（分组绘制）与统计使用。 */
    public int id() {
        return this.id;
    }

    public RenderType material() {
        return this.material;
    }

    public AABB localBounds() {
        return this.localBounds;
    }

    /**
     * 修改 MUTABLE 网格的实际光照；范围单位为顶点，零光照表示黑暗。
     * 渲染线程调用，可在几何上传尚未完成时暂存，首次可见绘制前合并上传脏范围。
     * 本操作影响共享此句柄的所有实例；调用方负责排除自发光等固定范围。
     * @return CPU 暂存光照是否实际发生变化
     */
    public boolean updateLight(int firstVertex, int vertexCount, int packedLight) {
        RenderSystem.assertOnRenderThread();
        if (!isAlive() || uploadFailed()) {
            throw new IllegalStateException("Cannot update a released or failed shard");
        }
        if (this.lightUpdates == null) {
            throw new IllegalStateException("Light range updates require MeshLighting.MUTABLE");
        }
        return this.lightUpdates.set(firstVertex, vertexCount, packedLight);
    }

    public LightRangeUpdates lightUpdates() {
        return this.lightUpdates;
    }

    public long generation() {
        return this.generation;
    }

    /** 是否有独立光照流；全固定和全实例光照都不需要此缓冲。 */
    public boolean hasLightBuffer() {
        return this.lightBufferId >= 0;
    }

    public LightMode lightMode() {
        return this.lightMode;
    }

    public MeshIntegerAttributes.LightState uniformLightState() {
        return this.uniformLightState;
    }

    public int lightBufferId() {
        return this.lightBufferId;
    }

    public ByteBuffer lightScratch() {
        return this.lightScratch;
    }

    /** 上传已成功完成；实际使用前还需检查 isAlive()。 */
    public boolean isUploaded() {
        return this.upload.isDone() && !this.upload.isCompletedExceptionally();
    }

    public boolean uploadFailed() {
        return this.upload.isCompletedExceptionally();
    }

    /** 是否仍可用：既没有硬失效，也没有被所有者释放。 */
    public boolean isAlive() {
        return !this.dead && !this.retired;
    }

    /** 所有者不再使用时释放；重复释放无操作，上传结束后再回收。 */
    public void release() {
        this.owner.release(this);
    }

    public String ownerId() {
        return this.ownerId;
    }

    public VertexBuffer buffer() {
        return this.buffer;
    }

    public VertexFormat format() {
        return this.format;
    }

    public CompletableFuture<Void> upload() {
        return this.upload;
    }

    public boolean retire() {
        if (this.dead || this.retired) return false;
        this.retired = true;
        return true;
    }

    public boolean isInvalidated() {
        return this.dead;
    }

    public boolean markRecycled() {
        if (this.recycled) {
            return false;
        }
        this.recycled = true;
        return true;
    }

    public boolean isLightStreamAttached() {
        return this.lightStreamAttached;
    }

    public void markLightStreamAttached() {
        this.lightStreamAttached = true;
    }

    public void kill() {
        this.dead = true;
        this.retired = true;
    }
}
