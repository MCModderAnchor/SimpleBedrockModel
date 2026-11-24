package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache.MeshUploadQueue;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache.StaticMeshCache;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.MeshSink;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Runs the real deferred uploader in a hidden GL context without starting Minecraft. */
public final class StaticMeshUploadGlCheck {
    public static void main(String[] args) throws ReflectiveOperationException {
        require(GLFW.glfwInit(), "GLFW initialization");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(16, 16, "SBM upload regression", 0, 0);
        require(window != 0, "hidden GL context");
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            RenderSystem.initRenderThread();
            System.out.println("renderer=" + GL11.glGetString(GL11.GL_RENDERER));
            try (TrackedBuffer caller = new TrackedBuffer(FailureMode.NONE)) {
                var bindImmediate = BufferUploader.class.getDeclaredMethod("bindImmediateBuffer", VertexBuffer.class);
                bindImmediate.setAccessible(true);
                bindImmediate.invoke(null, caller);
                int marker = GL15.glGenBuffers();
                int elementMarker = GL15.glGenBuffers();
                try {
                    GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, marker);
                    GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementMarker);
                    GL20.glEnableVertexAttribArray(0);
                    GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0L);
                    verifyUpload(caller, marker, GL11.GL_NO_ERROR, FailureMode.NONE);
                    verifyUpload(caller, marker, GL11.GL_INVALID_ENUM, FailureMode.NONE);
                    verifyUpload(caller, marker, GL11.GL_INVALID_VALUE, FailureMode.NONE);
                    verifyUpload(caller, marker, GL11.GL_INVALID_ENUM, FailureMode.JAVA);
                    verifyUpload(caller, marker, GL11.GL_NO_ERROR, FailureMode.GL);
                    require(GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING) == elementMarker,
                            "caller VAO retains its index-buffer association");
                    require(GL20.glGetVertexAttribi(0, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED) == GL11.GL_TRUE
                                    && GL20.glGetVertexAttribi(0, GL20.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING) == marker
                                    && GL20.glGetVertexAttribi(0, GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE) == 12,
                            "caller VAO retains its enabled vertex attribute and buffer layout");
                    int previousBinds = caller.bindCalls;
                    bindImmediate.invoke(null, caller);
                    require(caller.bindCalls == previousBinds, "BufferUploader can safely reuse its cached binding");
                    verifyBindings(caller, marker);
                    MeshIntegerAttributes attributes = new MeshIntegerAttributes();
                    try {
                        require(attributes.supports(GL.getCapabilities(), 0, 0), "integer attributes supported");
                        injectError(GL11.GL_INVALID_VALUE);
                        require(attributes.ensureReady(), "prior errors must not disable parameter upload");
                        verifyBindings(caller, marker);
                        verifyError(GL11.GL_INVALID_VALUE);
                    } finally {
                        attributes.close();
                    }
                    require(GL11.glGetError() == GL11.GL_NO_ERROR, "clean GL error state");
                } finally {
                    BufferUploader.invalidate();
                    GL15.glDeleteBuffers(elementMarker);
                    GL15.glDeleteBuffers(marker);
                    VertexBuffer.unbind();
                }
            }
            System.out.println("PASS shared GL errors preserved on success/failure and parameter-table upload,");
            System.out.println("     logical/GL bindings restored, Java failures detected, payload storage released.");
            System.out.println("PASS actual BufferUploader cache reuse and caller VAO index/attribute state preservation.");
        } finally {
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    private static void verifyUpload(TrackedBuffer caller, int marker, int priorError, FailureMode mode)
            throws ReflectiveOperationException {
        var immediateCache = BufferUploader.class.getDeclaredField("lastImmediateBuffer");
        immediateCache.setAccessible(true);
        require(immediateCache.get(null) == caller, "caller is cached by the real BufferUploader");
        ResourceLocation owner = new ResourceLocation("gl_check", "upload");
        var entry = StaticMeshCache.acquire(new StaticMeshCache.CacheKey(owner, "triangle"));
        MeshSink mesh = new MeshSink().begin(VertexFormat.Mode.TRIANGLES);
        for (int i = 0; i < 3; i++) mesh.vertex(i, 0, 0).color(-1).uv(0, 0).overlayCoords(0).uv2(0).normal(0, 1, 0).endVertex();
        BufferBuilder.RenderedBuffer payload = mesh.buildBuffer();
        BufferBuilder.RenderedBuffer data = payload;
        var target = new TrackedBuffer(mode);
        var part = new StaticMeshCache.Part(target, data.drawState().format(), null,
                new CompletableFuture<>(), true);
        part.owner = owner;
        entry.parts = List.of(part);
        entry.bytes = (long) payload.vertexBuffer().remaining() + payload.indexBuffer().remaining();
        var type = Class.forName(StaticMeshCache.class.getName() + "$PendingUpload");
        var constructor = type.getDeclaredConstructor(StaticMeshCache.Entry.class,
                StaticMeshCache.Part.class, BufferBuilder.RenderedBuffer.class, long.class);
        constructor.setAccessible(true);
        var upload = (MeshUploadQueue.Upload) constructor.newInstance(entry, part, payload, StaticMeshCache.generation);
        try {
            injectError(priorError);
            try {
                upload.upload();
                require(mode != FailureMode.JAVA, "Java upload failures must still propagate");
                require(part.upload.isDone() && !part.upload.isCompletedExceptionally(), "upload completed");
                GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, target.vertexBufferId);
                require(GL15.glGetBufferParameteri(GL15.GL_ARRAY_BUFFER, GL15.GL_BUFFER_SIZE) == entry.bytes,
                        "geometry reached GPU storage");
                GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, marker);
            } catch (IllegalStateException failure) {
                require(mode == FailureMode.JAVA && failure.getMessage().equals("Intentional upload failure"),
                        "expected Java upload failure");
                require(part.upload.isCompletedExceptionally(), "failed upload future");
            }
            verifyBindings(caller, marker);
            require(immediateCache.get(null) == caller, "upload preserves the real BufferUploader cache");
            verifyError(mode == FailureMode.GL ? GL11.GL_INVALID_ENUM : priorError);
            require(target.bindCalls == 0, "temporary upload must not invoke tracked VertexBuffer.bind");
            try {
                data.release();
                throw new AssertionError("payload storage leaked");
            } catch (IllegalStateException expected) {
                // Both successful and failed uploads must close their native payload.
            }
        } finally {
            StaticMeshCache.discard(entry);
        }
        verifyBindings(caller, marker);
        require(immediateCache.get(null) == caller, "retiring the upload target preserves the BufferUploader cache");
        require(TrackedBuffer.current == caller, "retiring an upload target must not leave a logical binding to it");
    }

    private static void verifyBindings(TrackedBuffer caller, int marker) {
        require(TrackedBuffer.current == caller, "logical caller binding preserved");
        require(GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING) == caller.arrayObjectId, "VAO restored");
        require(GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING) == marker, "ARRAY_BUFFER restored");
    }

    private static void injectError(int error) {
        if (error == GL11.GL_INVALID_ENUM) GL11.glEnable(-1);
        else if (error == GL11.GL_INVALID_VALUE) GL11.glLineWidth(-1);
        else require(error == GL11.GL_NO_ERROR, "supported test error");
    }

    /** Only the test owns this isolated context and is allowed to consume its injected errors. */
    private static void verifyError(int expected) {
        require(GL11.glGetError() == expected, "uploader must leave GL errors for the context owner");
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "no unexpected GL errors");
    }

    private enum FailureMode { NONE, JAVA, GL }

    /** Models Iris' bind injection; raw upload bindings must leave this record untouched. */
    private static final class TrackedBuffer extends VertexBuffer {
        private static TrackedBuffer current;
        private final FailureMode mode;
        private int bindCalls;

        private TrackedBuffer(FailureMode mode) {
            super(Usage.STATIC);
            this.mode = mode;
        }

        @Override public void bind() {
            current = this;
            bindCalls++;
            super.bind();
        }

        @Override public void upload(BufferBuilder.RenderedBuffer data) {
            super.upload(data);
            if (mode == FailureMode.JAVA) throw new IllegalStateException("Intentional upload failure");
            if (mode == FailureMode.GL) GL11.glEnable(-1);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
