package example.client.particle;

import com.github.argon4w.acceleratedrendering.core.backends.programs.ComputeProgram;
import com.github.argon4w.acceleratedrendering.core.backends.programs.Uniform;
import com.github.argon4w.acceleratedrendering.core.programs.ComputeShaderProgramLoader;
import com.github.argon4w.acceleratedrendering.core.programs.ComputeShaderPrograms;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.ParticleAppearanceBillboard.FaceCameraMode;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world.SnowStormParticle;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.render.MeshBatchRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.lwjgl.opengl.GL46.*;

/** Development-only backends for a single, order-preserving particle batch. */
abstract class ParticleGpuPrototype implements AutoCloseable {
    private static final int QUAD_VERTICES = 4;
    private static final int QUAD_INDICES = 6;
    private static final int PARTICLE_STRIDE = 28;
    private static final int COMPUTE_LOCAL_SIZE = 128;
    final Vector3f right = new Vector3f();
    final Vector3f up = new Vector3f();
    final int vao = glCreateVertexArrays();
    private final int indexBuffer = glCreateBuffers();
    private Vec3 eye;
    int count;

    ParticleGpuPrototype(int indexQuads) {
        IntBuffer indices = MemoryUtil.memAllocInt(indexQuads * QUAD_INDICES);
        try {
            for (int i = 0; i < indexQuads; i++) {
                int v = i * QUAD_VERTICES;
                indices.put(v).put(v + 1).put(v + 2).put(v + 2).put(v + 3).put(v);
            }
            glNamedBufferData(indexBuffer, indices.flip(), GL_STATIC_DRAW);
            glVertexArrayElementBuffer(vao, indexBuffer);
        } finally {
            MemoryUtil.memFree(indices);
        }
    }

    void begin() {
        count = 0;
    }

    final void push(Camera camera, double x, double y, double z, float roll, ParticleInstance data,
                    float r, float g, float b, float a, int light) {
        if (count == 0) {
            eye = camera.getPosition();
            right.set(1, 0, 0).rotate(camera.rotation());
            up.set(0, 1, 0).rotate(camera.rotation());
        }
        int color = (int) (a * 255) << 24 | (int) (b * 255) << 16 | (int) (g * 255) << 8 | (int) (r * 255);
        pack((float) (x - eye.x), (float) (y - eye.y), (float) (z - eye.z), roll, data, color, light);
        count++;
    }

    abstract void pack(float x, float y, float z, float roll, ParticleInstance data, int color, int light);

    abstract void draw();

    final void bindDraw(ShaderInstance shader) {
        BufferUploader.reset();
        glBindVertexArray(vao);
        MeshBatchRenderer.uploadSharedUniforms(shader, RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix());
    }

    static void endDraw(ShaderInstance shader) {
        shader.clear();
        VertexBuffer.unbind();
    }

    final void drawExpanded() {
        ShaderInstance shader = Objects.requireNonNull(RenderSystem.getShader());
        bindDraw(shader);
        try {
            glDrawElements(GL_TRIANGLES, count * QUAD_INDICES, GL_UNSIGNED_INT, 0L);
        } finally {
            endDraw(shader);
        }
    }

    final void attribute(int index, int elements, int type, boolean normalized, int offset) {
        glEnableVertexArrayAttrib(vao, index);
        glVertexArrayAttribFormat(vao, index, elements, type, normalized, offset);
        glVertexArrayAttribBinding(vao, index, 0);
    }

    final void lightAttribute(int index, int offset) {
        glEnableVertexArrayAttrib(vao, index);
        glVertexArrayAttribIFormat(vao, index, 2, GL_SHORT, offset);
        glVertexArrayAttribBinding(vao, index, 0);
    }

    @Override
    public void close() {
        glDeleteVertexArrays(vao);
        glDeleteBuffers(indexBuffer);
    }

    private abstract static class Compact extends ParticleGpuPrototype {
        static final int RECORD_STRIDE = 48;
        final int inputBuffer = glCreateBuffers();
        private final ByteBuffer records;
        private final long recordAddress;
        private final int capacity;
        private final int stride;
        private boolean rawRecordWrites;

