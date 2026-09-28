package com.github.mcmodderanchor.simplebedrockmodel.v2.client.world;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Owns the native vertex storage until the queued upload has consumed it. */
final class MeshUpload {
    private MeshUpload() {}

    static CompletableFuture<Void> submit(MeshSink mesh, Function<MeshData, CompletableFuture<Void>> uploader) {
        ByteBufferBuilder storage = new ByteBufferBuilder(Math.max(1536, mesh.estimatedBytes()));
        MeshData data = null;
        try {
            BufferBuilder builder = new BufferBuilder(storage, mesh.mode(), mesh.format());
            mesh.emitTo(builder);
            data = builder.buildOrThrow();
            MeshData uploaded = data;
            return uploader.apply(uploaded).whenComplete((ignored, failure) -> {
                try {
                    uploaded.close();
                } finally {
                    storage.close();
                }
            });
        } catch (RuntimeException | Error exception) {
            try {
                if (data != null) data.close();
            } finally {
                storage.close();
            }
            throw exception;
        }
    }
}
