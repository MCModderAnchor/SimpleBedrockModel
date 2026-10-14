package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.MeshSink;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/** Owns both the captured mesh and its native storage until upload or cancellation finishes. */
public final class MeshUpload {
    private MeshUpload() {}

    public static Payload capture(MeshSink mesh) {
        ByteBufferBuilder storage = new ByteBufferBuilder(Math.max(1536, mesh.estimatedBytes()));
        MeshData data = null;
        try {
            BufferBuilder builder = new BufferBuilder(storage, mesh.mode(), mesh.format());
            mesh.emitTo(builder);
            data = builder.buildOrThrow();
            return new Payload(storage, data);
        } catch (RuntimeException | Error failure) {
            try {
                if (data != null) data.close();
            } finally {
                storage.close();
            }
            throw failure;
        }
    }

    public static CompletableFuture<Void> submit(MeshSink mesh, Function<MeshData, CompletableFuture<Void>> uploader) {
        return submit(capture(mesh), uploader);
    }

    public static CompletableFuture<Void> submit(Payload payload, Function<MeshData, CompletableFuture<Void>> uploader) {
        try {
            return Objects.requireNonNull(uploader.apply(payload.data()), "upload future")
                    .whenComplete((ignored, failure) -> payload.close());
        } catch (RuntimeException | Error failure) {
            payload.close();
            throw failure;
        }
    }

    /** A deferred upload must retain this owner, because MeshData alone does not own its allocator. */
    public static final class Payload implements AutoCloseable {
        private final ByteBufferBuilder storage;
        private final MeshData data;
        private final long bytes;
        private boolean closed;

        private Payload(ByteBufferBuilder storage, MeshData data) {
            this.storage = storage;
            this.data = data;
            ByteBuffer indices = data.indexBuffer();
            this.bytes = (long) data.vertexBuffer().remaining() + (indices == null ? 0 : indices.remaining());
        }

        public MeshData data() { return data; }
        public long bytes() { return bytes; }

        /** Handles synchronous uploads, including failures before the uploader consumes MeshData. */
        public void upload(Consumer<MeshData> uploader) {
            try {
                uploader.accept(data);
            } finally {
                close();
            }
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            try {
                data.close();
            } finally {
                storage.close();
            }
        }
    }
}
