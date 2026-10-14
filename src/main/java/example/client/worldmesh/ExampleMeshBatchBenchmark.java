package example.client.worldmesh;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.GeometryCollector;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.StaticMeshRenderer;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.MeshDrawResult;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.MeshSubmitResult;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.StaticMeshBufferSource;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache.StaticMeshCache;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.TreeModelInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.BedrockModelResources;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import org.lwjgl.opengl.ARBTimerQuery;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** 仅供开发环境使用；所有提交路径采用相同的虚拟掉落物姿势。 */
@EventBusSubscriber(modid = SimpleBedrockModel.MOD_ID, value = Dist.CLIENT)
public class ExampleMeshBatchBenchmark {
    public enum Mode { IMMEDIATE, ORDERED, BATCHED }
    public enum Layout { SAME, MODELS, ALL }

    private static final int WARMUP_FRAMES = 45;
    private static final long PREPARE_TIMEOUT_NANOS = 60_000_000_000L;
    private static final int MAX_RECOVERIES = 3;
    private static final ResourceLocation CACHE_OWNER = ResourceLocation.fromNamespaceAndPath("example", "examplemeshbatchbenchmark");
    public record Key(ResourceLocation model, ResourceLocation texture) {}
    public record Instance(Geometry geometry, Vec3 origin, int index, int light, int overlay) {}
    public record Block(int repeat, Mode mode) {}
    public record PendingQuery(int id, Sample sample) {}
    public static class Geometry {
        final Key key;
        final TreeModelInstance model;
        final RenderType ordinary, emissive;
        int parts, uniformParts, fixedParts, vertices;

        public Geometry(Key key) {
            this.key = key;
            model = BedrockModelResources.getInstance().getTreeModel(key.model).createInstance();
            model.resetPose();
            for (var bone : model.baseModel().bones()) {
                model.getBone(bone.index()).illuminated = bone.name() != null && bone.name().endsWith("_illuminated");
            }
            ordinary = RenderType.entityCutout(key.texture);
            emissive = ExampleGunEmissiveRenderTypes.emissive(key.texture);
        }

        public boolean capture(GeometryCollector collector) {
            PoseStack pose = new PoseStack();
            renderModel(pose, collector.buffer(ordinary, emissive, VertexFormat.Mode.QUADS), OverlayTexture.NO_OVERLAY);
            parts = uniformParts = fixedParts = vertices = 0;
            for (var mesh : collector.snapshot().values()) {
                boolean fixed = mesh.lightRunValue(0) != 0;
                for (int run = 0; run < mesh.lightRunCount(); run++) {
                    if ((mesh.lightRunValue(run) != 0) != fixed) throw new IllegalStateException("Mixed part lighting");
                    if (fixed && mesh.lightRunValue(run) != LightTexture.FULL_BRIGHT) {
                        throw new IllegalStateException("Emissive part is not full bright");
                    }
                }
                parts++;
                vertices += mesh.vertexCount();
                if (fixed) fixedParts++; else uniformParts++;
            }
            return parts > 0;
        }

        public void renderModel(PoseStack pose, VertexConsumer consumer, int overlay) {
            pose.pushPose();
            try {
                // 与现有枪械属性对照场景使用相同的模型空间几何。
                pose.translate(0.5, 0, 0.5);
                model.baseModel().renderBoneTree(model, pose, consumer, 0, overlay, 1, 1, 1, 1, true, true);
            } finally {
                pose.popPose();
            }
        }
    }

    public static class Sample {
        Mode mode;
        int block, repeat, frame, items, parts, draws, setups, binds, pendingItems, discardedItems;
        Key firstPending;
        long cpu, interval, gpu = -1;
    }

    private static final List<Instance> INSTANCES = new ArrayList<>();
    private static final List<Block> BLOCKS = new ArrayList<>();
    private static final List<Sample> SAMPLES = new ArrayList<>();
    private static final List<PendingQuery> QUERIES = new ArrayList<>();
    private static final List<Integer> FREE_QUERIES = new ArrayList<>();
    private static final Map<Key, Geometry> GEOMETRY = new LinkedHashMap<>();
    private static ClientLevel level;
    private static Layout layout;
    private static Mode mode;
    private static boolean active, preview, preparing, finishing, gpuTimerSupported, coreTimer;
    public static int sampleFrames, blockIndex, frameInBlock, prepareFrames, expectedPartCount, recoveries;
    private static long lastFrame, preparationStarted, pausedAt, preparedGeneration;
    private static Path output;
    public static String lastResult = "No mesh batch benchmark has run.";

