package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.GeometryCollector;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache.MeshCachePolicy;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.WorldMeshPart;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.config.WorldMeshConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 库管理的世界网格渲染组。直接登记业务对象或其适配器，遍历时通过回调获取有效性与最新状态。
 * 全部方法在渲染线程调用。世界原点与局部实例矩阵独立于共享几何。
 */
public class WorldMeshGroup<T> implements AutoCloseable {
    @FunctionalInterface
    public interface ShardSink {
        public void accept(WorldMeshPart handle, Vec3 origin, Matrix4f localTransform, AABB worldBounds, int packedLight);
    }

    /** 自实现接口的对象共享一个桥接适配器；没有每对象的方法引用／lambda 分配。 */
    private static final MeshRenderableAdapter<Object> DIRECT_OBJECT = new MeshRenderableAdapter<>() {
        @Override
        public boolean isValid(Object object) {
            return ((MeshRenderable) object).isValid();
        }

        @Override
        public boolean needsUpdate(Object object) {
            return ((MeshRenderable) object).needsUpdate();
        }

        @Override
        public boolean isVisible(Object object) {
            return ((MeshRenderable) object).isVisible();
        }

        @Override
        public Vec3 origin(Object object) {
            return ((MeshRenderable) object).origin();
        }

        @Override
        public Matrix4f localTransform(Object object) {
            return ((MeshRenderable) object).localTransform();
        }

        @Override
        public int packedLight(Object object) {
            return ((MeshRenderable) object).packedLight();
        }

        @Override
        public Object geometryKey(Object object) {
            return ((MeshRenderable) object).geometryKey();
        }

        @Override
        public boolean collectGeometry(Object object, GeometryCollector collector) {
            return ((MeshRenderable) object).collectGeometry(collector);
        }
    };

    /** 一条登记的数据：对象、访问方式、比较缓存和脏版本；不承担行为转发。 */
    public class Entry {
        final T object;
        final MeshRenderableAdapter<? super T> adapter;
        Object geometryKey;
        Vec3 origin;
        Matrix4f localTransform;
        int packedLight;
        boolean visible;
        boolean initialized;
        long dirtyRevision = 1;
        long readRevision;
        long lastCheckTick = Long.MIN_VALUE;
        boolean explicitDraw;
        long claimedDrawSerial = Long.MIN_VALUE;
        Object claimedGeometryKey;
        Object claimedPreviousGeometryKey;

        public Entry(T object, MeshRenderableAdapter<? super T> adapter) {
            this.object = object;
            this.adapter = adapter;
        }
    }

    private final String id;
    private final WorldMeshStrategy defaultStrategy;
    private final Set<WorldMeshStrategy> supported;
    private WorldMeshStrategy effectiveStrategy;
    private StrategyOverride seenConfig;
    private String policySource = "default";
    private String warnings = "";
    private final Map<T, Entry> objects = new IdentityHashMap<>();
    private final List<Entry> traversal = new ArrayList<>();
    private final MeshCachePolicy cachePolicy;
    private final GeometryCache cache;
    private final InstanceMeshBatches<Entry> instances;
    private final SectionMeshBatches<Entry> sections;
    private boolean populated;
    private boolean closed;

    public WorldMeshGroup(String id, WorldMeshStrategy defaultStrategy, Set<WorldMeshStrategy> supported) {
        this(id, defaultStrategy, supported, MeshCachePolicy.DEFAULT);
    }

    public WorldMeshGroup(String id, WorldMeshStrategy defaultStrategy, Set<WorldMeshStrategy> supported,
                   MeshCachePolicy cachePolicy) {
        this.id = Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("Empty mesh group id");
        this.defaultStrategy = Objects.requireNonNull(defaultStrategy, "defaultStrategy");
        this.supported = Set.copyOf(EnumSet.copyOf(supported));
        if (!this.supported.contains(defaultStrategy)) throw new IllegalArgumentException("Default strategy is unsupported");
        this.effectiveStrategy = defaultStrategy;
        this.cachePolicy = Objects.requireNonNull(cachePolicy, "cachePolicy");
        this.cache = new GeometryCache(cachePolicy);
        this.instances = new InstanceMeshBatches<>(this.cache);
        this.sections = new SectionMeshBatches<>(this.cache);
    }

