package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.render.MeshBatchRenderer;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.WeakHashMap;

import static org.lwjgl.opengl.GL45.*;

// 雪暴粒子优化的尝试
// 采用了和ar相同的思路
public class WorldParticleCompute {
    private static final ResourceLocation KERNEL = SimpleBedrockModel.modLoc("shaders/core/world_particle_expand.comp");
    private static final int CAPACITY = 16_384;
    private static final int RECORD_STRIDE = 48;
    private static final int VERTEX_STRIDE = 28;
    private static final int QUAD_VERTICES = 4;
    private static final int QUAD_INDICES = 6;
    private static final int WORK_GROUP_SIZE = 128;

    private static WorldParticleCompute instance;
    private static boolean unavailable;

    private final ByteBuffer records = MemoryUtil.memAlloc(CAPACITY * RECORD_STRIDE);
    private final long recordBase = MemoryUtil.memAddress(records);
    private final SnowStormParticle[] collected = new SnowStormParticle[CAPACITY];
    private final Vector3f right = new Vector3f();
    private final Vector3f up = new Vector3f();
    private final WeakHashMap<ShaderInstance, Boolean> compatibleShaders = new WeakHashMap<>();
    private final int[] storageBuffers = new int[2];
    private final long[] storageOffsets = new long[2];
    private final long[] storageSizes = new long[2];

    private int program;
    private int vao;
    private int indices;
    private int input;
    private int output;
    private boolean loggedDraw;
    private boolean loggedFallback;

    private BufferBuilder builder;
    private Camera camera;
    private Vec3 eye;
    private float partialTick;
    private int count;
    private boolean collecting;

