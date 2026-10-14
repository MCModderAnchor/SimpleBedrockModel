package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBInstancedArrays;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLCapabilities;

import java.nio.IntBuffer;

/** 网格后端持有的整数参数表；临时网格读取 light／overlay，世界网格仅读取实例 light。 */
@OnlyIn(Dist.CLIENT)
public class MeshIntegerAttributes {
    static final int COORDINATES = 256;
    static final int STRIDE = 2 * Integer.BYTES;
    static final int TABLE_BYTES = COORDINATES * COORDINATES * STRIDE;

    private GLCapabilities context;
    private boolean supported;
    private boolean coreDivisor;
    private int maxAttributes;
    private int buffer = -1;
    private long generation;
    private boolean failed;

    public enum Backend { UNSUPPORTED, CORE, ARB }

    /** 世界网格只替换 UV2；UV1 继续读取几何，状态随句柄和 VAO 的生命周期保存。 */
    public static class LightState {
        private VertexFormat format;
        private int geometryBuffer = -1;
        private int lightIndex = -1;
        private int parameterBuffer = -1;
        private long parameterGeneration = -1;
        private long lightOffset = -1;
        private boolean attached;

        public void invalidate() { attached = false; }

        public boolean prepareLayout(VertexBuffer geometry, int maxAttributes) {
            VertexFormat uploaded = geometry.getFormat();
            if (uploaded == null || geometry.isInvalid() || geometry.vertexBufferId < 0) return false;
            if (format != uploaded || geometryBuffer != geometry.vertexBufferId) {
                invalidate();
                format = uploaded;
                geometryBuffer = geometry.vertexBufferId;
                lightIndex = uploaded.getElements().indexOf(VertexFormatElement.UV2);
            }
            return lightIndex >= 0 && lightIndex < maxAttributes;
        }
    }

    public static Backend selectBackend(boolean gl30, long integerPointer, boolean gl33, long corePointer,
                                 boolean arbInstancing, long arbPointer) {
        if (!gl30 || integerPointer == 0) return Backend.UNSUPPORTED;
        if (gl33 && corePointer != 0) return Backend.CORE;
        return arbInstancing && arbPointer != 0 ? Backend.ARB : Backend.UNSUPPORTED;
    }

    /** 状态由各 Part 持有，不放入全局光照／偏移缓存；新 Part 的属性来源初始为未挂载。 */
    public static class PartState {
        private VertexFormat format;
        private int geometryBuffer = -1;
        private int overlayIndex = -1;
        private int lightIndex = -1;
        private int stride;
        private int fixedLightType;
        private int fixedLightCount;
        private long fixedLightOffset;
        private int parameterBuffer = -1;
        private long parameterGeneration = -1;
        private long overlayOffset = -1;
        private long lightOffset = -1;
        private boolean attached;

        public void invalidate() { attached = false; }

        public boolean prepareLayout(VertexBuffer geometry, int maxAttributes) {
            VertexFormat uploaded = geometry.getFormat();
            if (uploaded == null || geometry.isInvalid() || geometry.vertexBufferId < 0) return false;
            if (format != uploaded || geometryBuffer != geometry.vertexBufferId) {
                invalidate();
                format = uploaded;
                geometryBuffer = geometry.vertexBufferId;
                overlayIndex = uploaded.getElements().indexOf(VertexFormatElement.UV1);
                lightIndex = uploaded.getElements().indexOf(VertexFormatElement.UV2);
                if (overlayIndex >= 0 && lightIndex >= 0) {
                    VertexFormatElement light = uploaded.getElements().get(lightIndex);
                    stride = uploaded.getVertexSize();
                    fixedLightType = light.type().glType();
                    fixedLightCount = light.count();
                    fixedLightOffset = uploaded.getOffset(light);
                }
            }
            return overlayIndex >= 0 && overlayIndex < maxAttributes
                    && lightIndex >= 0 && lightIndex < maxAttributes;
        }
    }