    public String id() { return this.id; }
    public WorldMeshStrategy defaultStrategy() { return this.defaultStrategy; }
    public Set<WorldMeshStrategy> supportedStrategies() { return this.supported; }
    public WorldMeshStrategy effectiveStrategy() { return this.effectiveStrategy; }
    public MeshCachePolicy cachePolicy() { return this.cachePolicy; }

    /** 对象需实现 MeshRenderable；同一对象重复登记不更换绑定，也不强制更新。 */
    public void track(T object) {
        checkRegistration(object);
        if (this.objects.containsKey(object)) return;
        if (!(object instanceof MeshRenderable)) {
            throw new IllegalArgumentException("Object must implement MeshRenderable or supply an adapter");
        }
        this.objects.put(object, new Entry(object, DIRECT_OBJECT));
    }

    /** 按引用身份登记，不使用对象的 equals/hashCode；更换适配器需先 untrack。 */
    public void track(T object, MeshRenderableAdapter<? super T> adapter) {
        checkRegistration(object);
        Objects.requireNonNull(adapter, "adapter");
        if (this.objects.containsKey(object)) return;
        this.objects.put(object, new Entry(object, adapter));
    }

    public void checkRegistration(T object) {
        checkOpen();
        Objects.requireNonNull(object, "object");
        if (!WorldMeshRenderer.ensureLevel()) throw new IllegalStateException("No client world");
    }

    /** 标记后在下一次渲染组遍历强制读取状态，不受 needsUpdate 或周期限制。 */
    public void markDirty(T object) {
        checkOpen();
        WorldMeshRenderer.ensureLevel();
        Entry entry = this.objects.get(object);
        if (entry != null) entry.dirtyRevision++;
    }

    public void untrack(T object) {
        checkOpen();
        WorldMeshRenderer.ensureLevel();
        Entry entry = this.objects.remove(object);
        if (entry != null && this.populated) removeBackend(entry);
    }

    /**
     * BER 在绘制单个对象前调用。首次调用后该对象改为显式绘制：仅当前主世界帧成功
     * claim 的已上传 INSTANCE 网格会在 AFTER_BLOCK_ENTITIES 绘制；返回 false 时调用方
     * 保留普通绘制。调用方应传入本帧实际外观对应的 key，不得只传上一帧快照。
     */
    public boolean claimForWorldDraw(T object, Object expectedGeometryKey) {
        return claimForWorldDraw(object, expectedGeometryKey, null);
    }

    /**
     * 目标网格准备期间，允许继续绘制已经上传的旧变体。
     * 调用方须提供精确兼容的键，例如同一外观的另一档 LOD。
     * 两个键都必须包含资源／外观修订；传入 null 时仍要求目标键精确匹配。
     * 此处不会同步捕获几何，也不会调用普通渲染器。
     */
    public boolean claimForWorldDraw(T object, Object expectedGeometryKey, Object previousGeometryKey) {
        checkOpen();
        Objects.requireNonNull(expectedGeometryKey, "expectedGeometryKey");
        WorldMeshRenderer.ensureLevel();
        Entry entry = this.objects.get(object);
        if (entry == null) return false;
        entry.explicitDraw = true;
        entry.claimedDrawSerial = Long.MIN_VALUE;
        entry.claimedGeometryKey = null;
        entry.claimedPreviousGeometryKey = null;
        // 相机引起的变化必须在本帧刷新，即使仍处于同一个客户端 tick。
        if (!expectedGeometryKey.equals(entry.geometryKey)) entry.dirtyRevision++;
        if (!WorldMeshRenderer.isEnabled() || this.effectiveStrategy != WorldMeshStrategy.INSTANCE
                || !this.instances.ready(entry, expectedGeometryKey)
                && (previousGeometryKey == null || !this.instances.ready(entry, previousGeometryKey))) return false;
        entry.claimedGeometryKey = expectedGeometryKey;
        entry.claimedPreviousGeometryKey = previousGeometryKey;
        entry.claimedDrawSerial = WorldMeshRenderer.nextWorldDrawSerial();
        return true;
    }

