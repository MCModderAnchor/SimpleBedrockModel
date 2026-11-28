package com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.sodium;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import org.lwjgl.system.MemoryStack;

public final class Sodium0814VertexPush {
    private Sodium0814VertexPush() {}

    public static void push(VertexBufferWriter writer, MemoryStack stack, long pointer, int count, VertexFormat format) {
        writer.sodium$push(stack, pointer, count, format);
    }
}