        Compact(int capacity, int indexQuads) {
            this(capacity, indexQuads, RECORD_STRIDE);
        }

        Compact(int capacity, int indexQuads, int stride) {
            super(indexQuads);
            this.capacity = capacity;
            this.stride = stride;
            records = MemoryUtil.memAlloc(capacity * stride);
            recordAddress = MemoryUtil.memAddress(records);
        }

        final void setRawRecordWrites(boolean rawRecordWrites) {
            this.rawRecordWrites = rawRecordWrites;
        }

        @Override
        void begin() {
            super.begin();
            records.clear();
        }

        @Override
        final void pack(float x, float y, float z, float roll, ParticleInstance data, int color, int light) {
            if (rawRecordWrites) {
                packByAddress(x, y, z, roll, data, color, light);
                return;
            }
            records.putFloat(x).putFloat(y).putFloat(z).putFloat(roll);
            records.putFloat(data.width * data.spawnScale).putFloat(data.height * data.spawnScale);
            records.putInt(color).putInt(light);
            records.putFloat(data.u0).putFloat(data.v0).putFloat(data.u1).putFloat(data.v1);
        }

        final long nextRecordAddress() {
            if (count >= capacity) throw new IllegalStateException("Particle record buffer is full");
            return recordAddress + (long) count * stride;
        }

        private void packByAddress(float x, float y, float z, float roll, ParticleInstance data, int color, int light) {
            long address = nextRecordAddress();
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

        final void upload() {
            if (rawRecordWrites) {
                records.limit(count * stride);
            } else {
                records.flip();
            }
            glNamedBufferData(inputBuffer, records, GL_STREAM_DRAW);
        }

        @Override
        public void close() {
            super.close();
            glDeleteBuffers(inputBuffer);
            MemoryUtil.memFree(records);
        }
    }

    static final class Instanced extends Compact {
        private final ShaderInstance shader;

        Instanced(int capacity, ShaderInstance shader) {
            super(capacity, 1);
            this.shader = shader;
            glVertexArrayVertexBuffer(vao, 0, inputBuffer, 0, RECORD_STRIDE);
            glVertexArrayBindingDivisor(vao, 0, 1);
            attribute(input("CenterRoll"), 4, GL_FLOAT, false, 0);
            attribute(input("HalfSize"), 2, GL_FLOAT, false, 16);
            attribute(input("Color"), 4, GL_UNSIGNED_BYTE, true, 24);
            lightAttribute(input("UV2"), 28);
            attribute(input("UVBounds"), 4, GL_FLOAT, false, 32);
        }

        private int input(String name) {
            int location = glGetAttribLocation(shader.getId(), name);
            if (location < 0) throw new IllegalStateException("Missing instanced particle attribute " + name);
            return location;
        }

        @Override
        void draw() {
            upload();
            shader.safeGetUniform("CameraRight").set(right.x, right.y, right.z);
            shader.safeGetUniform("CameraUp").set(up.x, up.y, up.z);
            bindDraw(shader);
            try {
                glDrawElementsInstanced(GL_TRIANGLES, QUAD_INDICES, GL_UNSIGNED_INT, 0L, count);
            } finally {
                endDraw(shader);
            }
        }

        @Override
        public void close() {
            super.close();
            shader.close();
        }
    }

    static class CustomCompute extends Compact {
        private static final ResourceLocation KERNEL = new ResourceLocation("example", "shaders/core/world_particle_expand.comp");
        private final int outputBuffer = glCreateBuffers();
        private final StorageBindings storage = new StorageBindings(0, 1);
        private final int program;

        static int loadProgram(ResourceProvider resources) throws IOException {
            return loadProgram(resources, KERNEL);
        }

