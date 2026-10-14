package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.GeometryCollector;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.MeshSink;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache.IdleMeshCache;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache.MeshCachePolicy;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.WorldMeshPart;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.MeshLighting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/** 保存共享几何、捕获任务和 GPU 所有权；不调用业务对象或回调渲染组。 */
public class GeometryCache {
    private final Map<Object, Entry> entries = new HashMap<>();
    private final ArrayDeque<Entry> captureQueue = new ArrayDeque<>();
    private final MeshCachePolicy policy;
    private final IdleMeshCache<Object, Entry> idle;
    private long captures;
    private long failures;

    public GeometryCache() { this(MeshCachePolicy.DEFAULT); }

    public GeometryCache(MeshCachePolicy policy) { this(policy, System::nanoTime); }

    public GeometryCache(MeshCachePolicy policy, LongSupplier clock) {
        this.policy = policy;
        this.idle = new IdleMeshCache<>(policy.maxIdleEntries(), policy.maxIdleBytes(), clock, this::discard);
    }

    public class Entry {
        final Object key;
        private int references;
        private Map<GeometryCollector.Pass, MeshSink> meshes;
        private List<WorldMeshPart> handles;
        private boolean captureQueued;

        public Entry(Object key) { this.key = key; }
        public boolean captured() { return this.meshes != null; }
        public Set<GeometryCollector.Pass> passes() { return this.meshes.keySet(); }
        public MeshSink mesh(GeometryCollector.Pass pass) { return this.meshes.get(pass); }

        /** 上传已经捕获的结果 */
        public boolean prepareGpu(WorldMeshGroup<?> group) {
            if (!captured()) return false;
            if (this.handles != null) {
                if (this.handles.stream().anyMatch(h -> !h.isAlive() || h.uploadFailed())) {
                    closeHandles();
                    failures++;
                } else return ready();
            }
            List<WorldMeshPart> uploaded = new ArrayList<>();
            try {
                for (GeometryCollector.Pass pass : passes()) {
                    MeshSink mesh = mesh(pass);
                    WorldMeshPart handle = WorldMeshRenderer.BUFFERS.submit(group.id(), mesh, pass.material(), MeshLighting.INSTANCE);
                    if (handle == null) {
                        uploaded.forEach(WorldMeshPart::release);
                        failures++;
                        return false;
                    }
                    uploaded.add(handle);
                }
            } catch (RuntimeException exception) {
                uploaded.forEach(WorldMeshPart::release);
                throw exception;
            }
            this.handles = uploaded;
            return ready();
        }

        public boolean ready() {
            return this.handles != null && this.handles.stream().allMatch(h -> h.isAlive() && h.isUploaded());
        }
        public List<WorldMeshPart> handles() { return this.handles == null ? List.of() : this.handles; }
        public boolean queued() { return this.handles != null && this.handles.stream().allMatch(h -> h.isAlive() && !h.uploadFailed()); }

        public void release() {
            if (this.references <= 0) throw new IllegalStateException("Geometry already released");
            if (--this.references == 0) {
                long bytes = this.meshes == null ? 0 : this.meshes.values().stream()
                        .mapToLong(mesh -> mesh.estimatedBytes()).sum();
                idle.retain(this.key, this, bytes, policy.idleRetention());
            }
        }
        public void closeHandles() {
            closeHandles(false);
        }
        public void closeHandles(boolean discardStorage) {
            if (this.handles != null) {
                if (discardStorage) this.handles.forEach(WorldMeshRenderer.BUFFERS::discard);
                else this.handles.forEach(WorldMeshPart::release);
                this.handles = null;
            }
        }
    }

    public Entry acquire(Object key) {
        this.idle.evictExpired();
        this.idle.remove(key);
        Entry entry = this.entries.get(key);
        if (entry == null) {
            entry = new Entry(key);
            this.entries.put(key, entry);
        }
        entry.references++;
        enqueueCapture(entry);
        return entry;
    }

    public void enqueueCapture(Entry entry) {
        if (entry.captured() || entry.captureQueued) return;
        entry.captureQueued = true;
        this.captureQueue.addLast(entry);
    }

    public void discard(Entry entry) {
        entry.closeHandles(true);
        this.entries.remove(entry.key, entry);
    }

    public int pendingCaptures() { return this.captureQueue.size(); }

    public Entry pollCapture() {
        Entry entry;
        while ((entry = this.captureQueue.pollFirst()) != null) {
            entry.captureQueued = false;
            if (entry.references > 0 && this.entries.get(entry.key) == entry && !entry.captured()) return entry;
        }
        return null;
    }

    /** 由渲染组写入收集结果；失败任务轮转重试，已经被解除引用的任务直接丢弃。 */
    public void finishCapture(Entry entry, GeometryCollector collector, boolean success) {
        if (entry.references == 0 || this.entries.get(entry.key) != entry) return;
        if (success) {
            entry.meshes = collector.snapshot();
            this.captures++;
        } else {
            this.failures++;
            enqueueCapture(entry);
        }
    }

    public void evictExpired() { this.idle.evictExpired(); }

    public void clear() {
        this.idle.clear();
        for (Entry entry : this.entries.values()) entry.closeHandles(true);
        this.entries.clear();
        this.captureQueue.clear();
    }

    public int size() {
        return this.entries.size();
    }

    public long captures() {
        return this.captures;
    }

    public long failures() {
        return this.failures;
    }
}
