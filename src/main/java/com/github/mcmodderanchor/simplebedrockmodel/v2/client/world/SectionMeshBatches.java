package com.github.mcmodderanchor.simplebedrockmodel.v2.client.world;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库内 section 合批。每个 (16³ section, RenderType, mode) 一个网格，顶点含 section 内位移及实际光照。
 * 几何变化重建受影响批次，光照变化只暂存该实例的 UV2 范围；上传期间继续画旧版。
 * 不接入地形 chunk layer，不进行 VBO 子分配，也不承担透明排序。
 */
@OnlyIn(Dist.CLIENT)
public class SectionMeshBatches<K> {
    private static final int MAX_BUILDS_PER_FRAME = 4;
    // 软预算：不在单个模型/批次内部中断，所以一个超大批次仍可能超时。
    private static final long BUILD_BUDGET_NANOS = 2_000_000L;

    private record Member(GeometryCache.Entry geometry, Vec3 origin, Matrix4f transform, int light) {
        boolean sameGeometry(Member other) {
            return this.geometry.key.equals(other.geometry.key) && this.origin.equals(other.origin)
                    && this.transform.equals(other.transform);
        }
    }

    private record PendingMember(GeometryCache.Entry geometry, Vec3 origin, Matrix4f transform, int light) {}

    private record LightRange(int firstVertex, int vertexCount) {
    }

    /** 范围属于某一版几何，不能用新成员顺序去更新旧 VBO。 */
    private static final class LightSlice {
        final Member captured;
        final List<LightRange> ranges;
        int appliedLight = Integer.MIN_VALUE;

        LightSlice(Member captured, List<LightRange> ranges) {
            this.captured = captured;
            this.ranges = ranges;
        }
    }

    private final class Version {
        final ShardHandle handle;
        final Map<K, LightSlice> slices;

        Version(ShardHandle handle, Map<K, LightSlice> slices) {
            this.handle = handle;
            this.slices = slices;
        }
    }

    private record BatchKey(Vec3 origin, GeometryCollector.Pass pass) {
    }

    private final class Batch {
        final BatchKey key;
        final Map<K, Member> members = new LinkedHashMap<>();
        Version active;
        Version pending;
        boolean dirty;

        Batch(BatchKey key) {
            this.key = key;
        }
    }

    private final GeometryCache cache;

    SectionMeshBatches(GeometryCache cache) { this.cache = cache; }

    private final Map<K, Member> members = new HashMap<>();
    private final Map<K, PendingMember> pendingMembers = new LinkedHashMap<>();
    private final Map<BatchKey, Batch> batches = new LinkedHashMap<>();
    private final ArrayDeque<Batch> dirtyBatches = new ArrayDeque<>();
    private long builds;
    private long uploadedVertices;
    private int failures;
    private long lightChanges;
    private double prepareMicros;

    void put(K id, Object geometryKey, Vec3 origin, Matrix4f transform, int light) {
        Member old = this.members.get(id);
        if (old != null && old.geometry.key.equals(geometryKey)) {
            PendingMember pending = this.pendingMembers.remove(id);
            if (pending != null) pending.geometry.release();
            if (old.origin.equals(origin) && old.transform.equals(transform) && old.light == light) return;
            installMember(id, new Member(old.geometry, origin, transform, light));
            return;
        }
        PendingMember pending = this.pendingMembers.get(id);
        GeometryCache.Entry entry;
        if (pending != null && pending.geometry.key.equals(geometryKey)) {
            entry = pending.geometry;
        } else {
            entry = this.cache.acquire(geometryKey);
            if (pending != null) pending.geometry.release();
        }
        this.pendingMembers.put(id, new PendingMember(entry, origin, transform, light));
    }

