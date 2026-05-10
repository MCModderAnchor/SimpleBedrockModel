package com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.sodium;

import com.mojang.blaze3d.vertex.VertexConsumer;
import me.jellysquid.mods.sodium.client.render.vertex.VertexConsumerUtils;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.ParticleVertex;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Sodium/Embeddium 粒子顶点快速写入路径，测试
 */
public final class SodiumParticleVertexWriter {
    private static final int PARTICLE_VERTEX_COUNT = 4;
    private static final long PARTICLE_SCRATCH_BUFFER = MemoryUtil.nmemAlignedAlloc(64, (long) PARTICLE_VERTEX_COUNT * ParticleVertex.STRIDE);

    private SodiumParticleVertexWriter() {}

    public static boolean tryRender(VertexConsumer consumer,
                                    float x0, float y0, float z0, float u0, float v0,
                                    float x1, float y1, float z1, float u1, float v1,
                                    float x2, float y2, float z2, float u2, float v2,
                                    float x3, float y3, float z3, float u3, float v3,
                                    float r, float g, float b, float a,
                                    int light) {
        VertexBufferWriter writer = VertexConsumerUtils.convertOrLog(consumer);
        if (writer == null) {
            return false;
        }

        int color = packColor(r, g, b, a);
        long ptr = PARTICLE_SCRATCH_BUFFER;

        ParticleVertex.put(ptr, x0, y0, z0, u0, v0, color, light);
        ptr += ParticleVertex.STRIDE;
        ParticleVertex.put(ptr, x1, y1, z1, u1, v1, color, light);
        ptr += ParticleVertex.STRIDE;
        ParticleVertex.put(ptr, x2, y2, z2, u2, v2, color, light);
        ptr += ParticleVertex.STRIDE;
        ParticleVertex.put(ptr, x3, y3, z3, u3, v3, color, light);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            writer.push(stack, PARTICLE_SCRATCH_BUFFER, PARTICLE_VERTEX_COUNT, ParticleVertex.FORMAT);
        }
        return true;
    }

    private static int packColor(float r, float g, float b, float a) {
        return (int) (a * 255.0f) << 24 | (int) (b * 255.0f) << 16 | (int) (g * 255.0f) << 8 | (int) (r * 255.0f);
    }
}