        static int loadProgram(ResourceProvider resources, ResourceLocation kernel) throws IOException {
            String source;
            try (var stream = resources.open(kernel)) {
                source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            int shader = glCreateShader(GL_COMPUTE_SHADER);
            try {
                glShaderSource(shader, source);
                glCompileShader(shader);
                if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
                    throw new IOException(kernel + " compilation failed: " + glGetShaderInfoLog(shader));
                }
                int program = glCreateProgram();
                glAttachShader(program, shader);
                glLinkProgram(program);
                glDetachShader(program, shader);
                if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
                    String log = glGetProgramInfoLog(program);
                    glDeleteProgram(program);
                    throw new IOException(kernel + " linking failed: " + log);
                }
                return program;
            } finally {
                glDeleteShader(shader);
            }
        }

        CustomCompute(int capacity, int program) {
            this(capacity, program, RECORD_STRIDE);
        }

        CustomCompute(int capacity, int program, int recordStride) {
            super(capacity, capacity, recordStride);
            this.program = program;
            glVertexArrayVertexBuffer(vao, 0, outputBuffer, 0, PARTICLE_STRIDE);
            attribute(0, 3, GL_FLOAT, false, 0);
            attribute(1, 2, GL_FLOAT, false, 12);
            attribute(2, 4, GL_UNSIGNED_BYTE, true, 20);
            lightAttribute(3, 24);
        }

        @Override
        void draw() {
            upload();
            glNamedBufferData(outputBuffer, (long) count * QUAD_VERTICES * PARTICLE_STRIDE, GL_STREAM_DRAW);
            storage.save();
            try {
                storage.bind(0, inputBuffer);
                storage.bind(1, outputBuffer);
                glUseProgram(program);
                setUniforms();
                glDispatchCompute((count + COMPUTE_LOCAL_SIZE - 1) / COMPUTE_LOCAL_SIZE, 1, 1);
                glMemoryBarrier(GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);
            } finally {
                glUseProgram(0);
                storage.restore();
            }
            drawExpanded();
        }

        void setUniforms() {
            glUniform1ui(0, count);
            glUniform3f(1, right.x, right.y, right.z);
            glUniform3f(2, up.x, up.y, up.z);
        }

        @Override
        public void close() {
            super.close();
            glDeleteBuffers(outputBuffer);
            glDeleteProgram(program);
        }
    }

    static final class LookAtCompute extends CustomCompute {
        private static final ResourceLocation KERNEL = new ResourceLocation("example", "shaders/core/world_particle_look_at.comp");
        private static final MethodHandle CPU_FACING = cpuFacingMethod();
        private final Quaternionf facing = new Quaternionf();
        private final Vector3f axisX = new Vector3f();
        private final Vector3f axisY = new Vector3f();
        private int gpuFacingMode;

        LookAtCompute(int capacity, ResourceProvider resources) throws IOException {
            super(capacity, loadProgram(resources, KERNEL), RECORD_STRIDE + 32);
            setRawRecordWrites(true);
        }

        void setFacingMode(FaceCameraMode mode, boolean gpu) {
            gpuFacingMode = gpu ? switch (mode) {
                case LOOKAT_XYZ -> 1;
                case LOOKAT_Y -> 2;
                case LOOKAT_DIRECTION -> 3;
                default -> throw new IllegalArgumentException("Expected a look-at facing mode");
            } : 0;
        }

        void pushFacing(SnowStormParticle particle, Camera camera, double currentX, double currentY, double currentZ,
                        double x, double y, double z, float roll, ParticleInstance data,
                        float r, float g, float b, float a, int light) {
            long extra = nextRecordAddress() + RECORD_STRIDE;
            if (gpuFacingMode == 0) {
                // Exercise the exact CPU implementation without exposing a production benchmark API.
                facing.identity();
                try {
                    CPU_FACING.invokeExact(particle, facing, camera);
                } catch (Throwable failure) {
                    throw new IllegalStateException("Could not evaluate the CPU particle orientation", failure);
                }
                if (roll != 0) facing.rotateZ(roll);
                axisX.set(1, 0, 0).rotate(facing).mul(data.width * data.spawnScale);
                axisY.set(0, 1, 0).rotate(facing).mul(data.height * data.spawnScale);
                writeVector(extra, axisX.x, axisX.y, axisX.z);
                writeVector(extra + 16, axisY.x, axisY.y, axisY.z);
            } else {
                var eye = camera.getPosition();
                writeVector(extra, (float) (eye.x - currentX), (float) (eye.y - currentY), (float) (eye.z - currentZ));
                writeVector(extra + 16, data.vx, data.vy, data.vz);
            }
            push(camera, x, y, z, roll, data, r, g, b, a, light);
        }

