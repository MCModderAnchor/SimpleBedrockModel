package example.client.worldmesh;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.GeometryCollector;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.StaticMeshRenderer;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.debug.MeshRenderDebug;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.MeshDrawResult;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.BedrockModelResources;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.ARBInstancedArrays;
import org.lwjgl.opengl.ARBTimerQuery;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** 客户端虚拟枪械阵列，使用实际 StaticMeshRenderer 和当前游戏着色器。 */
@EventBusSubscriber(modid = SimpleBedrockModel.MOD_ID, value = Dist.CLIENT)
public final class ExampleImmediateAttributeBenchmark {
    private static final ResourceLocation CACHE_OWNER = ResourceLocation.fromNamespaceAndPath("example", "exampleimmediateattributebenchmark");
    private static final int WARMUP_FRAMES = 45;

    public enum Mode { I2I, I4I, POINTER_PART, POINTER_ITEM, POINTER_CACHED, PRODUCTION }
    public enum Scene { PLAIN, TACZ_GLOW }

    private record GeometryKey(ResourceLocation model, ResourceLocation texture, Scene scene) {}
    private record CaptureStats(int uniformParts, int fixedParts, int uniformVertices, int fixedVertices) {}
    private record Instance(GeometryKey key, Vec3 origin, int light, int overlay) {}
    private record Block(int repeat, Mode mode) {}
    private static final class Sample {
        int block, repeat, frame, items, parts, pointers, binds, uniformParts, fixedParts;
        Mode mode;
        long cpu, interval, gpu = -1;
    }
    private record PendingQuery(int id, Sample sample) {}
    private static final class VaoState {
        Mode mode;
        VertexFormat format;
        boolean instanceLight;
        long lightOffset = -1, overlayOffset = -1;
    }

    private static final List<Instance> INSTANCES = new ArrayList<>();
    private static final List<Block> BLOCKS = new ArrayList<>();
    private static final List<Sample> SAMPLES = new ArrayList<>();
    private static final List<PendingQuery> QUERIES = new ArrayList<>();
    private static final List<Integer> FREE_QUERIES = new ArrayList<>();
    private static final IdentityHashMap<VertexBuffer, VaoState> VAOS = new IdentityHashMap<>();
    private static final Map<GeometryKey, CaptureStats> CAPTURES = new HashMap<>();
    private static final Attributes ATTRIBUTES = new Attributes();
    private static ClientLevel level;
    private static boolean active, preparing, finishing, preview, varyingOverlay, gpuTimerSupported, coreInstancing;
    private static int sampleFrames, blockIndex, frameInBlock, prepareFrames, parameterBuffer;
    private static int frameItems, frameParts, framePointers, frameBinds, frameUniformParts, frameFixedParts;
    private static long lastFrame;
    private static Mode mode;
    private static Mode previousAttributeMode;
    private static Scene scene;
    private static Path output;
    private static String lastResult = "No immediate attribute benchmark has run.";

    private ExampleImmediateAttributeBenchmark() {}

