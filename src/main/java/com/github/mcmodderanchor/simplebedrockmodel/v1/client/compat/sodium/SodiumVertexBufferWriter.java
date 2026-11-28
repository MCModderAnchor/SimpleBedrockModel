package com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.sodium;

import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import org.lwjgl.system.MemoryStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class SodiumVertexBufferWriter {
    private static final Class<?> VERTEX_FORMAT = resolveVertexFormat();
    private static final MethodHandle PUSH = resolvePush();
    private static final Object ENTITY_FORMAT = resolveFormat("EntityVertex");
    private static final Object PARTICLE_FORMAT = resolveFormat("ParticleVertex");

    private SodiumVertexBufferWriter() {}

    public static void push(VertexBufferWriter writer, MemoryStack stack, long pointer, int vertexCount,
                            Object format) {
        try {
            PUSH.invokeExact(writer, stack, pointer, vertexCount, format);
        } catch (Throwable exception) {
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (exception instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Failed to push vertices through the Sodium API", exception);
        }
    }

    public static Object entityFormat() {
        return ENTITY_FORMAT;
    }

    public static Object particleFormat() {
        return PARTICLE_FORMAT;
    }

    private static Object resolveFormat(String vertexType) {
        try {
            Field field = Class.forName("net.caffeinemc.mods.sodium.api.vertex.format.common." + vertexType)
                    .getField("FORMAT");
            return field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to resolve Sodium " + vertexType + " format", exception);
        }
    }

    private static MethodHandle resolvePush() {
        try {
            Method method = findPush("sodium$push");
            if (method == null) {
                method = findPush("push");
            }
            if (method == null) {
                throw new IllegalStateException("Unsupported Sodium VertexBufferWriter API: no push method found");
            }
            return MethodHandles.publicLookup().unreflect(method)
                    .asType(MethodType.methodType(void.class, VertexBufferWriter.class,
                            MemoryStack.class, long.class, int.class, Object.class));
        } catch (NoSuchMethodException ignored) {
            throw new IllegalStateException("Unsupported Sodium VertexBufferWriter API: no accessible push method found", ignored);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access Sodium VertexBufferWriter push method", exception);
        }
    }

    private static Method findPush(String name) throws NoSuchMethodException {
        try {
            return VertexBufferWriter.class.getMethod(name, MemoryStack.class, long.class, int.class, VERTEX_FORMAT);
        } catch (NoSuchMethodException ignored) {
            return null;
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