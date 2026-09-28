package com.github.mcmodderanchor.simplebedrockmodel.v2.client.world;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.nio.FloatBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * Static meshes drawn at the caller's current item/entity render position. Geometry is shared by key;
 * the caller supplies the current pose, light and overlay. All entry points run on the render thread.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = SimpleBedrockModel.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ImmediateStaticMeshRenderer {
    public enum DrawResult { DRAWN, PENDING, UNSUPPORTED }

    @FunctionalInterface
    public interface GeometryProvider {
        boolean capture(GeometryCollector collector);
    }

    private static final int MAX_ENTRIES = 128;
    private static final long MAX_BYTES = 256L * 1024L * 1024L;
    private static final int FORMAT_CHECK_DRAWS = 60;
    private static final VertexBufferPool POOL = new VertexBufferPool();
    private static final Map<Object, Entry> CACHE = new LinkedHashMap<>(16, 0.75F, true);

    private static ClientLevel level;
    private static VertexFormat probedFormat;
    private static int formatCheckCountdown = FORMAT_CHECK_DRAWS;
    private static long generation;
    private static long cachedBytes;
    private static long captureWindow = Long.MIN_VALUE;
    private static int capturesInWindow;
    private static long captureNanosInWindow;

    private static final class Part {
        final VertexBuffer buffer;
        final VertexFormat requestedFormat;
        final RenderType material;
        final CompletableFuture<Void> upload;
        final boolean instanceLight;

        Part(VertexBuffer buffer, VertexFormat requestedFormat, RenderType material,
             CompletableFuture<Void> upload, boolean instanceLight) {
            this.buffer = buffer;
            this.requestedFormat = requestedFormat;
            this.material = material;
            this.upload = upload;
            this.instanceLight = instanceLight;
        }
    }

    private static final class Entry {
        List<Part> parts;
        long bytes;
        long retryAtNanos;
        int failures;
        boolean unsupported;

        boolean ready() {
            return parts != null && !parts.isEmpty()
                    && parts.stream().allMatch(part -> part.upload.isDone() && !part.upload.isCompletedExceptionally());
        }

        boolean failed() {
            return parts != null && parts.stream().anyMatch(part -> part.upload.isCompletedExceptionally());
        }
    }

    private ImmediateStaticMeshRenderer() {}

    /**
     * PENDING owns this render call: no old renderer should run while capture or upload is pending.
     * beforeDraw is invoked only for a fully uploaded mesh, before changing RenderType state.
     */
    public static DrawResult tryDraw(Object key, GeometryProvider provider, PoseStack pose,
                                     int packedLight, int packedOverlay, Runnable beforeDraw) {
        RenderSystem.assertOnRenderThread();
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(beforeDraw, "beforeDraw");
        ClientLevel current = Minecraft.getInstance().level;
        if (current == null) return DrawResult.UNSUPPORTED;
        if (current != level) {
            clear();
            level = current;
        }
        if (--formatCheckCountdown <= 0) {
            formatCheckCountdown = FORMAT_CHECK_DRAWS;
            VertexFormat format = WorldMeshRenderer.probeFormat();
            if (format != null && probedFormat != null && probedFormat != format) {
                clear();
            }
            if (format != null) probedFormat = format;
        }

        Entry entry = CACHE.computeIfAbsent(key, ignored -> new Entry());
        trim(key);
        if (entry.unsupported) return DrawResult.UNSUPPORTED;
        if (entry.failed()) {
            retire(entry, true);
            entry.parts = null;
            entry.retryAtNanos = System.nanoTime() + retryDelay(entry.failures++);
        }
        if (entry.parts == null) {
            long now = System.nanoTime();
            if (now < entry.retryAtNanos || !captureBudgetAvailable(now)) return DrawResult.PENDING;
            long start = System.nanoTime();
            boolean captured = capture(entry, key, provider);
            capturesInWindow++;
            captureNanosInWindow += System.nanoTime() - start;
            if (!captured) {
                if (entry.unsupported) return DrawResult.UNSUPPORTED;
                entry.retryAtNanos = System.nanoTime() + retryDelay(entry.failures++);
                return DrawResult.PENDING;
            }
            trim(key);
        }
        if (!entry.ready()) return DrawResult.PENDING;
        if (!layoutSupported(entry)) return DrawResult.UNSUPPORTED;

        beforeDraw.run();
        return draw(entry, pose, packedLight, packedOverlay) ? DrawResult.DRAWN : DrawResult.PENDING;
    }

    private static boolean captureBudgetAvailable(long now) {
        long window = now / 16_000_000L;
        if (captureWindow != window) {
            captureWindow = window;
            capturesInWindow = 0;
            captureNanosInWindow = 0;
        }
        return capturesInWindow == 0 || capturesInWindow < 4 && captureNanosInWindow < 2_000_000L;
    }

    private static long retryDelay(int failures) {
        return Math.min(5_000_000_000L, 100_000_000L << Math.min(failures, 5));
    }

    private static boolean capture(Entry entry, Object key, GeometryProvider provider) {
        ChunkRenderDispatcher dispatcher = Minecraft.getInstance().levelRenderer.getChunkRenderDispatcher();
        if (dispatcher == null) return false;
        GeometryCollector collector = new GeometryCollector();
        List<Part> parts = new ArrayList<>();
        try {
            if (!provider.capture(collector)) return false;
            Map<GeometryCollector.Pass, MeshSink> meshes = collector.snapshot();
            if (meshes.isEmpty()) {
                entry.unsupported = true;
                return false;
            }
            long bytes = 0;
            for (Map.Entry<GeometryCollector.Pass, MeshSink> pass : meshes.entrySet()) {
                MeshSink mesh = pass.getValue();
                boolean dynamic = false;
                boolean fixed = false;
                for (int i = 0; i < mesh.lightRunCount(); i++) {
                    if (mesh.lightRunValue(i) == 0) dynamic = true;
                    else fixed = true;
                }
                if (dynamic && fixed) {
                    parts.forEach(part -> release(part, generation, true));
                    SimpleBedrockModel.LOGGER.warn("Immediate mesh has mixed per-vertex lighting: {}", key);
                    entry.unsupported = true;
                    return false;
                }
                VertexBuffer buffer = POOL.acquire(mesh.format());
                BufferBuilder builder = new BufferBuilder(Math.max(1536, mesh.estimatedBytes() / 6 + 64));
                builder.begin(mesh.mode(), mesh.format());
                mesh.emitTo(builder);
                BufferBuilder.RenderedBuffer rendered = builder.endOrDiscardIfEmpty();
                if (rendered == null) {
                    POOL.recycle(mesh.format(), buffer);
                    continue;
                }
                parts.add(new Part(buffer, mesh.format(), pass.getKey().material(),
                        dispatcher.uploadChunkLayer(rendered, buffer), dynamic));
                bytes += mesh.estimatedBytes();
            }
            if (parts.isEmpty()) return false;
            entry.parts = parts;
            entry.bytes = bytes;
            entry.failures = 0;
            cachedBytes += bytes;
            return true;
        } catch (RuntimeException exception) {
            parts.forEach(part -> release(part, generation, true));
            SimpleBedrockModel.LOGGER.warn("Failed to capture immediate static mesh {}", key, exception);
            return false;
        }
    }

    private static boolean layoutSupported(Entry entry) {
        for (Part part : entry.parts) {
            VertexFormat format = part.buffer.getFormat();
            if (format == null || attributeIndex(format, DefaultVertexFormat.ELEMENT_UV1) < 0
                    || attributeIndex(format, DefaultVertexFormat.ELEMENT_UV2) < 0) return false;
        }
        return true;
    }

    private static int attributeIndex(VertexFormat format, com.mojang.blaze3d.vertex.VertexFormatElement element) {
        List<com.mojang.blaze3d.vertex.VertexFormatElement> elements = format.getElements();
        for (int i = 0; i < elements.size(); i++) if (elements.get(i) == element) return i;
        return -1;
    }

    private static boolean draw(Entry entry, PoseStack pose, int packedLight, int packedOverlay) {
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
        for (Part part : entry.parts) {
            part.material.setupRenderState();
            try {
                ShaderInstance shader = RenderSystem.getShader();
                if (shader == null) return false;
                WorldMeshRenderer.uploadSharedUniforms(shader, modelView, RenderSystem.getProjectionMatrix());
                uploadItemLighting(shader, pose);
                part.buffer.bind();
                VertexFormat format = part.buffer.getFormat();
                int overlayIndex = attributeIndex(format, DefaultVertexFormat.ELEMENT_UV1);
                int lightIndex = attributeIndex(format, DefaultVertexFormat.ELEMENT_UV2);
                GlStateManager._disableVertexAttribArray(overlayIndex);
                GL30.glVertexAttribI2i(overlayIndex, packedOverlay & 0xFFFF, packedOverlay >>> 16);
                if (part.instanceLight) {
                    GlStateManager._disableVertexAttribArray(lightIndex);
                    GL30.glVertexAttribI2i(lightIndex, packedLight & 0xFFFF, packedLight >>> 16);
                } else {
                    GlStateManager._enableVertexAttribArray(lightIndex);
                }
                part.buffer.draw();
            } finally {
                ShaderInstance shader = RenderSystem.getShader();
                if (shader != null) shader.clear();
                VertexBuffer.unbind();
                part.material.clearRenderState();
            }
        }
        return true;
    }

    /** Match the current entity pass's light directions to normals kept in model space. */
    private static void uploadItemLighting(ShaderInstance shader, PoseStack pose) {
        if (shader.LIGHT0_DIRECTION == null && shader.LIGHT1_DIRECTION == null) return;
        RenderSystem.setupShaderLights(shader);
        Matrix3f inverseLinear = new Matrix3f(pose.last().pose());
        float determinant = inverseLinear.determinant();
        if (Float.isFinite(determinant) && Math.abs(determinant) > 1.0E-10F) inverseLinear.invert();
        else inverseLinear.identity();
        uploadLocalLight(shader.LIGHT0_DIRECTION, inverseLinear);
        uploadLocalLight(shader.LIGHT1_DIRECTION, inverseLinear);
    }

    private static void uploadLocalLight(Uniform uniform, Matrix3f inverseLinear) {
        if (uniform == null) return;
        FloatBuffer values = uniform.getFloatBuffer();
        Vector3f light = new Vector3f(values.get(0), values.get(1), values.get(2))
                .mul(inverseLinear);
        if (light.lengthSquared() > 1.0E-12F) light.normalize();
        uniform.set(light);
        uniform.upload();
    }

    private static void trim(Object current) {
        Iterator<Map.Entry<Object, Entry>> iterator = CACHE.entrySet().iterator();
        while ((CACHE.size() > MAX_ENTRIES || cachedBytes > MAX_BYTES) && iterator.hasNext()) {
            Map.Entry<Object, Entry> eldest = iterator.next();
            if (eldest.getKey().equals(current)) continue;
            iterator.remove();
            // Evicted GPU storage is closed after upload, so the byte cap includes idle memory.
            retire(eldest.getValue(), true);
        }
    }

    private static void retire(Entry entry, boolean invalidated) {
        if (entry.parts == null) return;
        cachedBytes -= entry.bytes;
        long retiringGeneration = generation;
        for (Part part : entry.parts) release(part, retiringGeneration, invalidated);
        entry.parts = null;
        entry.bytes = 0;
    }

    private static void release(Part part, long retiringGeneration, boolean invalidated) {
        part.upload.whenComplete((ignored, failure) -> {
            Runnable action = () -> {
                if (invalidated || failure != null || retiringGeneration != generation) part.buffer.close();
                else POOL.recycle(part.requestedFormat, part.buffer);
            };
            if (RenderSystem.isOnRenderThread()) {
                action.run();
            } else {
                RenderSystem.recordRenderCall(action::run);
            }
        });
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        Runnable action = () -> {
            if (event.getLevel() == level) {
                clear();
                level = null;
            }
        };
        if (RenderSystem.isOnRenderThread()) {
            action.run();
        } else {
            RenderSystem.recordRenderCall(action::run);
        }
    }

    /** Invalidate all item meshes on resource reload or render context loss. */
    public static void clear() {
        RenderSystem.assertOnRenderThread();
        generation++;
        for (Entry entry : CACHE.values()) {
            retire(entry, true);
        }
        CACHE.clear();
        cachedBytes = 0;
        POOL.clear();
        probedFormat = null;
        formatCheckCountdown = FORMAT_CHECK_DRAWS;
    }
}
