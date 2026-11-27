package com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.sodium;

import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import org.lwjgl.system.MemoryStack;

import java.lang.reflect.Field;

public final class SodiumVertexBufferWriter {
    private static final Class<?> VERTEX_FORMAT = resolveVertexFormat();
    private static final java.lang.reflect.Method PUSH = resolvePush();

    private SodiumVertexBufferWriter() {}

    public static void push(VertexBufferWriter writer, MemoryStack stack, long pointer, int vertexCount,
                            Object format) {
        try {
            PUSH.invoke(writer, stack, pointer, vertexCount, format);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Failed to push vertices through the Sodium API", cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to push vertices through the Sodium API", exception);
        }
    }

    public static Object format(String vertexType) {
        try {
            Field field = Class.forName("net.caffeinemc.mods.sodium.api.vertex.format.common." + vertexType)
                    .getField("FORMAT");
            return field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to resolve Sodium " + vertexType + " format", exception);
        }
    }

    private static java.lang.reflect.Method resolvePush() {
        try {
            return VertexBufferWriter.class.getMethod("sodium$push", MemoryStack.class, long.class, int.class, VERTEX_FORMAT);
        } catch (NoSuchMethodException ignored) {
            try {
                return VertexBufferWriter.class.getMethod("push", MemoryStack.class, long.class, int.class, VERTEX_FORMAT);
            } catch (NoSuchMethodException exception) {
                throw new IllegalStateException("Unsupported Sodium VertexBufferWriter API: no push method found", exception);
            }
        }
    }

    private static Class<?> resolveVertexFormat() {
        try {
            return Class.forName("com.mojang.renderpearl.api.vertex.VertexFormat");
        } catch (ClassNotFoundException ignored) {
            return com.mojang.blaze3d.vertex.VertexFormat.class;
        }
    }
}