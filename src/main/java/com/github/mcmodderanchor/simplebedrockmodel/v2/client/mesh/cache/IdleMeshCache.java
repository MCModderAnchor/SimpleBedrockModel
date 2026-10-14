package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** 只有引用数为零的条目进入 LRU；取用条目后持有引用，不参与空闲淘汰预算。 */
public class IdleMeshCache<K, V> {
    public record Idle<V>(V value, long releasedAt, long retentionNanos, long bytes) {}
    private final Map<K, Idle<V>> entries = new LinkedHashMap<>();
    private final int maxEntries;
    private final long maxBytes;
    private final LongSupplier clock;
    private final Consumer<V> dispose;
    private long bytes;

    public IdleMeshCache(int maxEntries, long maxBytes, LongSupplier clock, Consumer<V> dispose) {
        this.maxEntries = maxEntries;
        this.maxBytes = maxBytes;
        this.clock = clock;
        this.dispose = dispose;
    }

    public void retain(K key, V value, long estimatedBytes, Duration retention) {
        remove(key);
        evictExpired();
        long nanos = retention.toNanos();
        if (nanos == 0 || maxEntries == 0 || estimatedBytes > maxBytes) {
            dispose.accept(value);
            return;
        }
        entries.put(key, new Idle<>(value, clock.getAsLong(), nanos, estimatedBytes));
        bytes += estimatedBytes;
        Iterator<Idle<V>> iterator = entries.values().iterator();
        while ((entries.size() > maxEntries || bytes > maxBytes) && iterator.hasNext()) {
            Idle<V> idle = iterator.next();
            iterator.remove();
            bytes -= idle.bytes();
            dispose.accept(idle.value());
        }
    }

    public V remove(K key) {
        Idle<V> idle = entries.remove(key);
        if (idle == null) return null;
        bytes -= idle.bytes();
        return idle.value();
    }

    /** 内存准入压力下仅回收真正闲置的最旧条目。 */
    public boolean evictOldest() {
        Iterator<Idle<V>> iterator = entries.values().iterator();
        if (!iterator.hasNext()) return false;
        Idle<V> oldest = iterator.next();
        iterator.remove();
        bytes -= oldest.bytes();
        dispose.accept(oldest.value());
        return true;
    }

    public void evictExpired() {
        long now = clock.getAsLong();
        Iterator<Idle<V>> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Idle<V> idle = iterator.next();
            if (now - idle.releasedAt() < idle.retentionNanos()) continue;
            iterator.remove();
            bytes -= idle.bytes();
            dispose.accept(idle.value());
        }
    }

    public void clear() {
        var retired = entries.values().stream().map(Idle::value).toList();
        entries.clear();
        bytes = 0;
        retired.forEach(dispose);
    }
}