        private static void writeVector(long address, float x, float y, float z) {
            MemoryUtil.memPutFloat(address, x);
            MemoryUtil.memPutFloat(address + 4, y);
            MemoryUtil.memPutFloat(address + 8, z);
        }

        private static MethodHandle cpuFacingMethod() {
            try {
                return MethodHandles.privateLookupIn(SnowStormParticle.class, MethodHandles.lookup()).findVirtual(
                        SnowStormParticle.class, "applyFacingMode", MethodType.methodType(void.class, Quaternionf.class, Camera.class));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Could not find the CPU particle orientation method", e);
            }
        }

        @Override
        void setUniforms() {
            super.setUniforms();
            glUniform1i(3, gpuFacingMode);
        }
    }

    private static final class StorageBindings {
        private final int[] slots;
        private final int[] buffers;
        private final long[] offsets;
        private final long[] sizes;
        private int generic;

        StorageBindings(int... slots) {
            this.slots = slots;
            buffers = new int[slots.length];
            offsets = new long[slots.length];
            sizes = new long[slots.length];
        }

        void save() {
            generic = glGetInteger(GL_SHADER_STORAGE_BUFFER_BINDING);
            for (int i = 0; i < slots.length; i++) {
                int slot = slots[i];
                buffers[i] = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, slot);
                if (buffers[i] != 0) {
                    offsets[i] = glGetInteger64i(GL_SHADER_STORAGE_BUFFER_START, slot);
                    sizes[i] = glGetInteger64i(GL_SHADER_STORAGE_BUFFER_SIZE, slot);
                }
            }
        }

