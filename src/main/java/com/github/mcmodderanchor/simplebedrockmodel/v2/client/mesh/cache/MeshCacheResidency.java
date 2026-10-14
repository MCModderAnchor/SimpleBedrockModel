package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/** 最近请求、准备任务和绘制引用共同决定驻留；LRU 只包含真正闲置的就绪条目。 */
public class MeshCacheResidency<K, V> {
    private class Use {
        final V value;
        long frame, requestedAt, retention;
        boolean idle;
        Use(V value) { this.value = value; }
    }

    private final Map<K, V> cache;
    private final Map<K, Use> uses = new HashMap<>();
    private final IdleMeshCache<K, V> idle;
    private final LongSupplier clock;
    private final ToIntFunction<V> references;
    private final Predicate<V> preparing;
    private final ToLongFunction<V> bytes;
    private final Consumer<V> dispose;
    private long frame;

    public MeshCacheResidency(Map<K, V> cache, int maxIdleEntries, long maxIdleBytes,
                              LongSupplier clock, ToIntFunction<V> references,
                              Predicate<V> preparing, ToLongFunction<V> bytes, Consumer<V> dispose) {
        this.cache = cache;
        this.clock = clock;
        this.references = references;
        this.preparing = preparing;
        this.bytes = bytes;
        this.dispose = dispose;
        this.idle = new IdleMeshCache<>(maxIdleEntries, maxIdleBytes, clock, dispose);
    }

    public void request(K key, V value, Duration retention) {
        request(key, value, retention.toNanos());
    }

    private void request(K key, V value, long retention) {
        if (retention < 0) throw new IllegalArgumentException("Negative mesh retention");
        Use use = uses.get(key);
        if (use == null || use.value != value) {
            idle.remove(key);
            use = new Use(value);
            uses.put(key, use);
        }
        use.retention = retention;
        use.frame = frame;
        use.requestedAt = clock.getAsLong();
        use.idle = false;
        idle.remove(key);
    }

    public void request(K key, V value) {
        Use use = uses.get(key);
        request(key, value, use != null && use.value == value
                ? use.retention : MeshCachePolicy.DEFAULT.idleRetention().toNanos());
    }

    public void retention(K key, V value, Duration retention) {
        Use use = uses.get(key);
        if (use != null && use.value == value) use.retention = retention.toNanos();
    }

    public void forget(K key, V value) {
        Use use = uses.get(key);
        if (use == null || use.value != value) return;
        uses.remove(key);
        idle.remove(key);
    }

    public void beginFrame(long frame) {
        this.frame = frame;
        long now = clock.getAsLong();
        // 淘汰回调会删除对应的使用记录，遍历快照避免修改迭代器。
        for (var item : new ArrayList<>(uses.entrySet())) {
            K key = item.getKey();
            Use use = item.getValue();
            if (cache.get(key) != use.value) { forget(key, use.value); continue; }
            if (references.applyAsInt(use.value) > 0 || use.frame >= frame - 1) continue;
            long remaining = use.retention - (now - use.requestedAt);
            if (remaining <= 0) {
                forget(key, use.value);
                dispose.accept(use.value);
            } else if (!use.idle && !preparing.test(use.value)) {
                use.idle = true;
                idle.retain(key, use.value, bytes.applyAsLong(use.value), Duration.ofNanos(remaining));
            }
        }
        idle.evictExpired();
    }

    public boolean reclaimIdle() { return idle.evictOldest(); }
    public void clear() { uses.clear(); idle.clear(); }
}