    /** 材质 pass 由捕获结果决定；共享 CPU 网格就绪后才加入批次。 */
    private void prepareMembers() {
        var iterator = this.pendingMembers.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            PendingMember pending = entry.getValue();
            if (!pending.geometry.captured()) continue;
            iterator.remove();
            installMember(entry.getKey(), new Member(pending.geometry, pending.origin, pending.transform, pending.light));
        }
    }

    private void installMember(K id, Member member) {
        Member old = this.members.get(id);
        boolean sameGeometry = old != null && old.sameGeometry(member);
        if (old != null) {
            for (GeometryCollector.Pass pass : old.geometry.passes()) {
                if (!member.geometry.passes().contains(pass) || !key(old, pass).equals(key(member, pass))) {
                    removeFromBatch(id, key(old, pass));
                }
            }
        }
        for (GeometryCollector.Pass pass : member.geometry.passes()) {
            BatchKey nextKey = key(member, pass);
            Batch batch = this.batches.computeIfAbsent(nextKey, Batch::new);
            batch.members.put(id, member);
            if (sameGeometry) {
                patchLight(batch.active, id, member);
                patchLight(batch.pending, id, member);
            } else {
                markDirty(batch);
            }
        }
        if (sameGeometry) this.lightChanges++;
        this.members.put(id, member);
        if (old != null && old.geometry != member.geometry) old.geometry.release();
    }

    private void patchLight(Version version, K id, Member member) {
        if (version == null || !version.handle.isAlive() || version.handle.uploadFailed()) return;
        LightSlice slice = version.slices.get(id);
        if (slice == null || slice.ranges.isEmpty() || !slice.captured.sameGeometry(member)
                || slice.appliedLight == member.light) return;
        for (LightRange range : slice.ranges) {
            version.handle.updateLight(range.firstVertex, range.vertexCount, member.light);
        }
        slice.appliedLight = member.light;
    }

    void remove(K id) {
        PendingMember pending = this.pendingMembers.remove(id);
        if (pending != null) pending.geometry.release();
        Member member = this.members.remove(id);
        if (member != null) {
            for (GeometryCollector.Pass pass : member.geometry.passes()) removeFromBatch(id, key(member, pass));
            member.geometry.release();
        }
    }

    private void removeFromBatch(K id, BatchKey key) {
        Batch batch = this.batches.get(key);
        if (batch == null) {
            return;
        }
        batch.members.remove(id);
        if (batch.members.isEmpty()) {
            release(batch.active);
            release(batch.pending);
            this.batches.remove(key);
        } else {
            markDirty(batch);
        }
    }

    private void markDirty(Batch batch) {
        if (!batch.dirty) {
            batch.dirty = true;
            this.dirtyBatches.addLast(batch);
        }
    }

    private static BatchKey key(Member member, GeometryCollector.Pass pass) {
        return new BatchKey(sectionOrigin(member.origin), pass);
    }

    private static Vec3 sectionOrigin(Vec3 position) {
        return new Vec3(Math.floor(position.x / 16.0) * 16.0,
                Math.floor(position.y / 16.0) * 16.0, Math.floor(position.z / 16.0) * 16.0);
    }

    void prepare(WorldMeshGroup<?> owner) {
        long start = System.nanoTime();
        prepareMembers();
        // 先完成所有换挡，不能让前面的重建预算阻挡后面已就绪的批次。
        for (Batch batch : this.batches.values()) {
            if (batch.pending != null && batch.pending.handle.uploadFailed()) {
                release(batch.pending);
                batch.pending = null;
                markDirty(batch);
                this.failures++;
            } else if (batch.pending != null && batch.pending.handle.isUploaded()) {
                release(batch.active);
                batch.active = batch.pending;
                batch.pending = null;
            }
        }
        int built = 0;
        int remaining = this.dirtyBatches.size();
        while (remaining-- > 0 && !this.dirtyBatches.isEmpty()) {
            if (built >= MAX_BUILDS_PER_FRAME || (built > 0 && System.nanoTime() - start >= BUILD_BUDGET_NANOS)) {
                break;
            }
            Batch batch = this.dirtyBatches.removeFirst();
            if (this.batches.get(batch.key) != batch) continue;
            if (batch.pending != null) {
                this.dirtyBatches.addLast(batch);
                continue;
            }
            rebuild(owner, batch.key, batch);
            // 失败重试移到队尾，持续变化/失败的批次不能阻塞其它 section。
            if (batch.dirty) this.dirtyBatches.addLast(batch);
            built++;
        }
        this.prepareMicros = (System.nanoTime() - start) / 1000.0;
    }

    private void rebuild(WorldMeshGroup<?> owner, BatchKey key, Batch batch) {
        MeshSink sink = new MeshSink().begin(key.pass.mode());
        Map<K, LightSlice> slices = new LinkedHashMap<>();
        for (Map.Entry<K, Member> entry : batch.members.entrySet()) {
            Member member = entry.getValue();
            int firstVertex = sink.vertexCount();
            int firstRun = Math.max(0, sink.lightRunCount() - 1);
            // 用 0 捕获普通顶点，非零保持自发光；只记录普通顶点的范围。
            member.geometry.mesh(key.pass).emitTo(sink, member.transform,
                    member.origin.x - key.origin.x, member.origin.y - key.origin.y, member.origin.z - key.origin.z);
            int endVertex = sink.vertexCount();
            List<LightRange> ranges = new ArrayList<>();
            for (int run = firstRun, count = sink.lightRunCount(); run < count; run++) {
                if (sink.lightRunValue(run) != 0) continue;
                int start = Math.max(firstVertex, sink.lightRunStart(run));
                int end = Math.min(endVertex, sink.lightRunStart(run) + sink.lightRunLength(run));
                if (start < end) ranges.add(new LightRange(start, end - start));
            }
            slices.put(entry.getKey(), new LightSlice(member, ranges));
        }
        this.builds++;
        if (sink.isEmpty()) {
            release(batch.active);
            batch.active = null;
            batch.dirty = false;
            return;
        }
        // 纯发光批次的 UV2 全是固定值，直接读几何缓冲，不分配可变光照流。
        boolean hasDynamicLight = false;
        for (int run = 0, count = sink.lightRunCount(); run < count; run++) {
            if (sink.lightRunValue(run) == 0) {
                hasDynamicLight = true;
                break;
            }
        }
        // 保存真实局部包围盒；枚举时平移到世界坐标，允许几何跨 section 边界。
        ShardHandle submitted = WorldMeshRenderer.submit(owner, sink, key.pass.material(),
                hasDynamicLight ? MeshLighting.MUTABLE : MeshLighting.INSTANCE);
        if (submitted == null) {
            this.failures++;
            return;
        }
        batch.pending = new Version(submitted, slices);
        for (Map.Entry<K, Member> entry : batch.members.entrySet()) {
            patchLight(batch.pending, entry.getKey(), entry.getValue());
        }
        batch.dirty = false;
        this.uploadedVertices += sink.vertexCount();
    }

    void collect(WorldMeshGroup.ShardSink out) {
        for (Map.Entry<BatchKey, Batch> entry : this.batches.entrySet()) {
            Version version = entry.getValue().active;
            ShardHandle active = version == null ? null : version.handle;
            if (active != null && active.isAlive()) {
                out.accept(active, entry.getKey().origin, new Matrix4f(),
                        active.localBounds().move(entry.getKey().origin), 0);
            }
        }
    }

    void clear() {
        for (Batch batch : this.batches.values()) {
            release(batch.active);
            release(batch.pending);
        }
        this.batches.clear();
        for (PendingMember member : this.pendingMembers.values()) member.geometry.release();
        this.pendingMembers.clear();
        for (Member member : this.members.values()) member.geometry.release();
        this.members.clear();
        this.dirtyBatches.clear();
        this.prepareMicros = 0.0;
    }

    private void release(Version version) {
        if (version != null && version.handle.isAlive()) version.handle.release();
    }

    WorldMeshGroupStats.Section stats() {
        int active = 0;
        int dirty = 0;
        int pending = 0;
        for (Batch batch : this.batches.values()) {
            if (batch.active != null) active++;
            if (batch.pending != null) pending++;
            if (batch.dirty) dirty++;
        }
        return new WorldMeshGroupStats.Section(this.batches.size(), active, dirty, pending, this.pendingMembers.size(),
                this.builds, this.uploadedVertices, this.lightChanges, this.failures, this.prepareMicros);
    }
}
