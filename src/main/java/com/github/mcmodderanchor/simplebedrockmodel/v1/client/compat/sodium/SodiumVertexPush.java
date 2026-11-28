package com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.sodium;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import org.lwjgl.system.MemoryStack;

@FunctionalInterface
public interface SodiumVertexPush {
    SodiumVertexPush INSTANCE = resolve();

    void push(VertexBufferWriter writer, MemoryStack stack, long pointer, int count, VertexFormat format);

    private static SodiumVertexPush resolve() {
        try {
            VertexBufferWriter.class.getMethod("sodium$push", MemoryStack.class, long.class, int.class, VertexFormat.class);
            return Sodium0814VertexPush::push;
        } catch (NoSuchMethodException ignored) {
            return VertexBufferWriter::push;
        }
    }
}