    @Nullable
    public static WorldParticleCompute begin(BufferBuilder builder) {
        if (!WorldParticleConfig.SPEC.isLoaded() || !WorldParticleConfig.COMPUTE.get() || unavailable
                || !RenderSystem.isOnRenderThread() || !GL.getCapabilities().OpenGL45) {
            return null;
        }
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null || shader.getVertexFormat() != DefaultVertexFormat.PARTICLE) {
            return null;
        }
        if (instance == null) {
            var candidate = new WorldParticleCompute();
            try {
                candidate.initialize();
                instance = candidate;
            } catch (IOException | RuntimeException failure) {
                candidate.close();
                disable(failure);
                return null;
            }
        }
        if (instance.builder != null || !instance.compatibleShaders.computeIfAbsent(shader, WorldParticleCompute::canDraw)) {
            return null;
        }
        instance.builder = builder;
        instance.collecting = true;
        instance.records.clear();
        return instance;
    }

    public boolean collect(SnowStormParticle particle, VertexConsumer consumer, Camera camera, float partialTick) {
        if (!collecting || consumer != builder) {
            return false;
        }
        if (!particle.supportsCompute() || count == CAPACITY
                || (count != 0 && (camera != this.camera || partialTick != this.partialTick))) {
            fallBack();
            return false;
        }
        if (count == 0) {
            this.camera = camera;
            this.eye = camera.getPosition();
            this.partialTick = partialTick;
            right.set(1, 0, 0).rotate(camera.rotation());
            up.set(0, 1, 0).rotate(camera.rotation());
        }
        particle.writeComputeRecord(recordBase + (long) count * RECORD_STRIDE, eye, partialTick);
        collected[count++] = particle;
        return true;
    }

    private void fallBack() {
        if (count > 0 && !loggedFallback) {
            SimpleBedrockModel.LOGGER.debug("World particle compute is replaying {} staged particles in order", count);
            loggedFallback = true;
        }
        replay();
        collecting = false;
    }

    public void end(Tesselator tesselator) {
        try {
            ShaderInstance shader = RenderSystem.getShader();
            if (count > 0 && builder.isCurrentBatchEmpty() && shader != null
                    && compatibleShaders.computeIfAbsent(shader, WorldParticleCompute::canDraw) && expand()) {
                tesselator.end();
                draw(shader);
            } else {
                replay();
                tesselator.end();
            }
        } finally {
            resetBatch();
        }
    }

    private void replay() {
        for (int i = 0; i < count; i++) {
            collected[i].renderCpu(builder, camera, partialTick);
        }
        Arrays.fill(collected, 0, count, null);
        count = 0;
    }

    private void resetBatch() {
        Arrays.fill(collected, 0, count, null);
        count = 0;
        builder = null;
        camera = null;
        eye = null;
        collecting = false;
    }

    public static void writeRecord(long address, float x, float y, float z, float roll, ParticleInstance data,
                            float red, float green, float blue, float alpha, int light) {
        int color = (int) (alpha * 255) << 24 | (int) (blue * 255) << 16
                | (int) (green * 255) << 8 | (int) (red * 255);
        MemoryUtil.memPutFloat(address, x);
        MemoryUtil.memPutFloat(address + 4, y);
        MemoryUtil.memPutFloat(address + 8, z);
        MemoryUtil.memPutFloat(address + 12, roll);
        MemoryUtil.memPutFloat(address + 16, data.width * data.spawnScale);
        MemoryUtil.memPutFloat(address + 20, data.height * data.spawnScale);
        MemoryUtil.memPutInt(address + 24, color);
        MemoryUtil.memPutInt(address + 28, light);
        MemoryUtil.memPutFloat(address + 32, data.u0);
        MemoryUtil.memPutFloat(address + 36, data.v0);
        MemoryUtil.memPutFloat(address + 40, data.u1);
        MemoryUtil.memPutFloat(address + 44, data.v1);
    }

    private void initialize() throws IOException {
        program = loadProgram();
        vao = glCreateVertexArrays();
        indices = glCreateBuffers();
        input = glCreateBuffers();
        output = glCreateBuffers();
        if (vao == 0 || indices == 0 || input == 0 || output == 0) {
            throw new IllegalStateException("Could not allocate world particle buffer objects");
        }

        IntBuffer quadIndices = MemoryUtil.memAllocInt(CAPACITY * QUAD_INDICES);
        try {
            for (int i = 0; i < CAPACITY; i++) {
                int base = i * QUAD_VERTICES;
                quadIndices.put(base).put(base + 1).put(base + 2).put(base + 2).put(base + 3).put(base);
            }
            glNamedBufferData(indices, quadIndices.flip(), GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(quadIndices);
        }

        glVertexArrayElementBuffer(vao, indices);
        glVertexArrayVertexBuffer(vao, 0, output, 0, VERTEX_STRIDE);
        glVertexArrayAttribFormat(vao, 0, 3, GL_FLOAT, false, 0);
        glVertexArrayAttribFormat(vao, 1, 2, GL_FLOAT, false, 12);
        glVertexArrayAttribFormat(vao, 2, 4, GL_UNSIGNED_BYTE, true, 20);
        glVertexArrayAttribIFormat(vao, 3, 2, GL_SHORT, 24);
        for (int attribute = 0; attribute < 4; attribute++) {
            glVertexArrayAttribBinding(vao, attribute, 0);
            glEnableVertexArrayAttrib(vao, attribute);
        }
    }

    private static int loadProgram() throws IOException {
        String source;
        try (var stream = Minecraft.getInstance().getResourceManager().open(KERNEL)) {
            source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        int shader = glCreateShader(GL_COMPUTE_SHADER);
        try {
            glShaderSource(shader, source);
            glCompileShader(shader);
            if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
                throw new IOException(glGetShaderInfoLog(shader));
            }
            int program = glCreateProgram();
            glAttachShader(program, shader);
            glLinkProgram(program);
            glDetachShader(program, shader);
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
                String log = glGetProgramInfoLog(program);
                glDeleteProgram(program);
                throw new IOException(log);
            }
            return program;
        } finally {
            glDeleteShader(shader);
        }
    }

    private static boolean canDraw(ShaderInstance shader) {
        if (shader.getVertexFormat() != DefaultVertexFormat.PARTICLE) return false;
        if (!shader.getClass().getName().equals("net.irisshaders.iris.pipeline.programs.ExtendedShader")) return true;
        try {
            // Oculus and Iris expose these on their extended program without a hard dependency.
            return shader.getClass().getMethod("getTessControl").invoke(shader) == null
                    && shader.getClass().getMethod("getTessEval").invoke(shader) == null;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private boolean expand() {
        try {
            records.limit(count * RECORD_STRIDE);
            glNamedBufferData(input, records, GL_STREAM_DRAW);
            glNamedBufferData(output, (long) count * QUAD_VERTICES * VERTEX_STRIDE, GL_STREAM_DRAW);

            int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
            int previousStorage = glGetInteger(GL_SHADER_STORAGE_BUFFER_BINDING);
            for (int slot = 0; slot < storageBuffers.length; slot++) {
                storageBuffers[slot] = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, slot);
                if (storageBuffers[slot] != 0) {
                    storageOffsets[slot] = glGetInteger64i(GL_SHADER_STORAGE_BUFFER_START, slot);
                    storageSizes[slot] = glGetInteger64i(GL_SHADER_STORAGE_BUFFER_SIZE, slot);
                }
            }
            try {
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, input);
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, output);
                GlStateManager._glUseProgram(program);
                glUniform1ui(0, count);
                glUniform3f(1, right.x, right.y, right.z);
                glUniform3f(2, up.x, up.y, up.z);
                glDispatchCompute((count + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE, 1, 1);
                glMemoryBarrier(GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);
            } finally {
                GlStateManager._glUseProgram(previousProgram);
                for (int slot = 0; slot < storageBuffers.length; slot++) {
                    if (storageBuffers[slot] != 0 && storageSizes[slot] > 0) {
                        glBindBufferRange(GL_SHADER_STORAGE_BUFFER, slot, storageBuffers[slot],
                                storageOffsets[slot], storageSizes[slot]);
                    } else {
                        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, slot, storageBuffers[slot]);
                    }
                }
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, previousStorage);
            }
            return true;
        } catch (RuntimeException failure) {
            disable(failure);
            return false;
        }
    }

    private void draw(ShaderInstance shader) {
        BufferUploader.reset();
        GlStateManager._glBindVertexArray(vao);
        try {
            MeshBatchRenderer.setSharedUniforms(shader, RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix());
            if (shader.SCREEN_SIZE != null) {
                var window = Minecraft.getInstance().getWindow();
                shader.SCREEN_SIZE.set((float) window.getWidth(), (float) window.getHeight());
            }
            RenderSystem.setupShaderLights(shader);
            shader.apply();
            RenderSystem.drawElements(GL_TRIANGLES, count * QUAD_INDICES, GL_UNSIGNED_INT);
            if (!loggedDraw) {
                SimpleBedrockModel.LOGGER.debug("World particle compute submitted its first batch ({} particles)", count);
                loggedDraw = true;
            }
        } finally {
            try {
                shader.clear();
            } finally {
                VertexBuffer.unbind();
            }
        }
    }

    private static void disable(Exception failure) {
        unavailable = true;
        SimpleBedrockModel.LOGGER.warn("World particle compute is unavailable. Using CPU rendering.", failure);
    }

    public static void invalidate() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(WorldParticleCompute::invalidate);
            return;
        }
        if (instance != null) {
            instance.close();
            instance = null;
        }
        unavailable = false;
    }

    private void close() {
        resetBatch();
        glDeleteVertexArrays(vao);
        glDeleteBuffers(indices);
        glDeleteBuffers(input);
        glDeleteBuffers(output);
        glDeleteProgram(program);
        MemoryUtil.memFree(records);
    }
}