    static boolean start(int count, int frames, int repeats, boolean bothAttributes, Scene selectedScene) {
        RenderSystem.assertOnRenderThread();
        Minecraft minecraft = Minecraft.getInstance();
        if (FMLEnvironment.production || minecraft.player == null || minecraft.level == null) return false;
        stop();
        preview = false;
        coreInstancing = GL.getCapabilities().OpenGL33;
        if (!coreInstancing && !GL.getCapabilities().GL_ARB_instanced_arrays) {
            message("OpenGL 3.3 or ARB_instanced_arrays is required for this comparison.");
            return false;
        }
        for (int i = 0; i < ExampleStressResources.COUNT; i++) {
            if (BedrockModelResources.getInstance().getTreeModel(ExampleStressResources.model(i)) == null) {
                message("Missing development stress model: " + ExampleStressResources.model(i));
                return false;
            }
        }
        ExampleMeshBatchBenchmark.stop();
        level = minecraft.level;
        scene = selectedScene;
        varyingOverlay = bothAttributes;
        sampleFrames = frames;
        blockIndex = frameInBlock = prepareFrames = 0;
        lastFrame = 0;
        int columns = (int) Math.ceil(Math.sqrt(count));
        int rows = (count + columns - 1) / columns;
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 look = minecraft.player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0, look.z);
        forward = forward.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 center = eye.add(forward.scale(10));
        for (int i = 0; i < count; i++) {
            int model = i % ExampleStressResources.COUNT;
            double x = (i % columns - (columns - 1) / 2.0) * 0.85;
            double y = (i / columns - (rows - 1) / 2.0) * 0.85;
            GeometryKey key = new GeometryKey(ExampleStressResources.model(model), ExampleStressResources.texture(model), scene);
            int light = LightTexture.pack((i * 3 + 4) & 15, (i * 7 + 9) & 15);
            int overlay = bothAttributes ? OverlayTexture.pack(i & 15, (i & 16) == 0 ? 3 : 10) : OverlayTexture.NO_OVERLAY;
            INSTANCES.add(new Instance(key, center.add(right.scale(x)).add(0, y, 0), light, overlay));
        }
        Random random = new Random(20261006);
        for (int repeat = 0; repeat < repeats; repeat++) {
            List<Mode> order = new ArrayList<>(Arrays.asList(Mode.values()));
            Collections.shuffle(order, random);
            for (Mode value : order) BLOCKS.add(new Block(repeat, value));
        }
        createParameters();
        gpuTimerSupported = (coreInstancing || GL.getCapabilities().GL_ARB_timer_query)
                && GL15.glGetQueryi(GL33.GL_TIME_ELAPSED, GL15.GL_QUERY_COUNTER_BITS) > 0;
        if (gpuTimerSupported) for (int i = 0; i < 8; i++) FREE_QUERIES.add(GL15.glGenQueries());
        output = minecraft.gameDirectory.toPath().resolve("benchmark-results/"
                + (scene == Scene.TACZ_GLOW ? "immediate-attributes-glow-" : "immediate-attributes-")
                + LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        try {
            Files.createDirectories(output);
            Files.writeString(output.resolve("environment.txt"), environment(minecraft, count, frames, repeats));
        } catch (IOException e) {
            SimpleBedrockModel.LOGGER.error("Cannot create benchmark output", e);
            stop();
            return false;
        }
        active = preparing = true;
        finishing = false;
        mode = Mode.I2I;
        message("Preparing " + count + " virtual guns (" + scene + "); " + BLOCKS.size() + " randomized blocks, "
                + frames + " samples + " + WARMUP_FRAMES + " warmup frames each. Keep the camera still.");
        return true;
    }

    static boolean view(Mode selected, int count, boolean bothAttributes, Scene selectedScene) {
        if (active && preview && INSTANCES.size() == count && varyingOverlay == bothAttributes
                && scene == selectedScene && Minecraft.getInstance().level == level) {
            mode = selected;
            message("Preview mode=" + selected + "; geometry and positions retained.");
            return true;
        }
        if (!start(count, 120, 1, bothAttributes, selectedScene)) return false;
        preview = true;
        mode = selected;
        message("Visual preview scene=" + scene + ", mode=" + selected + "; attrib stop clears the scene.");
        return true;
    }

    static String status() {
        if (!active) return lastResult;
        if (finishing) return "Draining GPU queries.";
        if (preparing) return "Preparing geometry, frame " + prepareFrames + ".";
        if (preview) return "Visual preview: " + scene + "/" + mode + ", " + INSTANCES.size() + " items, varyingOverlay=" + varyingOverlay + ".";
        return "Immediate attributes: " + scene + "/" + mode + ", block " + (blockIndex + 1) + "/" + BLOCKS.size()
                + ", frame " + frameInBlock + "/" + (WARMUP_FRAMES + sampleFrames) + ".";
    }