    /** 清除逻辑对象及其缓存；策略默认值和覆盖设置保持不变。 */
    public void clear() {
        checkOpen();
        clearCaches();
        this.objects.clear();
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (this.closed) return;
        clear();
        this.closed = true;
        WorldMeshRenderer.unregister(this);
    }

    public void checkOpen() {
        RenderSystem.assertOnRenderThread();
        if (this.closed) throw new IllegalStateException("Mesh group is closed: " + this.id);
    }

    public void updateBackend(Entry entry) {
        if (this.effectiveStrategy == WorldMeshStrategy.SECTION) {
            this.sections.put(entry, entry.geometryKey, entry.origin, entry.localTransform, entry.packedLight);
        } else {
            this.instances.put(entry, entry.geometryKey, entry.origin, entry.localTransform, entry.packedLight);
        }
    }

    public void removeBackend(Entry entry) {
        if (this.effectiveStrategy == WorldMeshStrategy.SECTION) this.sections.remove(entry);
        else this.instances.remove(entry);
    }

    /** 阶段一：只读取对象状态，不捕获顶点、不上传 GPU。 */
    public void refreshObjects() {
        this.traversal.clear();
        this.traversal.addAll(this.objects.values());
        try {
            for (Entry entry : this.traversal) {
                if (this.objects.get(entry.object) != entry) continue;
                boolean valid = entry.adapter.isValid(entry.object);
                if (this.objects.get(entry.object) != entry) continue;
                if (!valid) {
                    this.objects.remove(entry.object);
                    if (this.populated) removeBackend(entry);
                    continue;
                }
                long tick = WorldMeshRenderer.stateUpdateTick();
                boolean forced = !entry.initialized || entry.dirtyRevision != entry.readRevision;
                if (!forced && entry.lastCheckTick == tick) continue;
                entry.lastCheckTick = tick;
                if (!forced && !entry.adapter.needsUpdate(entry.object)) continue;
                if (this.objects.get(entry.object) != entry) continue;
                long revision = entry.dirtyRevision;
                Object key = Objects.requireNonNull(entry.adapter.geometryKey(entry.object), "geometryKey");
                Vec3 origin = Objects.requireNonNull(entry.adapter.origin(entry.object), "origin");
                Matrix4f localTransform = new Matrix4f(Objects.requireNonNull(
                        entry.adapter.localTransform(entry.object), "localTransform"));
                int light = entry.adapter.packedLight(entry.object);
                boolean visible = entry.adapter.isVisible(entry.object);
                if (this.objects.get(entry.object) != entry) continue;
                boolean changed = !entry.initialized || !key.equals(entry.geometryKey) || !origin.equals(entry.origin)
                        || !localTransform.equals(entry.localTransform)
                        || light != entry.packedLight || visible != entry.visible;
                entry.geometryKey = key;
                entry.origin = origin;
                entry.localTransform = localTransform;
                entry.packedLight = light;
                entry.visible = visible;
                entry.initialized = true;
                // 属性回调里再次 markDirty 的版本仍保留。
                entry.readRevision = revision;
                if (changed && this.populated) {
                    if (visible) updateBackend(entry);
                    else removeBackend(entry);
                }
            }
        } finally {
            this.traversal.clear();
        }
    }

