package com.github.mcmodderanchor.simplebedrockmodel.v2.client.world;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

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

    private static MeshSink triangle() {
        MeshSink mesh = new MeshSink().begin(VertexFormat.Mode.TRIANGLES);
        for (int i = 0; i < 3; i++) mesh.addVertex(i, 0, 0, -1, 0, 0, 0, 0, 0, 1, 0);
        return mesh;
    }
}
