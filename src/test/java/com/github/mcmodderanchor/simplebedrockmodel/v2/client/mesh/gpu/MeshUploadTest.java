package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.MeshSink;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache.MeshPreparationPolicy;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache.MeshUploadQueue;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MeshUploadTest {
    @Test
    void queuedUploadKeepsStorageReadableUntilConsumedAndToleratesMeshClose() {
        AtomicReference<MeshData> captured = new AtomicReference<>();
        CompletableFuture<Void> queued = new CompletableFuture<>();
        CompletableFuture<Void> upload = MeshUpload.submit(triangle(), data -> {
            captured.set(data);
            return queued;
        });

        MeshData data = captured.get();
        assertFalse(upload.isDone());
        assertEquals(3, data.drawState().vertexCount());
        assertEquals(1F, data.vertexBuffer().getFloat(MeshSink.VERTEX_STRIDE_BYTES));
        // VertexBuffer.upload also closes MeshData before its queue future completes.
        data.close();
        queued.complete(null);
        assertDoesNotThrow(upload::join);
        assertThrows(IllegalStateException.class, data::vertexBuffer);
    }

    @Test
    void queuedFailureReleasesTheNativeStorage() {
        AtomicReference<MeshData> captured = new AtomicReference<>();
        CompletableFuture<Void> queued = new CompletableFuture<>();
        CompletableFuture<Void> upload = MeshUpload.submit(triangle(), data -> {
            captured.set(data);
            return queued;
        });
        queued.completeExceptionally(new IllegalStateException("queue stopped"));
        assertTrue(upload.isCompletedExceptionally());
        assertThrows(IllegalStateException.class, captured.get()::vertexBuffer);
    }

    @Test
    void immediatelyCompletedQueueReleasesAnUnconsumedMesh() {
        AtomicReference<MeshData> captured = new AtomicReference<>();
        CompletableFuture<Void> upload = MeshUpload.submit(triangle(), data -> {
            captured.set(data);
            return CompletableFuture.completedFuture(null);
        });
        assertDoesNotThrow(upload::join);
        assertThrows(IllegalStateException.class, captured.get()::vertexBuffer);
    }

    @Test
    void synchronousQueueRejectionReleasesTheMesh() {
        AtomicReference<MeshData> captured = new AtomicReference<>();
        IllegalStateException rejected = new IllegalStateException("queue rejected upload");
        assertSame(rejected, assertThrows(IllegalStateException.class, () -> MeshUpload.submit(triangle(), data -> {
            captured.set(data);
            throw rejected;
        })));
        assertThrows(IllegalStateException.class, captured.get()::vertexBuffer);
    }

    @Test
    void cancelledWorldQueueReleasesTheNativeStorage() {
        AtomicReference<MeshData> captured = new AtomicReference<>();
        CompletableFuture<Void> queued = new CompletableFuture<>();
        CompletableFuture<Void> upload = MeshUpload.submit(triangle(), data -> {
            captured.set(data);
            return queued;
        });
        queued.cancel(false);
        assertTrue(upload.isCompletedExceptionally());
        assertThrows(IllegalStateException.class, captured.get()::vertexBuffer);
    }

    @Test
    void synchronousPayloadFailureReleasesStorageBeforeMeshConsumption() {
        MeshUpload.Payload payload = triangle().buildBuffer();
        MeshData data = payload.data();
        IllegalStateException rejected = new IllegalStateException("retired target");
        assertSame(rejected, assertThrows(IllegalStateException.class, () -> payload.upload(mesh -> {
            assertEquals(3, mesh.drawState().vertexCount());
            throw rejected;
        })));
        assertThrows(IllegalStateException.class, data::vertexBuffer);
        assertDoesNotThrow(payload::close);
    }

    @Test
    void cancellingAdmissionBlockedCacheUploadReleasesCapturedStorage() {
        MeshUpload.Payload payload = triangle().buildBuffer();
        MeshData data = payload.data();
        assertNull(data.indexBuffer()); // Unsorted MeshData uses the shared sequential index buffer.
        assertEquals((long) data.drawState().vertexCount() * data.drawState().format().getVertexSize(), payload.bytes());
        MeshUploadQueue<String> queue = new MeshUploadQueue<>(MeshPreparationPolicy.DEFAULT, () -> 0L);
        assertTrue(queue.enqueue("triangle", List.of(new MeshUploadQueue.Upload() {
            @Override public long bytes() { return payload.bytes(); }
            @Override public boolean admitted() { return false; }
            @Override public void upload() { fail("Unadmitted geometry must remain queued"); }
            @Override public void cancel() { payload.close(); }
        }), () -> true, failure -> fail(failure)));
        queue.beginFrame(1);
        queue.drain();
        assertEquals(1F, data.vertexBuffer().getFloat(MeshSink.VERTEX_STRIDE_BYTES));
        queue.cancel("triangle");
        assertThrows(IllegalStateException.class, data::vertexBuffer);
        assertDoesNotThrow(queue::clear);
        assertDoesNotThrow(payload::close);
    }

    private static MeshSink triangle() {
        MeshSink mesh = new MeshSink().begin(VertexFormat.Mode.TRIANGLES);
        for (int i = 0; i < 3; i++) mesh.addVertex(i, 0, 0, -1, 0, 0, 0, 0, 0, 1, 0);
        return mesh;
    }
}