    public boolean supports(GLCapabilities current, int packedLight, int packedOverlay) {
        if (context != current) {
            // GL 对象标识属于创建它的上下文；不能在新上下文中删除旧标识。
            close();
            context = current;
            Backend backend = selectBackend(current.OpenGL30, current.glVertexAttribIPointer,
                    current.OpenGL33, current.glVertexAttribDivisor,
                    current.GL_ARB_instanced_arrays, current.glVertexAttribDivisorARB);
            coreDivisor = backend == Backend.CORE;
            supported = backend != Backend.UNSUPPORTED;
            maxAttributes = supported ? GL11.glGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS) : 0;
            if (!supported) {
                SimpleBedrockModel.LOGGER.warn("Buffered mesh integer attributes unavailable: "
                                + "OpenGL30={}, IPointer={}, core divisor={}, ARB extension={}, ARB divisor={}",
                        current.OpenGL30, current.glVertexAttribIPointer != 0,
                        coreDivisor, current.GL_ARB_instanced_arrays, current.glVertexAttribDivisorARB != 0);
            }
        }
        return supported && !failed && parameterOffset(packedLight) >= 0 && parameterOffset(packedOverlay) >= 0;
    }

    /** 检查两个完整的无符号 16 位分量，不截断，也不钳制数值。 */
    public static long parameterOffset(int packed) {
        int u = packed & 0xFFFF;
        int v = packed >>> 16;
        return (u | v) < COORDINATES ? ((long) v * COORDINATES + u) * STRIDE : -1;
    }

    public static void fillParameters(IntBuffer destination) {
        for (int v = 0; v < COORDINATES; v++) {
            for (int u = 0; u < COORDINATES; u++) destination.put(u).put(v);
        }
    }

    public boolean layoutSupported(PartState state, VertexBuffer geometry) {
        return state.prepareLayout(geometry, maxAttributes);
    }

    public boolean layoutSupported(LightState state, VertexBuffer geometry) {
        return state.prepareLayout(geometry, maxAttributes);
    }

    /** 分配／上传失败记录并停用后端，直到明确的生命周期重置。 */
    public boolean ensureReady() {
        if (buffer >= 0) return true;
        if (!supported || failed) return false;
        int previous = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int created = -1;
        try {
            created = GlStateManager._glGenBuffers();
            if (created <= 0) throw new IllegalStateException("Cannot allocate integer attribute buffer");
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, created);
            IntBuffer pairs = BufferUtils.createIntBuffer(TABLE_BYTES / Integer.BYTES);
            fillParameters(pairs);
            pairs.flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, pairs, GL15.GL_STATIC_DRAW);
            if (GL15.glGetBufferParameteri(GL15.GL_ARRAY_BUFFER, GL15.GL_BUFFER_SIZE) != TABLE_BYTES) {
                throw new IllegalStateException("Integer attribute buffer upload did not allocate the complete table");
            }
            buffer = created;
            created = -1;
            generation++;
            return true;
        } catch (RuntimeException exception) {
            failed = true;
            SimpleBedrockModel.LOGGER.error("Mesh integer attributes disabled after parameter buffer failure", exception);
            return false;
        } finally {
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, previous);
            if (created > 0) RenderSystem.glDeleteBuffers(created);
        }
    }

    public int beginItem() {
        int previous = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        return previous;
    }

    public void endItem(int previous) {
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, previous);
    }

    /** 在已绑定的世界网格 VAO 上设置实例 UV2，保持几何 overlay 和调用方 ARRAY_BUFFER 绑定。 */
    public void setupLight(LightState state, long lightOffset) {
        if (!state.attached || state.parameterBuffer != buffer || state.parameterGeneration != generation) {
            state.invalidate();
            state.lightOffset = -1;
            GlStateManager._enableVertexAttribArray(state.lightIndex);
            divisor(state.lightIndex, 1);
        }
        if (state.lightOffset != lightOffset) {
            int previous = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            if (previous != buffer) GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            GlStateManager._vertexAttribIPointer(state.lightIndex, 2, GL11.GL_INT, STRIDE, lightOffset);
            state.lightOffset = lightOffset;
            if (previous != buffer) GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, previous);
        }
        state.parameterBuffer = buffer;
        state.parameterGeneration = generation;
        state.attached = true;
    }

    public void setupPart(PartState state, boolean instanceLight, long lightOffset, long overlayOffset) {
        boolean reattach = !state.attached || state.parameterBuffer != buffer
                || state.parameterGeneration != generation;
        if (reattach) {
            state.invalidate();
            state.overlayOffset = state.lightOffset = -1;
            GlStateManager._enableVertexAttribArray(state.overlayIndex);
            divisor(state.overlayIndex, 1);
            GlStateManager._enableVertexAttribArray(state.lightIndex);
            divisor(state.lightIndex, instanceLight ? 1 : 0);
            if (!instanceLight) {
                GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, state.geometryBuffer);
                GlStateManager._vertexAttribIPointer(state.lightIndex, state.fixedLightCount, state.fixedLightType,
                        state.stride, state.fixedLightOffset);
            }
        }
        boolean updateOverlay = state.overlayOffset != overlayOffset;
        boolean updateLight = instanceLight && state.lightOffset != lightOffset;
        if (updateOverlay || updateLight) {
            // 材质／着色器钩子可能在 beginItem 后改变 ARRAY_BUFFER；仅在写入属性指针时查询绑定。
            if (GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING) != buffer) {
                GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            }
            if (updateOverlay) {
                GlStateManager._vertexAttribIPointer(state.overlayIndex, 2, GL11.GL_INT, STRIDE, overlayOffset);
                state.overlayOffset = overlayOffset;
            }
            if (updateLight) {
                GlStateManager._vertexAttribIPointer(state.lightIndex, 2, GL11.GL_INT, STRIDE, lightOffset);
                state.lightOffset = lightOffset;
            }
        }
        state.parameterBuffer = buffer;
        state.parameterGeneration = generation;
        state.attached = true;
    }

    public void divisor(int index, int value) {
        if (coreDivisor) GL33.glVertexAttribDivisor(index, value);
        else ARBInstancedArrays.glVertexAttribDivisorARB(index, value);
    }

    public void close() {
        if (buffer >= 0 && context == GL.getCapabilities()) RenderSystem.glDeleteBuffers(buffer);
        buffer = -1;
        generation++;
        failed = false;
    }
}
