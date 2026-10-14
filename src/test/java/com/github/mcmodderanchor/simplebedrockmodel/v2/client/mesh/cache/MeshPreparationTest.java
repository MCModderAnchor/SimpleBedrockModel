package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class MeshPreparationTest {
    private static final MeshPreparationPolicy POLICY = new MeshPreparationPolicy(4, 100, 4, 16, 100, 4096, 4096);

    private static class Geometry {
        int references, pending = 3, captured = 1, cancellations;
        long bytes = 3;
        boolean ready() { return pending == 0; }
    }

    private static MeshUploadQueue.Upload part(Geometry geometry, long bytes, AtomicLong clock, long cost) {
        return new MeshUploadQueue.Upload() {
            boolean released;
            public long bytes() { return bytes; }
            public boolean admitted() { return true; }
            public void upload() {
                assertFalse(released);
                released = true;
                geometry.pending--;
                clock.addAndGet(cost);
            }
            public void cancel() {
                assertFalse(released);
                released = true;
                geometry.cancellations++;
            }
        };
    }

    @Test
    void fiveHundredTwelveInstancesWithTwoHundredKeysConvergeWithoutRecapture() {
        AtomicLong clock = new AtomicLong();
        Map<Integer, Geometry> cache = new HashMap<>();
        MeshUploadQueue<Geometry> uploads = new MeshUploadQueue<>(POLICY, clock::get);
        MeshCacheResidency<Integer, Geometry> residency = new MeshCacheResidency<>(cache, 128, 4096,
                clock::get, g -> g.references, g -> !g.ready(), g -> g.bytes,
                g -> { cache.values().remove(g); uploads.cancel(g); });
        int captures = 0;
        for (int frame = 1; frame <= 180; frame++) {
            clock.set(frame * 16_000_000L);
            residency.beginFrame(frame);
            uploads.beginFrame(frame);
            uploads.drain();
            for (int instance = 0; instance < 512; instance++) {
                int key = instance % 200;
                Geometry geometry = cache.get(key);
                if (geometry == null) {
                    geometry = new Geometry();
                    cache.put(key, geometry);
                    captures++;
                    Geometry selected = geometry;
                    assertTrue(uploads.enqueue(geometry, List.of(part(geometry, 1, clock, 0),
                                    part(geometry, 1, clock, 0), part(geometry, 1, clock, 0)),
                            () -> cache.get(key) == selected, failure -> fail(failure)));
                }
                residency.request(key, geometry, Duration.ofSeconds(30));
                geometry.references++;
                // 即时调用结束，但仍持续请求的几何不进入空闲 LRU。
                geometry.references--;
            }
        }
        assertEquals(200, captures);
        assertEquals(200, cache.size());
        assertTrue(cache.values().stream().allMatch(Geometry::ready));
        assertTrue(cache.values().stream().allMatch(g -> g.cancellations == 0 && g.captured == 1));
        assertEquals(0, uploads.pendingParts());
        residency.beginFrame(182);
        assertEquals(128, cache.size(), "Only the no-longer-requested working set becomes idle");
        clock.addAndGet(Duration.ofSeconds(31).toNanos());
        residency.beginFrame(183);
        assertTrue(cache.isEmpty());
    }

    @Test
    void uploadTimeAndByteBudgetsSurviveRepeatedDrainsAndPermitOversizedFirstPart() {
        AtomicLong clock = new AtomicLong();
        Geometry geometry = new Geometry();
        MeshUploadQueue<String> queue = new MeshUploadQueue<>(POLICY, clock::get);
        assertTrue(queue.enqueue("gun", List.of(part(geometry, 32, clock, 150), part(geometry, 8, clock, 1),
                part(geometry, 8, clock, 1)), () -> true, failure -> fail(failure)));
        assertFalse(queue.enqueue("gun", List.of(), () -> true, failure -> fail(failure)), "Same key already queued");
        queue.beginFrame(1);
        queue.drain();
        assertEquals(1, queue.uploadedParts());
        assertEquals(32, queue.uploadedBytes());
        assertEquals(2, queue.pendingParts());
        queue.beginFrame(1);
        queue.drain();
        assertEquals(2, queue.pendingParts(), "Another render phase cannot reset this frame's budget");
        queue.beginFrame(2);
        queue.drain();
        assertTrue(geometry.ready());
        assertEquals(0, geometry.cancellations);
    }

    @Test
    void queueFailureDoesNotBlockOtherGeometryAndCancelsOnlyUnstartedParts() {
        AtomicLong clock = new AtomicLong();
        Geometry failed = new Geometry(), healthy = new Geometry();
        List<RuntimeException> errors = new ArrayList<>();
        MeshUploadQueue<String> queue = new MeshUploadQueue<>(POLICY, clock::get);
        queue.enqueue("failed", List.of(new MeshUploadQueue.Upload() {
            public long bytes() { return 1; }
            public boolean admitted() { return true; }
            public void upload() { throw new IllegalStateException("Driver failure"); }
            public void cancel() { fail("Started upload owns its cleanup"); }
        }, part(failed, 1, clock, 0)), () -> true, errors::add);
        queue.enqueue("healthy", List.of(part(healthy, 1, clock, 0)), () -> true, failure -> fail(failure));
        queue.beginFrame(1);
        queue.drain();
        assertEquals(1, errors.size());
        assertEquals(1, failed.cancellations);
        assertEquals(2, healthy.pending);
        assertEquals(0, queue.pendingBytes());
    }

    @Test
    void invalidationDuringUploadCannotResurrectRemainingWork() {
        AtomicLong clock = new AtomicLong();
        Geometry geometry = new Geometry();
        MeshUploadQueue<String> queue = new MeshUploadQueue<>(POLICY, clock::get);
        queue.enqueue("gun", List.of(new MeshUploadQueue.Upload() {
            public long bytes() { return 1; }
            public boolean admitted() { return true; }
            public void upload() { queue.cancel("gun"); }
            public void cancel() { fail("Running upload must not be canceled in its GL call"); }
        }, part(geometry, 1, clock, 0)), () -> true, failure -> fail(failure));
        queue.beginFrame(1);
        queue.drain();
        assertEquals(1, geometry.cancellations);
        assertFalse(queue.contains("gun"));
        assertEquals(0, queue.pendingParts());
        queue.clear();
        assertEquals(1, geometry.cancellations);
    }

    @Test
    void pendingWorkIsProtectedFromIdlePressureButExpiresWithoutRequests() {
        AtomicLong clock = new AtomicLong();
        Map<Integer, Geometry> cache = new HashMap<>();
        MeshCacheResidency<Integer, Geometry> residency = new MeshCacheResidency<>(cache, 1, 8,
                clock::get, g -> g.references, g -> !g.ready(), g -> g.bytes, g -> cache.values().remove(g));
        residency.beginFrame(1);
        for (int i = 0; i < 200; i++) {
            Geometry geometry = new Geometry();
            cache.put(i, geometry);
            residency.request(i, geometry, Duration.ofSeconds(30));
        }
        residency.beginFrame(3);
        assertEquals(200, cache.size());
        assertFalse(residency.reclaimIdle());
        clock.set(Duration.ofSeconds(31).toNanos());
        residency.beginFrame(4);
        assertTrue(cache.isEmpty());
    }
}