        void bind(int index, int buffer) {
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, slots[index], buffer);
        }

        void restore() {
            for (int i = 0; i < slots.length; i++) {
                if (buffers[i] != 0 && sizes[i] > 0) {
                    glBindBufferRange(GL_SHADER_STORAGE_BUFFER, slots[i], buffers[i], offsets[i], sizes[i]);
                } else {
                    bind(i, buffers[i]);
                }
            }
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, generic);
        }
    }

    static final class ArCompute extends ParticleGpuPrototype {
        // std430 layouts of AR 8179092 pos_color_tex_light_mesh_uploading_shader.
        private static final int SHARING_STRIDE = 112;
        private static final int MESH_INFO_STRIDE = 28;
        private static final int VARYING_STRIDE = 16;
        private static final float V_MAX = 0.125f;
        private final int[] buffers = new int[5];
        private final StorageBindings storage = new StorageBindings(1, 2, 4, 7, 8);
        private final ByteBuffer sharingRecords;
        private final ByteBuffer meshRecords;
        private final ComputeProgram program;
        private final Uniform meshCount;
        private final Uniform meshSize;
        private final Uniform meshOffset;

        static ComputeProgram loadedProgram() {
            ComputeProgram program = ComputeShaderProgramLoader.getProgram(
                    ComputeShaderPrograms.CORE_POS_COLOR_TEX_LIGHT_MESH_UPLOADING_KEY);
            if (program == null || !program.isLinked()) {
                throw new IllegalStateException("AR's PARTICLE mesh uploading program did not load");
            }
            return program;
        }

        ArCompute(int capacity, ComputeProgram program) {
            super(capacity);
            this.program = program;
            meshCount = program.getUniform("meshCount");
            meshSize = program.getUniform("meshSize");
            meshOffset = program.getUniform("meshOffset");
            if (DefaultVertexFormat.PARTICLE.getVertexSize() != PARTICLE_STRIDE) {
                throw new IllegalStateException("Unexpected vanilla PARTICLE vertex stride");
            }
            sharingRecords = MemoryUtil.memAlloc(capacity * SHARING_STRIDE);
            meshRecords = MemoryUtil.memAlloc(capacity * MESH_INFO_STRIDE);
            glCreateBuffers(buffers);
            glNamedBufferData(buffers[2], (long) capacity * QUAD_VERTICES * VARYING_STRIDE, GL_STREAM_DRAW);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer quad = stack.malloc(QUAD_VERTICES * PARTICLE_STRIDE);
                vertex(quad, -1, -1, 0, V_MAX);
                vertex(quad, -1, 1, 0, 0);
                vertex(quad, 1, 1, 1, 0);
                vertex(quad, 1, -1, 1, V_MAX);
                glNamedBufferData(buffers[3], quad.flip(), GL_STATIC_DRAW);
            }
            glVertexArrayVertexBuffer(vao, 0, buffers[0], 0, PARTICLE_STRIDE);
            // AR stores Color before UV0, while the vanilla shader binds UV0 before Color.
            attribute(0, 3, GL_FLOAT, false, 0);
            attribute(1, 2, GL_FLOAT, false, 16);
            attribute(2, 4, GL_UNSIGNED_BYTE, true, 12);
            lightAttribute(3, 24);
        }

        private static void vertex(ByteBuffer out, float x, float y, float u, float v) {
            out.putFloat(x).putFloat(y).putFloat(0).putInt(-1).putFloat(u).putFloat(v).putInt(0);
        }

        @Override
        void begin() {
            super.begin();
            sharingRecords.clear();
            meshRecords.clear();
        }

        @Override
        void pack(float x, float y, float z, float roll, ParticleInstance data, int color, int light) {
            if (data.u0 != 0 || data.v0 != 0 || data.u1 != 1 || data.v1 != V_MAX) {
                throw new IllegalArgumentException("AR prototype requires the common test quad's fixed UV rectangle");
            }
            float w = data.width * data.spawnScale;
            float h = data.height * data.spawnScale;
            float c = (float) Math.cos(roll);
            float s = (float) Math.sin(roll);
            column((right.x * c + up.x * s) * w, (right.y * c + up.y * s) * w, (right.z * c + up.z * s) * w, 0);
            column((up.x * c - right.x * s) * h, (up.y * c - right.y * s) * h, (up.z * c - right.z * s) * h, 0);
            column(0, 0, 1, 0);
            column(x, y, z, 1);
            column(1, 0, 0, 0);
            column(0, 1, 0, 0);
            column(0, 0, 1, 0);

            int offset = count * QUAD_VERTICES;
            meshRecords.putInt(offset).putInt(offset).putInt(count).putInt(0).putInt(color).putInt(0).putInt(light);
        }

        private void column(float x, float y, float z, float w) {
            sharingRecords.putFloat(x).putFloat(y).putFloat(z).putFloat(w);
        }

        @Override
        void draw() {
            int vertices = count * QUAD_VERTICES;
            glNamedBufferData(buffers[0], (long) vertices * PARTICLE_STRIDE, GL_STREAM_DRAW);
            glNamedBufferData(buffers[1], sharingRecords.flip(), GL_STREAM_DRAW);
            glNamedBufferData(buffers[4], meshRecords.flip(), GL_STREAM_DRAW);

            storage.save();
            try {
                for (int i = 0; i < buffers.length; i++) storage.bind(i, buffers[i]);
                program.useProgram();
                meshCount.uploadUnsignedInt(count);
                meshSize.uploadUnsignedInt(QUAD_VERTICES);
                meshOffset.uploadUnsignedInt(0);
                program.dispatch((vertices + COMPUTE_LOCAL_SIZE - 1) / COMPUTE_LOCAL_SIZE, 1, 1);
                glMemoryBarrier(GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
            } finally {
                program.resetProgram();
                storage.restore();
            }
            drawExpanded();
        }

        @Override
        public void close() {
            super.close();
            glDeleteBuffers(buffers);
            MemoryUtil.memFree(meshRecords);
            MemoryUtil.memFree(sharingRecords);
        }
    }
}