    public ExampleMeshBatchBenchmark() {}

    public static boolean start(Layout selectedLayout, int count, int frames, int repeats) {
        RenderSystem.assertOnRenderThread();
        Minecraft minecraft = Minecraft.getInstance();
        if (FMLEnvironment.production || minecraft.player == null || minecraft.level == null) return false;
        stop();
        if (!StaticMeshRenderer.supportsInstanceAttributes(LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY)) {
            message("The production VBO integer-attribute backend is unsupported.");
            return false;
        }
        for (int i = 0; i < ExampleStressResources.COUNT; i++) {
            if (BedrockModelResources.getInstance().getTreeModel(ExampleStressResources.model(i)) == null) {
                message("Missing stress model: " + ExampleStressResources.model(i));
                return false;
            }
        }
        ExampleImmediateAttributeBenchmark.stop();
        level = minecraft.level;
        layout = selectedLayout;
        sampleFrames = frames;
        blockIndex = frameInBlock = prepareFrames = 0;
        expectedPartCount = -1;
        recoveries = 0;
        preparedGeneration = -1;
        pausedAt = 0;
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
            int model = layout == Layout.SAME ? 0 : i % ExampleStressResources.COUNT;
            int texture = layout == Layout.ALL ? model : 0;
            Key key = new Key(ExampleStressResources.model(model), ExampleStressResources.texture(texture));
            Geometry geometry = GEOMETRY.computeIfAbsent(key, Geometry::new);
            Vec3 origin = center.add(right.scale((i % columns - (columns - 1) / 2.0) * 0.85))
                    .add(0, (i / columns - (rows - 1) / 2.0) * 0.85, 0);
            INSTANCES.add(new Instance(geometry, origin, i, LightTexture.pack((i * 3 + 4) & 15, (i * 7 + 9) & 15),
                    OverlayTexture.pack(i & 15, (i & 16) == 0 ? 3 : 10)));
        }
        Random random = new Random(20261007);
        for (int repeat = 0; repeat < repeats; repeat++) {
            List<Mode> order = new ArrayList<>(Arrays.asList(Mode.values()));
            Collections.shuffle(order, random);
            for (Mode value : order) BLOCKS.add(new Block(repeat, value));
        }
        coreTimer = GL.getCapabilities().OpenGL33;
        gpuTimerSupported = (coreTimer || GL.getCapabilities().GL_ARB_timer_query)
                && GL15.glGetQueryi(GL33.GL_TIME_ELAPSED, GL15.GL_QUERY_COUNTER_BITS) > 0;
        if (gpuTimerSupported) for (int i = 0; i < 8; i++) FREE_QUERIES.add(GL15.glGenQueries());
        output = minecraft.gameDirectory.toPath().resolve("benchmark-results/mesh-batch-"
                + LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + "-" + layout.name().toLowerCase(Locale.ROOT));
        try {
            Files.createDirectories(output);
            Files.writeString(output.resolve("environment.txt"), environment(minecraft, count, frames, repeats));
        } catch (IOException e) {
            SimpleBedrockModel.LOGGER.error("Cannot create mesh batch benchmark output", e);
            stop();
            return false;
        }
        active = preparing = true;
        preparationStarted = System.nanoTime();
        mode = Mode.IMMEDIATE;
        message("Preparing " + count + " virtual dropped items (" + layout + "); " + BLOCKS.size()
                + " randomized blocks, " + frames + " samples + " + WARMUP_FRAMES + " warmup frames each. Keep the camera still.");
        return true;
    }

    public static boolean view(Mode selected, Layout selectedLayout, int count) {
        if (active && preview && layout == selectedLayout && INSTANCES.size() == count
                && level == Minecraft.getInstance().level) {
            mode = selected;
            message("Preview=" + mode + "; poses and geometry retained.");
            return true;
        }
        if (!start(selectedLayout, count, 120, 1)) return false;
        preview = true;
        mode = selected;
        message("Mesh batch preview=" + mode + "; batch stop clears it.");
        return true;
    }

    public static String status() {
        if (!active) return lastResult;
        if (finishing) return "Draining mesh batch GPU queries.";
        if (preparing) return "Preparing mesh batch geometry, frame " + prepareFrames + ".";
        if (preview) return "Mesh batch preview: " + layout + "/" + mode + ", " + INSTANCES.size() + " items.";
        return "Mesh batch: " + layout + "/" + mode + ", block " + (blockIndex + 1) + "/" + BLOCKS.size()
                + ", frame " + frameInBlock + "/" + (WARMUP_FRAMES + sampleFrames) + ".";
    }

    public static void stop() {
        active = preview = preparing = finishing = false;
        level = null;
        INSTANCES.clear();
        GEOMETRY.clear();
        BLOCKS.clear();
        SAMPLES.clear();
        lastFrame = pausedAt = 0;
        for (PendingQuery query : QUERIES) GL15.glDeleteQueries(query.id);
        QUERIES.clear();
        for (int query : FREE_QUERIES) GL15.glDeleteQueries(query);
        FREE_QUERIES.clear();
        StaticMeshRenderer.invalidateOwner(CACHE_OWNER);
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (!active || event.getLevel() != level) return;
        if (RenderSystem.isOnRenderThread()) stop();
        else RenderSystem.recordRenderCall(ExampleMeshBatchBenchmark::stop);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (!active || event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != level || minecraft.player == null
                || GEOMETRY.values().stream().anyMatch(geometry -> geometry.model.baseModel()
                        != BedrockModelResources.getInstance().getTreeModel(geometry.key.model))) {
            abort("Mesh batch benchmark cancelled: world/resources changed.");
            return;
        }
        if (minecraft.isPaused()) {
            if (pausedAt == 0) pausedAt = System.nanoTime();
            lastFrame = 0;
            return;
        }
        long now = System.nanoTime();
        if (pausedAt != 0) {
            if (preparing) preparationStarted += now - pausedAt;
            pausedAt = 0;
        }
        if (!preparing && StaticMeshCache.generation != preparedGeneration) {
            abort("Mesh batch stopped: mesh format/context generation changed; restart with a stable render pipeline.");
            return;
        }
        drainQueries();
        if (finishing) { if (QUERIES.isEmpty()) finish(); return; }
        Sample sample = new Sample();
        sample.interval = lastFrame == 0 ? 0 : now - lastFrame;
        lastFrame = now;
        if (!preparing && !preview) mode = BLOCKS.get(blockIndex).mode;
        sample.mode = preparing ? Mode.IMMEDIATE : mode;
        sample.block = blockIndex;
        sample.repeat = preparing || preview ? -1 : BLOCKS.get(blockIndex).repeat;
        sample.frame = frameInBlock - WARMUP_FRAMES;
        boolean measuring = !preparing && !preview && frameInBlock >= WARMUP_FRAMES;
        if (!preparing && !preview && frameInBlock == 0) {
            message("Block " + (blockIndex + 1) + "/" + BLOCKS.size() + ": " + mode + ", repeat=" + sample.repeat);
        }
        minecraft.renderBuffers().bufferSource().endBatch();
        int query = measuring && !FREE_QUERIES.isEmpty()
                && GL15.glGetQueryi(GL33.GL_TIME_ELAPSED, GL15.GL_CURRENT_QUERY) == 0
                ? FREE_QUERIES.remove(FREE_QUERIES.size() - 1) : 0;
        if (query != 0) GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, query);
        long start = System.nanoTime();
        try {
            drawInstances(event, sample);
        } catch (RuntimeException e) {
            if (query != 0) { GL15.glEndQuery(GL33.GL_TIME_ELAPSED); FREE_QUERIES.add(query); }
            SimpleBedrockModel.LOGGER.error("Mesh batch benchmark failed", e);
            abort("Mesh batch benchmark failed: " + e.getMessage());
            return;
        }
        sample.cpu = System.nanoTime() - start;
        if (query != 0) {
            GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
            QUERIES.add(new PendingQuery(query, sample));
        }
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) {
            abort("Mesh batch stopped: GL error 0x" + Integer.toHexString(error));
            return;
        }
        if (!preparing && StaticMeshCache.generation != preparedGeneration) {
            abort("Mesh batch stopped: mesh format/context generation changed during draw; " + frameDetails(sample));
            return;
        }
        if (preparing) {
            prepareFrames++;
            if (sample.items == INSTANCES.size() && sample.parts == expectedParts()) {
                if (expectedPartCount != -1 && sample.parts != expectedPartCount) {
                    abort("Mesh batch stopped: captured part count changed after recovery; " + frameDetails(sample));
                    return;
                }
                expectedPartCount = sample.parts;
                preparedGeneration = StaticMeshCache.generation;
                preparing = false;
                frameInBlock = 0;
                message("Geometry ready: " + sample.items + " items / " + sample.parts + " parts. "
                        + (preview ? "Preview=" + mode : "Starting three-path VBO comparison."));
            } else if (now - preparationStarted > PREPARE_TIMEOUT_NANOS) {
                abort("Mesh batch geometry preparation timed out after 60 active seconds; " + frameDetails(sample));
            }
            return;
        }
        if (sample.items != INSTANCES.size() || sample.parts != expectedPartCount) {
            recover(sample);
            return;
        }
        if (preview) return;
        if (measuring) SAMPLES.add(sample);
        if (++frameInBlock >= WARMUP_FRAMES + sampleFrames) {
            frameInBlock = 0;
            if (++blockIndex >= BLOCKS.size()) finishing = true;
        }
    }

    public static void recover(Sample sample) {
        if (++recoveries > MAX_RECOVERIES) {
            abort("Mesh batch stopped: meshes repeatedly became unavailable; " + frameDetails(sample));
            return;
        }
        // 空闲缓存到期或上传暂未就绪时，丢弃当前块的部分样本；重建和重新预热均不进入采样。
        SAMPLES.removeIf(recorded -> recorded.block == blockIndex);
        preparing = true;
        preparationStarted = System.nanoTime();
        frameInBlock = prepareFrames = 0;
        lastFrame = 0;
        message("Meshes pending; preparing again and restarting this block's warmup (recovery "
                + recoveries + "/" + MAX_RECOVERIES + "); " + frameDetails(sample));
    }

    public static String frameDetails(Sample sample) {
        return "mode=" + sample.mode + ", block=" + (sample.block + 1) + "/" + BLOCKS.size()
                + ", items=" + sample.items + "/" + INSTANCES.size()
                + ", parts=" + sample.parts + "/" + expectedPartCount
                + ", pending=" + sample.pendingItems + ", discarded=" + sample.discardedItems
                + ", firstPending=" + sample.firstPending + ", generation=" + StaticMeshCache.generation;
    }

    public static void abort(String reason) {
        lastResult = reason;
        message(reason);
        stop();
    }

    public static void drawInstances(RenderLevelStageEvent event, Sample sample) {
        PoseStack pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        Vec3 camera = event.getCamera().getPosition();
        boolean queued = sample.mode == Mode.ORDERED || sample.mode == Mode.BATCHED;
        StaticMeshRenderer.BatchOrder order = sample.mode == Mode.BATCHED
                ? StaticMeshRenderer.BatchOrder.MATERIAL : StaticMeshRenderer.BatchOrder.SUBMISSION;
        try (var batch = queued ? StaticMeshRenderer.openBatch(CACHE_OWNER, order) : null) {
            for (Instance instance : INSTANCES) {
                pose.pushPose();
                try {
                    // 提交辅助方法只接收已经变换的姿势栈，不接收世界坐标。
                    pose.translate(instance.origin.x - camera.x, instance.origin.y - camera.y, instance.origin.z - camera.z);
                    pose.mulPose(Axis.YP.rotationDegrees(17)); // 任意父层变换。
                    pose.mulPose(Axis.XP.rotationDegrees(-8));
                    pose.scale(0.85F, 0.85F, 0.85F);
                    pose.pushPose();
                    try {
                        float phase = instance.index * 0.618F;
                        pose.translate(0, 0.25 + Math.sin(phase) * 0.1, 0);
                        pose.mulPose(Axis.YP.rotation(phase));
                        pose.translate(((instance.index % 3) - 1) * 0.075, 0, (instance.index & 1) * 0.05);
                        pose.translate(-0.5, -0.5, -0.5); // ItemRenderer 的居中变换。
                        submit(instance, pose, batch, sample);
                    } finally { pose.popPose(); }
                } finally { pose.popPose(); }
            }
            // 绘制队列网格前，各物品及父层的姿势栈都已弹出。
            if (batch != null) {
                var stats = batch.endBatch();
                if (stats.queuedItems() != sample.items
                        || stats.discardedItems() == 0 && stats.drawnParts() != sample.parts) {
                    throw new IllegalStateException("Mesh batch returned inconsistent draw counts: " + stats);
                }
                sample.discardedItems = stats.discardedItems();
                sample.items -= stats.discardedItems();
                sample.parts = stats.drawnParts();
                sample.draws = stats.drawnParts();
                sample.setups = stats.materialSetups();
                sample.binds = stats.bufferBinds();
            }
        }
    }

    public static void submit(Instance instance, PoseStack pose, StaticMeshBufferSource batch, Sample sample) {
        Geometry geometry = instance.geometry;
        boolean accepted;
        if (batch != null) {
            var result = batch.submit(geometry.key, geometry::capture, pose, instance.light, instance.overlay);
            if (result == MeshSubmitResult.UNSUPPORTED) throw new IllegalStateException("Unsupported mesh " + geometry.key);
            accepted = result.hasMesh();
        } else {
            var result = StaticMeshRenderer.tryDraw(CACHE_OWNER, geometry.key, geometry::capture, pose, instance.light, instance.overlay);
            if (result == MeshDrawResult.UNSUPPORTED) throw new IllegalStateException("Unsupported mesh " + geometry.key);
            accepted = result.drawn();
            if (accepted) {
                sample.draws += geometry.parts;
                sample.setups += geometry.parts;
                sample.binds += geometry.parts;
            }
        }
        if (accepted) {
            sample.items++;
            sample.parts += geometry.parts;
        } else {
            sample.pendingItems++;
            if (sample.firstPending == null) sample.firstPending = geometry.key;
        }
    }

    public static int expectedParts() {
        return INSTANCES.stream().mapToInt(instance -> instance.geometry.parts).sum();
    }

    public static void drainQueries() {
        for (int i = QUERIES.size() - 1; i >= 0; i--) {
            PendingQuery query = QUERIES.get(i);
            if (GL15.glGetQueryObjecti(query.id, GL15.GL_QUERY_RESULT_AVAILABLE) == GL11.GL_FALSE) continue;
            query.sample.gpu = coreTimer ? GL33.glGetQueryObjectui64(query.id, GL15.GL_QUERY_RESULT)
                    : ARBTimerQuery.glGetQueryObjectui64(query.id, GL15.GL_QUERY_RESULT);
            FREE_QUERIES.add(query.id);
            QUERIES.remove(i);
        }
    }

    public static void finish() {
        try {
            StringBuilder raw = new StringBuilder("block,repeat,mode,frame,items,parts,draws,material_setups,vao_binds,cpu_ns,frame_interval_ns,gpu_elapsed_ns\n");
            for (Sample s : SAMPLES) raw.append(String.format(Locale.ROOT, "%d,%d,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
                    s.block, s.repeat, s.mode, s.frame, s.items, s.parts, s.draws, s.setups, s.binds, s.cpu, s.interval, s.gpu));
            Files.writeString(output.resolve("samples.csv"), raw);
            StringBuilder summary = new StringBuilder("mode,samples,items,parts,draws,material_setups,vao_binds,cpu_median_us,cpu_p95_us,frame_median_ms,frame_p95_ms,gpu_median_us\n");
            for (Mode value : Mode.values()) {
                List<Sample> samples = SAMPLES.stream().filter(s -> s.mode == value).toList();
                if (samples.isEmpty()) continue;
                Sample first = samples.get(0);
                double cpu = percentile(samples.stream().mapToLong(s -> s.cpu).toArray(), .5) / 1000;
                double p95 = percentile(samples.stream().mapToLong(s -> s.cpu).toArray(), .95) / 1000;
                long[] intervals = samples.stream().filter(s -> s.interval > 0).mapToLong(s -> s.interval).toArray();
                long[] gpu = samples.stream().filter(s -> s.gpu >= 0).mapToLong(s -> s.gpu).toArray();
                summary.append(String.format(Locale.ROOT, "%s,%d,%d,%d,%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.3f%n",
                        value, samples.size(), first.items, first.parts, first.draws, first.setups, first.binds,
                        cpu, p95, percentile(intervals, .5) / 1e6, percentile(intervals, .95) / 1e6,
                        gpu.length == 0 ? -1 : percentile(gpu, .5) / 1000));
                message(String.format(Locale.ROOT, "%s: cpu %.1f us (p95 %.1f), draws=%d, materials=%d, binds=%d",
                        value, cpu, p95, first.draws, first.setups, first.binds));
            }
            Files.writeString(output.resolve("summary.csv"), summary);
            StringBuilder captures = new StringBuilder("model,texture,parts,uniform_parts,fixed_parts,vertices\n");
            for (Geometry geometry : GEOMETRY.values()) captures.append(String.format(Locale.ROOT, "%s,%s,%d,%d,%d,%d%n",
                    geometry.key.model, geometry.key.texture, geometry.parts, geometry.uniformParts, geometry.fixedParts, geometry.vertices));
            Files.writeString(output.resolve("capture.csv"), captures);
            lastResult = "Completed: " + output.toAbsolutePath();
            message(lastResult);
        } catch (IOException e) {
            SimpleBedrockModel.LOGGER.error("Cannot save mesh batch benchmark", e);
            lastResult = "Failed to save mesh batch benchmark: " + e.getMessage();
            message(lastResult);
        } finally { stop(); }
    }

    public static double percentile(long[] values, double fraction) {
        if (values.length == 0) return 0;
        Arrays.sort(values);
        double index = fraction * (values.length - 1);
        int lower = (int) index, upper = (int) Math.ceil(index);
        return values[lower] + (values[upper] - values[lower]) * (index - lower);
    }

    public static String environment(Minecraft minecraft, int count, int frames, int repeats) {
        return "renderer=" + GL11.glGetString(GL11.GL_RENDERER) + "\nversion=" + GL11.glGetString(GL11.GL_VERSION)
                + "\njava=" + System.getProperty("java.version") + "\nlayout=" + layout + "\nitems=" + count
                + "\ngeometry_keys=" + GEOMETRY.size() + "\nwarmup_frames=" + WARMUP_FRAMES
                + "\nsample_frames_per_block=" + frames + "\nrepeats=" + repeats
                + "\nvsync=" + minecraft.options.enableVsync().get() + "\nmax_fps=" + minecraft.options.framerateLimit().get()
                + "\nrender_distance=" + minecraft.options.renderDistance().get()
                + "\ncamera=" + minecraft.player.getEyePosition() + "\nrotation=" + minecraft.player.getXRot() + "," + minecraft.player.getYRot()
                + "\nmethod=virtual dropped-item poses at AFTER_BLOCK_ENTITIES; not real ItemEntity/BEWLR/shadow integration"
                + "\npose=frozen per-item bob/spin/scatter under a rotated uniform-scale parent; identical across modes"
                + "\ngeometry=QUADS only, ordinary plus full-bright _illuminated descendants; all modes skip normal visibility culling"
                + "\nmodes=IMMEDIATE,ORDERED,BATCHED\nvao_binds=IMMEDIATE inferred from ready part count; queued modes instrument actual bind calls"
                + "\ncpu=full pose/submission/flush cost; captures/uploads excluded by preparation and warmup\nrecovery=incomplete frames and the current block samples are discarded; prepare then repeat warmup; format/context generation changes abort"
                + "\nprepare_timeout_active_seconds=60\nmax_recoveries=" + MAX_RECOVERIES
                + "\ngpu_timer_supported=" + gpuTimerSupported
                + "\ngpu_timer=asynchronous GL_TIME_ELAPSED; includes GPU idle during CPU submission; -1 when unavailable\n";
    }

    public static void message(String text) {
        SimpleBedrockModel.LOGGER.info("[MeshBatchBenchmark] {}", text);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) minecraft.player.displayClientMessage(Component.literal(text), false);
    }
}