    static void stop() {
        active = preparing = finishing = preview = false;
        level = null;
        INSTANCES.clear();
        BLOCKS.clear();
        SAMPLES.clear();
        VAOS.clear();
        previousAttributeMode = null;
        CAPTURES.clear();
        lastFrame = 0;
        if (parameterBuffer != 0) {
            // 缓存几何的 VAO 可能引用此缓冲；销毁缓冲前须先失效这些几何。
            StaticMeshRenderer.invalidateOwner(CACHE_OWNER);
            RenderSystem.glDeleteBuffers(parameterBuffer);
            parameterBuffer = 0;
        }
        for (PendingQuery pending : QUERIES) GL15.glDeleteQueries(pending.id);
        QUERIES.clear();
        for (int id : FREE_QUERIES) GL15.glDeleteQueries(id);
        FREE_QUERIES.clear();
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (!active || event.getLevel() != level) return;
        Runnable cleanup = () -> {
            message("Benchmark cancelled: world unloaded.");
            stop();
        };
        if (RenderSystem.isOnRenderThread()) cleanup.run();
        else RenderSystem.recordRenderCall(cleanup::run);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (!active || event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != level || minecraft.player == null) {
            message("Benchmark cancelled: world changed.");
            stop();
            return;
        }
        if (minecraft.isPaused()) { lastFrame = 0; return; }
        drainQueries();
        if (finishing) {
            if (QUERIES.isEmpty()) finish();
            return;
        }
        long now = System.nanoTime();
        long interval = lastFrame == 0 ? 0 : now - lastFrame;
        lastFrame = now;
        if (!preparing && !preview) mode = BLOCKS.get(blockIndex).mode;
        boolean measuring = !preparing && !preview && frameInBlock >= WARMUP_FRAMES;
        Sample sample = new Sample();
        sample.mode = mode;
        sample.block = blockIndex;
        sample.repeat = preparing || preview ? -1 : BLOCKS.get(blockIndex).repeat;
        sample.frame = frameInBlock - WARMUP_FRAMES;
        sample.interval = interval;
        // 开始计时的即时绘制阶段前，先提交此前积累的缓冲内容。
        minecraft.renderBuffers().bufferSource().endBatch();
        int query = measuring && !FREE_QUERIES.isEmpty()
                && GL15.glGetQueryi(GL33.GL_TIME_ELAPSED, GL15.GL_CURRENT_QUERY) == 0
                ? FREE_QUERIES.remove(FREE_QUERIES.size() - 1) : 0;
        if (query != 0) GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, query);
        frameItems = frameParts = framePointers = frameBinds = frameUniformParts = frameFixedParts = 0;
        if (mode != previousAttributeMode && (mode == Mode.PRODUCTION || previousAttributeMode == Mode.PRODUCTION)) {
            // 生产路径可能已经替换开发控制器配置的 VAO 属性指针和 divisor。
            VAOS.clear();
        }
        previousAttributeMode = mode;
        if (mode == Mode.PRODUCTION) framePointers = frameBinds = -1;
        long start = System.nanoTime();
        try {
            if (mode == Mode.PRODUCTION) drawInstances(event);
            else MeshRenderDebug.withDrawAttributes(ATTRIBUTES, () -> drawInstances(event));
        } catch (RuntimeException e) {
            if (query != 0) {
                GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
                FREE_QUERIES.add(query);
            }
            SimpleBedrockModel.LOGGER.error("Immediate attribute benchmark failed", e);
            message("Benchmark failed: " + e.getMessage());
            stop();
            return;
        }
        sample.cpu = System.nanoTime() - start;
        if (query != 0) {
            GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
            QUERIES.add(new PendingQuery(query, sample));
        }
        sample.items = frameItems;
        sample.parts = frameParts;
        sample.pointers = framePointers;
        sample.binds = frameBinds;
        sample.uniformParts = frameUniformParts;
        sample.fixedParts = frameFixedParts;
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) {
            message("Benchmark stopped: GL error 0x" + Integer.toHexString(error));
            stop();
            return;
        }
        if (preparing) {
            if (++prepareFrames > 1800) {
                message("Geometry preparation timed out (" + frameItems + "/" + INSTANCES.size() + " ready).");
                stop();
            } else if (frameItems == INSTANCES.size()) {
                if (scene == Scene.TACZ_GLOW && frameFixedParts == 0) {
                    message("Benchmark stopped: no fixed emissive part was captured in the TaCZ glow scene.");
                    stop();
                    return;
                }
                preparing = false;
                frameInBlock = 0;
                message("Geometry ready: " + frameItems + " items / " + frameParts + " parts ("
                        + frameUniformParts + " instance-light, " + frameFixedParts + " fixed emissive) per frame. "
                        + (preview ? "Preview=" + mode : "Starting A/B blocks."));
            }
            return;
        }
        if (frameItems != INSTANCES.size()) {
            message("Benchmark stopped: geometry became unavailable.");
            stop();
            return;
        }
        if (preview) return;
        if (measuring) SAMPLES.add(sample);
        if (++frameInBlock >= WARMUP_FRAMES + sampleFrames) {
            SimpleBedrockModel.LOGGER.info("Immediate attribute benchmark block {}/{}: {} complete",
                    blockIndex + 1, BLOCKS.size(), mode);
            frameInBlock = 0;
            if (++blockIndex >= BLOCKS.size()) finishing = true;
        }
    }

    private static void drawInstances(RenderLevelStageEvent event) {
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        for (Instance instance : INSTANCES) {
            pose.pushPose();
            pose.translate(instance.origin.x - camera.x, instance.origin.y - camera.y, instance.origin.z - camera.z);
            MeshDrawResult result = StaticMeshRenderer.tryDraw(CACHE_OWNER, instance.key,
                    collector -> captureGeometry(instance.key, collector),
                    pose, instance.light, instance.overlay);
            if (result.drawn()) {
                frameItems++;
                CaptureStats captured = CAPTURES.get(instance.key);
                frameUniformParts += captured.uniformParts;
                frameFixedParts += captured.fixedParts;
                frameParts += captured.uniformParts + captured.fixedParts;
            }
            else if (result == MeshDrawResult.UNSUPPORTED) {
                throw new IllegalStateException("Unsupported immediate layout/model: " + instance.key.model);
            }
            pose.popPose();
        }
    }

    private static boolean captureGeometry(GeometryKey key, GeometryCollector collector) {
        if (key.scene == Scene.PLAIN) {
            if (!collector.blockModel(key.model, key.texture, Direction.NORTH)) return false;
        } else {
            var model = BedrockModelResources.getInstance().getTreeModel(key.model);
            if (model == null) return false;
            var instance = model.createInstance();
            instance.resetPose();
            // TaCZ 在模型实例上设置此属性，不把它编码进 Bedrock JSON。
            for (var definition : model.bones()) {
                String name = definition.name();
                var bone = instance.getBone(definition.index());
                if (bone != null) bone.illuminated = name != null && name.endsWith("_illuminated");
            }
            PoseStack pose = new PoseStack();
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-Direction.NORTH.toYRot()));
            model.renderBoneTree(instance, pose, collector.buffer(RenderType.entityCutout(key.texture),
                            ExampleGunEmissiveRenderTypes.emissive(key.texture), VertexFormat.Mode.QUADS),
                    0, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1, true, true);
        }
        int uniformParts = 0, fixedParts = 0, uniformVertices = 0, fixedVertices = 0;
        for (var mesh : collector.snapshot().values()) {
            boolean fixed = mesh.lightRunValue(0) != 0;
            for (int run = 0; run < mesh.lightRunCount(); run++) {
                if ((mesh.lightRunValue(run) != 0) != fixed) throw new IllegalStateException("Mixed lighting within a captured part");
                if (fixed && mesh.lightRunValue(run) != LightTexture.FULL_BRIGHT) {
                    throw new IllegalStateException("Emissive part is not full bright");
                }
            }
            if (fixed) { fixedParts++; fixedVertices += mesh.vertexCount(); }
            else { uniformParts++; uniformVertices += mesh.vertexCount(); }
        }
        CAPTURES.put(key, new CaptureStats(uniformParts, fixedParts, uniformVertices, fixedVertices));
        return true;
    }

    private static void createParameters() {
        int previous = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        parameterBuffer = GlStateManager._glGenBuffers();
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, parameterBuffer);
        IntBuffer pairs = BufferUtils.createIntBuffer(256 * 256 * 2);
        for (int v = 0; v < 256; v++) for (int u = 0; u < 256; u++) pairs.put(u).put(v);
        pairs.flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, pairs, GL15.GL_STATIC_DRAW);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, previous);
    }

    private static long pairOffset(int packed) {
        int u = packed & 0xFFFF, v = packed >>> 16;
        if ((u | v) > 255) throw new IllegalArgumentException("Parameter table coordinates exceed 255");
        return ((long) v * 256 + u) * 8;
    }

    private static int elementOffset(VertexFormat format, VertexFormatElement target) {
        int offset = format.getOffset(target);
        if (offset < 0) throw new IllegalStateException("Missing vertex element " + target);
        return offset;
    }

    private static void divisor(int index, int value) {
        if (coreInstancing) GL33.glVertexAttribDivisor(index, value);
        else ARBInstancedArrays.glVertexAttribDivisorARB(index, value);
    }

    private static final class Attributes implements MeshRenderDebug.DrawAttributes {
        private int previousBuffer;

        @Override public void beginItem() {
            if (mode == Mode.I2I || mode == Mode.I4I) return;
            previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            if (mode != Mode.POINTER_PART) bind(parameterBuffer);
        }

        @Override public void setupPart(VertexBuffer buffer, VertexFormat format, boolean instanceLight,
                                        int lightIndex, int overlayIndex, int light, int overlay) {
            VaoState state = VAOS.computeIfAbsent(buffer, ignored -> new VaoState());
            boolean array = mode != Mode.I2I && mode != Mode.I4I;
            if (state.mode != mode || state.format != format || state.instanceLight != instanceLight) {
                // 仅在初始化或模式切换时设置数组启用状态、divisor 和固定逐顶点 UV2。
                divisor(overlayIndex, array ? 1 : 0);
                divisor(lightIndex, array && instanceLight ? 1 : 0);
                if (array) GlStateManager._enableVertexAttribArray(overlayIndex);
                else GlStateManager._disableVertexAttribArray(overlayIndex);
                if (array && instanceLight) GlStateManager._enableVertexAttribArray(lightIndex);
                else if (instanceLight) GlStateManager._disableVertexAttribArray(lightIndex);
                else {
                    int restore = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
                    bind(buffer.vertexBufferId);
                    GlStateManager._vertexAttribIPointer(lightIndex, 2, GL11.GL_SHORT,
                            format.getVertexSize(), elementOffset(format, VertexFormatElement.UV2));
                    framePointers++;
                    GlStateManager._enableVertexAttribArray(lightIndex);
                    bind(restore);
                }
                state.mode = mode;
                state.format = format;
                state.instanceLight = instanceLight;
                state.lightOffset = state.overlayOffset = -1;
            }
            if (!array) {
                // 与现有渲染器逐 Part 关闭属性数组、设置常量属性的调用保持一致。
                GlStateManager._disableVertexAttribArray(overlayIndex);
                constant(overlayIndex, overlay);
                if (instanceLight) {
                    GlStateManager._disableVertexAttribArray(lightIndex);
                    constant(lightIndex, light);
                } else GlStateManager._enableVertexAttribArray(lightIndex);
                return;
            }
            if (mode == Mode.POINTER_PART) bind(parameterBuffer);
            if ((preparing || preview || frameInBlock < WARMUP_FRAMES)
                    && GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING) != parameterBuffer) {
                throw new IllegalStateException("Material/shader hook changed the prebound ARRAY_BUFFER in " + mode);
            }
            long overlayOffset = pairOffset(overlay);
            if (mode != Mode.POINTER_CACHED || state.overlayOffset != overlayOffset) {
                pointer(overlayIndex, overlayOffset);
                state.overlayOffset = overlayOffset;
            }
            if (instanceLight) {
                long lightOffset = pairOffset(light);
                if (mode != Mode.POINTER_CACHED || state.lightOffset != lightOffset) {
                    pointer(lightIndex, lightOffset);
                    state.lightOffset = lightOffset;
                }
            }
            if (mode == Mode.POINTER_PART) bind(previousBuffer);
        }

        @Override public void endItem() {
            if (mode != Mode.I2I && mode != Mode.I4I && mode != Mode.POINTER_PART) bind(previousBuffer);
        }

        private void constant(int index, int packed) {
            if (mode == Mode.I4I) GL30.glVertexAttribI4i(index, packed & 0xFFFF, packed >>> 16, 0, 1);
            else GL30.glVertexAttribI2i(index, packed & 0xFFFF, packed >>> 16);
        }

        private void pointer(int index, long offset) {
            GlStateManager._vertexAttribIPointer(index, 2, GL11.GL_INT, 8, offset);
            framePointers++;
        }

        private void bind(int buffer) {
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            frameBinds++;
        }
    }

    private static void drainQueries() {
        for (int i = QUERIES.size() - 1; i >= 0; i--) {
            PendingQuery pending = QUERIES.get(i);
            if (GL15.glGetQueryObjecti(pending.id, GL15.GL_QUERY_RESULT_AVAILABLE) == GL11.GL_FALSE) continue;
            pending.sample.gpu = coreInstancing ? GL33.glGetQueryObjectui64(pending.id, GL15.GL_QUERY_RESULT)
                    : ARBTimerQuery.glGetQueryObjectui64(pending.id, GL15.GL_QUERY_RESULT);
            FREE_QUERIES.add(pending.id);
            QUERIES.remove(i);
        }
    }

    private static void finish() {
        Path completedOutput = output;
        try {
            StringBuilder raw = new StringBuilder("block,repeat,mode,frame,items,parts,pointers,binds,cpu_ns,frame_interval_ns,gpu_elapsed_ns,uniform_parts,fixed_parts\n");
            for (Sample s : SAMPLES) raw.append(String.format(Locale.ROOT, "%d,%d,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
                    s.block, s.repeat, s.mode, s.frame, s.items, s.parts, s.pointers, s.binds, s.cpu, s.interval, s.gpu,
                    s.uniformParts, s.fixedParts));
            Files.writeString(output.resolve("samples.csv"), raw);
            StringBuilder summary = new StringBuilder("mode,samples,items,parts,cpu_median_us,cpu_p95_us,frame_median_ms,frame_p95_ms,gpu_median_us,pointers_mean,binds_mean,uniform_parts,fixed_parts\n");
            for (Mode value : Mode.values()) {
                List<Sample> selected = SAMPLES.stream().filter(s -> s.mode == value).toList();
                double cpu = percentile(selected.stream().mapToLong(s -> s.cpu).toArray(), .5) / 1000;
                double p95 = percentile(selected.stream().mapToLong(s -> s.cpu).toArray(), .95) / 1000;
                long[] intervals = selected.stream().filter(s -> s.interval > 0).mapToLong(s -> s.interval).toArray();
                double interval = percentile(intervals, .5) / 1e6;
                double interval95 = percentile(intervals, .95) / 1e6;
                long[] gpuValues = selected.stream().filter(s -> s.gpu >= 0).mapToLong(s -> s.gpu).toArray();
                double gpu = gpuValues.length == 0 ? -1 : percentile(gpuValues, .5) / 1000;
                double pointers = selected.stream().mapToInt(s -> s.pointers).average().orElse(0);
                double binds = selected.stream().mapToInt(s -> s.binds).average().orElse(0);
                summary.append(String.format(Locale.ROOT, "%s,%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%d%n",
                        value, selected.size(), selected.get(0).items, selected.get(0).parts,
                        cpu, p95, interval, interval95, gpu, pointers, binds,
                        selected.get(0).uniformParts, selected.get(0).fixedParts));
                message(String.format(Locale.ROOT, "%s: cpu %.1f us (p95 %.1f), frame %.2f ms, GPU interval %.1f us",
                        value, cpu, p95, interval, gpu));
            }
            Files.writeString(output.resolve("summary.csv"), summary);
            StringBuilder captures = new StringBuilder("model,texture,scene,uniform_parts,fixed_parts,uniform_vertices,fixed_vertices\n");
            CAPTURES.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().model.toString()))
                    .forEach(entry -> {
                        GeometryKey key = entry.getKey();
                        CaptureStats captured = entry.getValue();
                        captures.append(String.format(Locale.ROOT, "%s,%s,%s,%d,%d,%d,%d%n",
                                key.model, key.texture, key.scene, captured.uniformParts, captured.fixedParts,
                                captured.uniformVertices, captured.fixedVertices));
                    });
            Files.writeString(output.resolve("capture.csv"), captures);
            lastResult = "Completed: " + completedOutput.toAbsolutePath();
            message(lastResult);
        } catch (IOException e) {
            SimpleBedrockModel.LOGGER.error("Cannot save immediate benchmark", e);
            lastResult = "Failed to save benchmark: " + e.getMessage();
            message(lastResult);
        } finally {
            stop();
        }
    }

    private static double percentile(long[] values, double fraction) {
        if (values.length == 0) return 0;
        Arrays.sort(values);
        double index = fraction * (values.length - 1);
        int lower = (int) index, upper = (int) Math.ceil(index);
        return values[lower] + (values[upper] - values[lower]) * (index - lower);
    }

    private static String environment(Minecraft minecraft, int count, int frames, int repeats) {
        return "renderer=" + GL11.glGetString(GL11.GL_RENDERER) + "\nversion=" + GL11.glGetString(GL11.GL_VERSION)
                + "\njava=" + System.getProperty("java.version") + "\nscene=" + scene
                + "\nobjects=" + count + "\nmodels=" + Math.min(count, ExampleStressResources.COUNT)
                + "\nemissive_rule=" + (scene == Scene.TACZ_GLOW ? "bone name ends with _illuminated; descendants inherit full bright" : "none (single-part control)")
                + "\nwarmup_frames=" + WARMUP_FRAMES + "\nsample_frames_per_block=" + frames + "\nrepeats=" + repeats
                + "\nvarying_overlay=" + varyingOverlay + "\nvsync=" + minecraft.options.enableVsync().get()
                + "\nmax_fps=" + minecraft.options.framerateLimit().get() + "\nrender_distance=" + minecraft.options.renderDistance().get()
                + "\ncamera=" + minecraft.player.getEyePosition() + "\nrotation=" + minecraft.player.getXRot() + "," + minecraft.player.getYRot()
                + "\nmethod=actual StaticMeshRenderer, virtual models at AFTER_BLOCK_ENTITIES; not a TaCZ BEWLR/entity pass\n"
                + "gpu_timer_supported=" + gpuTimerSupported
                + "\nproduction_mode=unmodified default backend with binding guards; pointer/bind counters are -1\n"
                + "\ngpu_timer=asynchronous GL_TIME_ELAPSED, includes submission-related GPU idle; -1 when unavailable\n";
    }

    private static void message(String message) {
        SimpleBedrockModel.LOGGER.info("[ImmediateAttribBenchmark] {}", message);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) minecraft.player.displayClientMessage(Component.literal(message), false);
    }
}