    /** 阶段二：渲染组显式调度捕获。缓存只保存任务和结果，不通过闭包反向调用渲染组。 */
    public void capturePending() {
        int remaining = this.cache.pendingCaptures();
        if (remaining == 0) return;
        List<Entry> providers = new ArrayList<>(this.objects.values());
        long start = System.nanoTime();
        int attempts = 0;
        while (remaining-- > 0 && attempts < 4 && (attempts == 0 || System.nanoTime() - start < 2_000_000L)) {
            GeometryCache.Entry geometry = this.cache.pollCapture();
            if (geometry == null) break;
            GeometryCollector collector = new GeometryCollector();
            boolean success = false;
            try {
                success = captureGeometry(geometry.key, collector, providers);
            } finally {
                this.cache.finishCapture(geometry, collector, success);
            }
            attempts++;
        }
    }

    public boolean captureGeometry(Object key, GeometryCollector collector, List<Entry> providers) {
        for (Entry entry : providers) {
            if (this.objects.get(entry.object) != entry || !entry.initialized || !key.equals(entry.geometryKey)
                    || !entry.adapter.isValid(entry.object)) continue;
            if (!key.equals(entry.adapter.geometryKey(entry.object)) || this.objects.get(entry.object) != entry) continue;
            collector.clear();
            boolean captured = entry.adapter.collectGeometry(entry.object, collector);
            if (captured && this.objects.get(entry.object) == entry && key.equals(entry.adapter.geometryKey(entry.object))) return true;
        }
        collector.clear();
        return false;
    }

    public void applyPolicy() {
        StrategyOverride config = WorldMeshConfig.SPEC.isLoaded() ? WorldMeshConfig.STRATEGY.get() : StrategyOverride.AUTO;
        if (config == this.seenConfig) return;
        this.seenConfig = config;
        WorldMeshStrategy selected = this.defaultStrategy;
        String source = "default";
        StringBuilder rejected = new StringBuilder();
        if (config != StrategyOverride.AUTO) {
            if (this.supported.contains(config.strategy())) {
                selected = config.strategy();
                source = "config";
            } else rejected.append("unsupported config ").append(config).append("; ");
        }
        this.policySource = source;
        this.warnings = rejected.toString();
        if (selected != this.effectiveStrategy) {
            clearCaches();
            this.effectiveStrategy = selected;
        }
    }

    public void clearCaches() {
        this.instances.clear();
        this.sections.clear();
        this.cache.clear();
        this.populated = false;
    }

    public void invalidate(WorldMeshRenderer.Invalidation reason) {
        clearCaches();
        if (reason == WorldMeshRenderer.Invalidation.WORLD) this.objects.clear();
        else if (reason == WorldMeshRenderer.Invalidation.RESOURCES) {
            for (Entry entry : this.objects.values()) entry.dirtyRevision++;
        }
    }

    public void prepareFrame() {
        applyPolicy();
        refreshObjects();
        if (!this.populated) {
            for (Entry entry : this.objects.values()) {
                if (entry.initialized && entry.visible) updateBackend(entry);
            }
            this.populated = true;
        }
        capturePending();
        // 阶段三：策略仅使用已捕获结果构建／上传网格。
        if (this.effectiveStrategy == WorldMeshStrategy.SECTION) this.sections.prepare(this);
        else this.instances.prepare(this);
        this.cache.evictExpired();
    }

    public void collect(ShardSink out) {
        if (this.effectiveStrategy == WorldMeshStrategy.SECTION) this.sections.collect(out);
        else this.instances.collect(out, entry -> !entry.explicitDraw
                || entry.claimedDrawSerial == WorldMeshRenderer.currentWorldDrawSerial()
                && entry.visible && entry.claimedGeometryKey.equals(entry.geometryKey)
                && (this.instances.ready(entry, entry.claimedGeometryKey)
                || entry.claimedPreviousGeometryKey != null
                && this.instances.ready(entry, entry.claimedPreviousGeometryKey)));
    }

    public WorldMeshGroupStats stats() {
        checkOpen();
        int visible = 0;
        for (Entry entry : this.objects.values()) if (entry.initialized && entry.visible) visible++;
        return new WorldMeshGroupStats(this.id, this.defaultStrategy, this.effectiveStrategy, this.policySource, this.warnings,
                this.objects.size(), visible, this.cache.size(), this.cache.captures(), this.cache.failures(), this.sections.stats());
    }
}
