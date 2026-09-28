package com.github.mcmodderanchor.simplebedrockmodel.v2.client.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 保存共享几何、捕获任务和 GPU 所有权；不调用业务对象或回调渲染组。 */
final class GeometryCache {
    private final Map<Object, Entry> entries = new HashMap<>();
    private final ArrayDeque<Entry> captureQueue = new ArrayDeque<>();
    private long captures;
    private long failures;

    final class Entry {
        final Object key;
        private int references;
        private Map<GeometryCollector.Pass, MeshSink> meshes;
        private List<ShardHandle> handles;

        Entry(Object key) { this.key = key; }
        boolean captured() { return this.meshes != null; }
        Set<GeometryCollector.Pass> passes() { return this.meshes.keySet(); }
        MeshSink mesh(GeometryCollector.Pass pass) { return this.meshes.get(pass); }

        /** 上传已经捕获的结果 */
        boolean prepareGpu(WorldMeshGroup<?> group) {
            if (!captured()) return false;
            if (this.handles != null) {
                if (this.handles.stream().anyMatch(h -> !h.isAlive() || h.uploadFailed())) {
                    closeHandles();
                    failures++;
                } else return ready();
            }
            List<ShardHandle> uploaded = new ArrayList<>();
            try {
                for (GeometryCollector.Pass pass : passes()) {
                    MeshSink mesh = mesh(pass);
                    ShardHandle handle = WorldMeshRenderer.submit(group, mesh, pass.material(), MeshLighting.INSTANCE);
                    if (handle == null) {
                        uploaded.forEach(ShardHandle::release);
                        failures++;
                        return false;
                    }
                    uploaded.add(handle);
                }
            } catch (RuntimeException exception) {
                uploaded.forEach(ShardHandle::release);
                throw exception;
            }
            this.handles = uploaded;
            return ready();
        }

        boolean ready() {
            return this.handles != null && this.handles.stream().allMatch(h -> h.isAlive() && h.isUploaded());
        }
        List<ShardHandle> handles() { return this.handles == null ? List.of() : this.handles; }
        boolean queued() { return this.handles != null && this.handles.stream().allMatch(h -> h.isAlive() && !h.uploadFailed()); }

        void release() {
            if (--this.references == 0) {
                closeHandles();
                entries.remove(this.key, this);
            }
        }
        private void closeHandles() {
            if (this.handles != null) {
                this.handles.forEach(ShardHandle::release);
                this.handles = null;
            }
        }
    }

    Entry acquire(Object key) {
        Entry entry = this.entries.get(key);
        if (entry == null) {
            entry = new Entry(key);
            this.entries.put(key, entry);
            this.captureQueue.addLast(entry);
        }
        entry.references++;
        return entry;
    }

    int pendingCaptures() { return this.captureQueue.size(); }

    Entry pollCapture() {
        Entry entry;
        while ((entry = this.captureQueue.pollFirst()) != null) {
            if (entry.references > 0 && !entry.captured()) return entry;
        }
        return null;
    }

    /** 由渲染组写入收集结果；失败任务轮转重试，已经被解除引用的任务直接丢弃。 */
    void finishCapture(Entry entry, GeometryCollector collector, boolean success) {
        if (entry.references == 0 || this.entries.get(entry.key) != entry) return;
        if (success) {
            entry.meshes = collector.snapshot();
            this.captures++;
        } else {
            this.failures++;
            this.captureQueue.addLast(entry);
        }
    }

    public void discardReleasedCaptures() {
        this.captureQueue.removeIf(entry -> entry.references == 0);
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
