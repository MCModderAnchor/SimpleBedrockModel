package example.client.particle;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.sodium.SodiumCompat;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleDescription;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleEffectDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.IComponent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.ParticleAppearanceBillboard;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.tinting.ParticleTintingGradient;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.resource.ParticleDefinitionLoader;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleMolangEnvironment;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world.MolangWorldParticleRenderType;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world.SnowStormParticle;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world.WorldParticleConfig;
import com.google.common.collect.EvictingQueue;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.AbstractQueue;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.SplittableRandom;

/** Measures the existing SnowStormParticle paths in an isolated development world. */
@Mod.EventBusSubscriber(modid = SimpleBedrockModel.MOD_ID, value = Dist.CLIENT)
public final class ExampleWorldParticleBenchmark {
    private static final boolean ENABLED = Boolean.getBoolean("sbm.worldParticleBenchmark");
    private static final boolean SMOKE_TICK = Boolean.getBoolean("sbm.worldParticleBenchmark.smokeTick");
    private static final boolean CPU_PROFILE = Boolean.getBoolean("sbm.worldParticleBenchmark.cpuProfile");
    private static final boolean CPU_ISOLATION = Boolean.getBoolean("sbm.worldParticleBenchmark.cpuIsolation");
    private static final String SHADER_PACK = System.getProperty("sbm.worldParticleBenchmark.shaderPack");
    private static final boolean PRODUCTION_COMPUTE = Boolean.getBoolean("sbm.worldParticleBenchmark.productionCompute");
    private static final boolean COMPUTE_COMPARISON = SHADER_PACK != null || PRODUCTION_COMPUTE;
    private static final boolean COMPUTE_SMOKE = COMPUTE_COMPARISON && SMOKE_TICK;
    private static final int[] FLIPBOOK_CAPTURE_TICKS = {4, 14};
    private static final boolean LARGE_BATCHES = CPU_PROFILE || CPU_ISOLATION
            || Boolean.getBoolean("sbm.worldParticleBenchmark.largeBatches");
    private static final boolean GPU_PROTOTYPES = LARGE_BATCHES || Boolean.getBoolean("sbm.worldParticleBenchmark.gpuPrototypes");
    private static final int WARMUP = 120;
    private static final int FRAMES = 240;
    private static final int REPEATS = 3;
    private static final int UNLIMITED_FRAME_LIMIT = 260;
    private static final long UNCAPPED_WARMUP_NS = 1_000_000_000L;
    private static final long UNCAPPED_SAMPLE_NS = 2_000_000_000L;
    private static final long PROFILE_SAMPLE_NS = 5_000_000_000L;
    private static final int MAX_PENDING_QUERIES = 16;
    private static final int MAX_TEST_PARTICLES = 12000;
    private static final int MAX_LARGE_BATCH_PARTICLES = 96000;
    private static final int GPU_CAPACITY = LARGE_BATCHES ? MAX_LARGE_BATCH_PARTICLES : MAX_TEST_PARTICLES;
    private static final ResourceLocation TEXTURE = new ResourceLocation("example", "textures/particle/bomb_smoke.png");
    private static final ParticleDescription.Material MATERIAL = ParticleDescription.Material.PARTICLES_ALPHA;
    private static final ResourceLocation ID = new ResourceLocation("example", "world_particle_benchmark");
    private static final ResourceLocation SMOKE_ID = new ResourceLocation("example", "test_basic_smoke");
    private static boolean loadingWorld;
    private static int worldTicks;
    private static ExampleWorldParticleBenchmark run;

    private final Minecraft minecraft;
    private final ClientLevel level;
    private final Path output;
    private final boolean uncapped;
    private final Map<ParticleRenderType, Queue<Particle>> particleQueues;
    private final List<Scenario> scenarios = new ArrayList<>();
    private final ParticleGpuPrototype.Instanced instanced;
    private final ParticleGpuPrototype.ArCompute arCompute;
    private final ParticleGpuPrototype.CustomCompute customCompute;
    private final ParticleRenderType renderType = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder builder, TextureManager textures) {
            batch.begin(builder, textures);
            renderLeaderOnly = batch.scenario.cpuVariant == CpuVariant.DIRECT_LOOP;
        }

        @Override
        public void end(Tesselator tesselator) {
            try {
                batch.end(tesselator);
            } finally {
                renderLeaderOnly = false;
            }
        }
    };
    private int scenarioIndex;
    private Batch batch;
    private boolean renderLeaderOnly;
    private long lastFrameEnd = System.nanoTime();
    private long clientTickStart;

    private ExampleWorldParticleBenchmark(Minecraft minecraft) throws IOException {
        this.minecraft = minecraft;
        this.level = minecraft.level;
        this.output = Files.createTempDirectory(minecraft.gameDirectory.toPath(), "particle-results-");
        this.uncapped = minecraft.getWindow().getFramerateLimit() >= UNLIMITED_FRAME_LIMIT;
        Files.writeString(output.resolve("environment.txt"), "gpu=" + GL11.glGetString(GL11.GL_RENDERER)
                + "\ngl=" + GL11.glGetString(GL11.GL_VERSION)
                + "\njava=" + System.getProperty("java.runtime.version")
                + "\nprocessors=" + Runtime.getRuntime().availableProcessors()
                + "\nframebuffer=" + minecraft.getWindow().getWidth() + "x" + minecraft.getWindow().getHeight()
                + "\nfov=" + minecraft.options.fov().get()
                + "\nframe_limit=" + (uncapped ? "unlimited" : minecraft.getWindow().getFramerateLimit())
                + "\nvsync=" + minecraft.options.enableVsync().get()
                + "\nsmoke_tick=" + SMOKE_TICK
                + "\ngpu_prototypes=" + (GPU_PROTOTYPES || (SHADER_PACK != null && !PRODUCTION_COMPUTE))
                + "\nproduction_compute=" + PRODUCTION_COMPUTE
                + "\nlarge_batches=" + LARGE_BATCHES
                + "\ncpu_profile=" + CPU_PROFILE
                + "\ncpu_isolation=" + CPU_ISOLATION
                + "\nbenchmark_queue_capacity=" + (LARGE_BATCHES ? GPU_CAPACITY : 16384)
                + "\nmin_warmup_frames=" + WARMUP + "\nmin_sample_frames=" + FRAMES
                + "\nmin_warmup_ns=" + (uncapped ? UNCAPPED_WARMUP_NS : 0)
                + "\nmin_sample_ns=" + sampleDuration(uncapped) + "\n");
        if (CPU_PROFILE) {
            Files.writeString(output.resolve("profile-windows.csv"), "case,start_utc,end_utc\n");
        }
        if (!SodiumCompat.isSodiumInstalled() || !GL.getCapabilities().OpenGL33
                || GL15.glGetQueryi(GL33.GL_TIME_ELAPSED, GL15.GL_QUERY_COUNTER_BITS) == 0) {
            throw new IllegalStateException("Embeddium and OpenGL timer queries are required for this benchmark");
        }
        if (COMPUTE_COMPARISON) {
            if (!GL.getCapabilities().OpenGL45) {
                throw new IllegalStateException("The GPU benchmark uses OpenGL 4.5 vertex and buffer setup");
            }
            if (SHADER_PACK != null) {
                var oculus = ModList.get().getModContainerById("oculus")
                        .orElseThrow(() -> new IllegalStateException("The shader benchmark requires Oculus"));
                Files.writeString(output.resolve("environment.txt"),
                        "oculus=" + oculus.getModInfo().getVersion() + "\nshader_pack=" + SHADER_PACK + "\n",
                        StandardOpenOption.APPEND);
                Files.writeString(output.resolve("shader-selection.csv"),
                        "case,draw,shader,shader_class,program,getter_shader,getter_program\n");
            }
            if (COMPUTE_SMOKE) {
                Files.writeString(output.resolve("flipbook-snapshots.csv"), "image,tick,u0,v0,u1,v1\n");
            }
            captureSnapshot(output.resolve("verify-empty.png"));
            this.instanced = null;
            this.arCompute = null;
            this.customCompute = PRODUCTION_COMPUTE ? null : new ParticleGpuPrototype.CustomCompute(GPU_CAPACITY,
                    ParticleGpuPrototype.CustomCompute.loadProgram(minecraft.getResourceManager()));
            addComputeScenarios();
        } else if (CPU_ISOLATION) {
            if (!GL.getCapabilities().OpenGL45) {
                throw new IllegalStateException("The GPU benchmark uses OpenGL 4.5 vertex and buffer setup");
            }
            var shader = new ShaderInstance(minecraft.getResourceManager(),
                    new ResourceLocation("example", "world_particle_instanced"), DefaultVertexFormat.PARTICLE);
            this.instanced = new ParticleGpuPrototype.Instanced(GPU_CAPACITY, shader);
            this.arCompute = null;
            this.customCompute = null;
            addCpuIsolationScenarios();
        } else if (GPU_PROTOTYPES) {
            if (!GL.getCapabilities().OpenGL46) {
                throw new IllegalStateException("The stock AR shader requires OpenGL 4.6");
            }
            var arProgram = ParticleGpuPrototype.ArCompute.loadedProgram();
            var shader = new ShaderInstance(minecraft.getResourceManager(),
                    new ResourceLocation("example", "world_particle_instanced"), DefaultVertexFormat.PARTICLE);
            int customProgram = ParticleGpuPrototype.CustomCompute.loadProgram(minecraft.getResourceManager());
            this.instanced = new ParticleGpuPrototype.Instanced(GPU_CAPACITY, shader);
            this.arCompute = new ParticleGpuPrototype.ArCompute(GPU_CAPACITY, arProgram);
            this.customCompute = new ParticleGpuPrototype.CustomCompute(GPU_CAPACITY, customProgram);
            addGpuScenarios();
            Files.writeString(output.resolve("prototype-layouts.txt"), "AR program=" + arProgram.getProgramHandle()
                    + "\nAR shader=CORE_POS_COLOR_TEX_LIGHT_MESH_UPLOADING_KEY"
                    + "\nCustom compute program=" + customProgram
                    + "\nCPU upload per particle: Embeddium=112 B, instanced=48 B, AR compute=140 B, custom compute=48 B\n");
        } else {
            this.instanced = null;
            this.arCompute = null;
            this.customCompute = null;
        }
        if (SMOKE_TICK && !COMPUTE_SMOKE) {
            addSmokeScenarios();
        } else if (!GPU_PROTOTYPES && !COMPUTE_COMPARISON) {
            for (int repeat = 1; repeat <= REPEATS; repeat++) {
                for (int count : new int[]{1024, 4096, 12000}) {
                    addPair(repeat, count, 0.045f);
                }
                addPair(repeat, 12000, 0.45f);
            }
        }
        particleQueues = LARGE_BATCHES ? installLargeBatchQueue() : null;
        SimpleBedrockModel.LOGGER.info("World particle benchmark output: {}", output.toAbsolutePath());
        nextScenario();
    }

    private Map<ParticleRenderType, Queue<Particle>> installLargeBatchQueue() {
        // ParticleEngine normally creates an EvictingQueue capped at 16,384 per render type.
        // Preinstall a larger queue only for this benchmark's private render type.
        Map<ParticleRenderType, Queue<Particle>> queues = Objects.requireNonNull(
                ObfuscationReflectionHelper.getPrivateValue(ParticleEngine.class, minecraft.particleEngine, "f_107289_"));
        Queue<Particle> queue = CPU_ISOLATION ? new RenderDispatchQueue() : EvictingQueue.create(GPU_CAPACITY);
        if (queues.putIfAbsent(renderType, queue) != null) {
            throw new IllegalStateException("The benchmark particle queue was already registered");
        }
        return queues;
    }

    private final class RenderDispatchQueue extends AbstractQueue<Particle> {
        private final Queue<Particle> delegate = EvictingQueue.create(GPU_CAPACITY);

        @Override
        public Iterator<Particle> iterator() {
            // The normal engine tick and all other modes still iterate the whole FIFO.
            if (!renderLeaderOnly) return delegate.iterator();
            Particle leader = delegate.peek();
            return leader == null ? Collections.emptyIterator() : Collections.singleton(leader).iterator();
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean offer(Particle particle) {
            return delegate.offer(particle);
        }

        @Override
        public Particle poll() {
            return delegate.poll();
        }

        @Override
        public Particle peek() {
            return delegate.peek();
        }
    }

    private void addPair(int repeat, int count, float halfSize) {
        boolean sodiumFirst = repeat % 2 == 0;
        Renderer first = sodiumFirst ? Renderer.EMBEDDIUM : Renderer.VERTEX_CONSUMER;
        Renderer second = sodiumFirst ? Renderer.VERTEX_CONSUMER : Renderer.EMBEDDIUM;
        scenarios.add(new Scenario(repeat, count, halfSize, first, Workload.STATIC));
        scenarios.add(new Scenario(repeat, count, halfSize, second, Workload.STATIC));
    }

    private void addSmokeScenarios() {
        Workload[] workloads = {Workload.BASELINE, Workload.SMOKE_MOLANG, Workload.SMOKE_JAVA};
        for (int repeat = 1; repeat <= REPEATS; repeat++) {
            for (int count : new int[]{1024, 4096, 12000}) {
                for (int i = 0; i < workloads.length; i++) {
                    scenarios.add(new Scenario(repeat, count, 0.4f, Renderer.EMBEDDIUM,
                            workloads[(i + repeat - 1) % workloads.length]));
                }
            }
        }
    }

    private void addGpuScenarios() {
        Renderer[] renderers = {Renderer.EMBEDDIUM, Renderer.INSTANCED, Renderer.AR_COMPUTE, Renderer.CUSTOM_COMPUTE};
        int[] counts;
        if (CPU_PROFILE) {
            counts = new int[]{24000, GPU_CAPACITY};
        } else {
            counts = LARGE_BATCHES ? new int[]{24000, 48000, GPU_CAPACITY, GPU_CAPACITY}
                    : new int[]{1024, 4096, GPU_CAPACITY, GPU_CAPACITY};
        }
        for (int repeat = 1; repeat <= REPEATS; repeat++) {
            for (int i = 0; i < counts.length; i++) {
                float halfSize = !CPU_PROFILE && i == counts.length - 1 ? 0.45f : 0.045f;
                for (int j = 0; j < renderers.length; j++) {
                    scenarios.add(new Scenario(repeat, counts[i], halfSize, renderers[(j + repeat - 1) % renderers.length],
                            Workload.STATIC));
                }
            }
        }
    }

    private void addComputeScenarios() {
        for (int repeat = 1; repeat <= REPEATS; repeat++) {
            Renderer first = repeat % 2 == 1 ? Renderer.EMBEDDIUM : Renderer.CUSTOM_COMPUTE;
            Renderer second = first == Renderer.EMBEDDIUM ? Renderer.CUSTOM_COMPUTE : Renderer.EMBEDDIUM;
            int[] counts = COMPUTE_SMOKE ? new int[]{4096, MAX_TEST_PARTICLES} : new int[]{4096, MAX_TEST_PARTICLES, MAX_TEST_PARTICLES};
            Workload workload = COMPUTE_SMOKE ? Workload.SMOKE_MOLANG : Workload.STATIC;
            for (int i = 0; i < counts.length; i++) {
                float halfSize = COMPUTE_SMOKE ? 0.085f : (i == counts.length - 1 ? 0.45f : 0.045f);
                for (Renderer renderer : new Renderer[]{first, second}) {
                    scenarios.add(new Scenario(repeat, counts[i], halfSize, renderer, workload));
                }
            }
        }
        if (PRODUCTION_COMPUTE && COMPUTE_SMOKE) {
            scenarios.add(new Scenario(REPEATS + 1, 4096, 0.085f, Renderer.EMBEDDIUM, Workload.MIXED_SMOKE));
            scenarios.add(new Scenario(REPEATS + 1, 4096, 0.085f, Renderer.CUSTOM_COMPUTE, Workload.MIXED_SMOKE));
        }
    }

    private void addCpuIsolationScenarios() {
        CpuVariant[] variants = CpuVariant.values();
        for (int repeat = 1; repeat <= REPEATS; repeat++) {
            for (int count : new int[]{24000, GPU_CAPACITY}) {
                for (int i = 0; i < variants.length; i++) {
                    CpuVariant variant = variants[(i + 2 * (repeat - 1)) % variants.length];
                    scenarios.add(new Scenario(repeat, count, 0.045f, Renderer.INSTANCED, Workload.STATIC, variant));
                }
            }
        }
    }

    private static long sampleDuration(boolean uncapped) {
        if (CPU_PROFILE) return PROFILE_SAMPLE_NS;
        return uncapped ? UNCAPPED_SAMPLE_NS : 0;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClientTickStart(TickEvent.ClientTickEvent event) {
        if (run != null && event.phase == TickEvent.Phase.START) run.clientTickStart = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) return;
        if (run != null) {
            run.batch.finishClientTick(System.nanoTime() - run.clientTickStart);
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        try {
            if (!loadingWorld && minecraft.screen instanceof TitleScreen) {
                loadingWorld = true;
                Path gameDir = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
                if (!Files.isRegularFile(gameDir.resolve(".sbm-particle-benchmark"))) {
                    throw new IllegalStateException("Run the benchmark in its isolated game directory");
                }
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                LevelSettings settings = new LevelSettings("SBM particle benchmark", GameType.SPECTATOR,
                        false, Difficulty.PEACEFUL, false, rules, WorldDataConfiguration.DEFAULT);
                minecraft.createWorldOpenFlows().createFreshLevel("world-" + System.currentTimeMillis(), settings,
                        new WorldOptions(0L, false, false), registry -> registry.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
            if (!loadingWorld || minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
            if (++worldTicks == 1) {
                var server = minecraft.getSingleplayerServer();
                server.execute(() -> server.getPlayerList().getPlayers().forEach(player ->
                        player.connection.teleport(0, 128, 0, 0, 0)));
            }
            if (worldTicks == 80) run = new ExampleWorldParticleBenchmark(minecraft);
        } catch (IOException | RuntimeException e) {
            SimpleBedrockModel.LOGGER.error("World particle benchmark failed to start", e);
            minecraft.stop();
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (run == null || event.phase != TickEvent.Phase.END) return;
        try {
            run.finishFrame();
        } catch (IOException | RuntimeException e) {
            SimpleBedrockModel.LOGGER.error("World particle benchmark failed", e);
            run.batch.close();
            run.closePrototypes();
            run.minecraft.stop();
            run = null;
        }
    }

    private void finishFrame() throws IOException {
        if (minecraft.level != level) throw new IllegalStateException("Benchmark world changed");
        long now = System.nanoTime();
        batch.finishFrame(now - lastFrameEnd, output);
        lastFrameEnd = now;
        batch.poll();
        if (!batch.samplingComplete() || !batch.pending.isEmpty()) return;
        batch.write(output, scenarioIndex);
        batch.close();
        if (scenarioIndex == scenarios.size()) {
            SimpleBedrockModel.LOGGER.info("World particle benchmark complete: {}", output.toAbsolutePath());
            closePrototypes();
            minecraft.stop();
            run = null;
            return;
        }
        nextScenario();
    }

    private void closePrototypes() {
        if (particleQueues != null) particleQueues.remove(renderType);
        if (instanced != null) instanced.close();
        if (arCompute != null) arCompute.close();
        if (customCompute != null) customCompute.close();
    }

    private void nextScenario() {
        Scenario scenario = scenarios.get(scenarioIndex++);
        if (PRODUCTION_COMPUTE) WorldParticleConfig.COMPUTE.set(scenario.renderer == Renderer.CUSTOM_COMPUTE);
        ParticleEffectDefinition definition = definitionFor(scenario.workload);
        ParticleEffectDefinition mixedDefinition = scenario.workload == Workload.MIXED_SMOKE ? rotateY(definition) : definition;
        ParticleGpuPrototype prototype = switch (scenario.renderer) {
            case INSTANCED -> instanced;
            case AR_COMPUTE -> arCompute;
            case CUSTOM_COMPUTE -> customCompute;
            default -> null;
        };
        if (CPU_ISOLATION) instanced.setRawRecordWrites(scenario.cpuVariant.rawWrites);
        Path snapshot = COMPUTE_COMPARISON && !COMPUTE_SMOKE && scenario.count == GPU_CAPACITY
                ? output.resolve("verify-" + scenario.coverage() + "-" + scenario.resultMode() + "-" + scenario.repeat + ".png")
                : GPU_PROTOTYPES && !CPU_PROFILE && scenario.repeat == 1 && scenario.count == GPU_CAPACITY
                ? output.resolve("verify-" + scenario.coverage() + "-" + scenario.resultMode() + ".png") : null;
        batch = new Batch(scenario, uncapped, definition.getDescription(), prototype, snapshot);
        ParticleEmitterInstance emitter = new ParticleEmitterInstance(definition, new ParticleMolangEnvironment());
        List<ParticleInstance> smokeParticles = new ArrayList<>();
        if (scenario.workload.smoke()) {
            emitter.setExternalParticleManagement(smokeParticles::add);
            for (int i = 0; i < scenario.count; i++) emitter.spawnParticle();
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition();
        var random = new SplittableRandom(20261123);
        var offset = new Vector3f();
        for (int i = 0; i < scenario.count; i++) {
            offset.set((float) random.nextDouble(-5, 5), (float) random.nextDouble(-2, 2),
                    (float) random.nextDouble(12, 20)).rotate(camera.rotation());
            ParticleInstance particle = scenario.workload.smoke() ? smokeParticles.get(i) : new ParticleInstance();
            particle.x = (float) (eye.x + offset.x);
            particle.y = (float) (eye.y + offset.y);
            particle.z = (float) (eye.z + offset.z);
            particle.width = particle.height = scenario.halfSize;
            particle.maxLifetime = SMOKE_TICK ? 10 : 300;
            particle.u1 = 1;
            particle.v1 = 0.125f;
            particle.a = 0.7f;
            particle.rotation = (float) random.nextDouble(-180, 180);
            if (SMOKE_TICK) {
                // Keep a fixed population and distribute particles across flipbook frames and color stops.
                particle.emitter = emitter;
                particle.age = (float) random.nextDouble(0, 6);
                // Track one particle across its first flipbook transition while keeping the rest staggered.
                if (COMPUTE_SMOKE && i == 0) particle.age = 1f;
                particle.random1 = random.nextFloat();
                particle.random2 = random.nextFloat();
                particle.random3 = random.nextFloat();
                particle.random4 = random.nextFloat();
                particle.vx = particle.vy = particle.vz = 0;
                particle.rotationRate = (float) random.nextDouble(-90, 90);
            }
            ParticleEffectDefinition particleDefinition = i == scenario.count / 2 ? mixedDefinition : definition;
            var worldParticle = new BenchParticle(level, particle, particleDefinition, emitter, batch,
                    renderType, i == 0, i == scenario.count - 1);
            batch.particles.add(worldParticle);
            minecraft.particleEngine.add(worldParticle);
        }
        SimpleBedrockModel.LOGGER.info("World particle benchmark {}/{}: {}", scenarioIndex, scenarios.size(), scenario);
    }

    private static ParticleEffectDefinition definitionFor(Workload workload) {
        if (!workload.smoke()) {
            return new ParticleEffectDefinition(ID, new ParticleDescription(ID, MATERIAL, TEXTURE, 0, 0),
                    List.of(), null, null);
        }
        ParticleEffectDefinition smoke = Objects.requireNonNull(ParticleDefinitionLoader.getInstance().getDefinition(SMOKE_ID));
        if (workload != Workload.SMOKE_JAVA) return smoke;

        // Keep the same gradient and context lookups while replacing its one dynamic expression.
        List<IComponent> components = new ArrayList<>(smoke.emitterPreset().components());
        for (var component : smoke.particlePreset().components()) {
            if (component instanceof ParticleTintingGradient gradient) {
                components.add(new ParticleTintingGradient(context -> {
                    var vars = context.getVariableStorage();
                    return vars.get("particle_age").getAsNumber() / vars.get("particle_lifetime").getAsNumber();
                }, gradient.stops(), gradient.colors()));
            } else {
                components.add(component);
            }
        }
        return new ParticleEffectDefinition(smoke.getIdentifier(), smoke.getDescription(), components,
                smoke.getCurves(), smoke.getEvents());
    }

    private static ParticleEffectDefinition rotateY(ParticleEffectDefinition smoke) {
        List<IComponent> components = new ArrayList<>(smoke.emitterPreset().components());
        for (var component : smoke.particlePreset().components()) {
            if (component instanceof ParticleAppearanceBillboard billboard) {
                components.add(new ParticleAppearanceBillboard(billboard.size(),
                        ParticleAppearanceBillboard.FaceCameraMode.ROTATE_Y, billboard.uv(),
                        billboard.flipbook(), billboard.dynamicSize()));
            } else {
                components.add(component);
            }
        }
        return new ParticleEffectDefinition(smoke.getIdentifier(), smoke.getDescription(), components,
                smoke.getCurves(), smoke.getEvents());
    }

    private enum Workload {
        STATIC, BASELINE, SMOKE_MOLANG, SMOKE_JAVA, MIXED_SMOKE;

        private boolean smoke() {
            return this == SMOKE_MOLANG || this == SMOKE_JAVA || this == MIXED_SMOKE;
        }
    }

    private enum Renderer {
        VERTEX_CONSUMER, EMBEDDIUM, INSTANCED, AR_COMPUTE, CUSTOM_COMPUTE;

        String label() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    private enum CpuVariant {
        STOCK(true, false), NO_CULL(false, false), RAW_WRITE(true, true),
        NO_CULL_RAW_WRITE(false, true), DIRECT_LOOP(false, false);

        final boolean perParticleCull;
        final boolean rawWrites;

        CpuVariant(boolean perParticleCull, boolean rawWrites) {
            this.perParticleCull = perParticleCull;
            this.rawWrites = rawWrites;
        }
    }

    private record Scenario(int repeat, int count, float halfSize, Renderer renderer, Workload workload,
                            CpuVariant cpuVariant) {
        Scenario(int repeat, int count, float halfSize, Renderer renderer, Workload workload) {
            this(repeat, count, halfSize, renderer, workload, CpuVariant.STOCK);
        }

        String coverage() {
            return halfSize > 0.1f ? "dense" : "sparse";
        }

        String resultMode() {
            if (SMOKE_TICK && !COMPUTE_SMOKE) return workload.name().toLowerCase(Locale.ROOT).replace('_', '-');
            if (CPU_ISOLATION) return cpuVariant.name().toLowerCase(Locale.ROOT).replace('_', '-');
            return renderer.label();
        }
    }

    private record TickSample(long particleNs, long clientNs) {}

    private static void captureSnapshot(Path path) throws IOException {
        try (NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(path);
        }
    }

    private static final class BenchParticle extends SnowStormParticle {
        private final Batch batch;
        private final ParticleRenderType renderType;
        private final ParticleInstance data;
        private final boolean first;
        private final boolean last;

        private BenchParticle(ClientLevel level, ParticleInstance data, ParticleEffectDefinition definition,
                              ParticleEmitterInstance emitter, Batch batch, ParticleRenderType renderType,
                              boolean first, boolean last) {
            super(level, data, definition, emitter);
            this.batch = batch;
            this.renderType = renderType;
            this.data = data;
            this.first = first;
            this.last = last;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return renderType;
        }

        @Override
        public void tick() {
            if (first) batch.tickStart = System.nanoTime();
            super.tick();
            if (last) {
                if (batch.measuring()) batch.particleTickNs = System.nanoTime() - batch.tickStart;
                if (COMPUTE_SMOKE) batch.particleTicks++;
            }
        }

        @Override
        public void render(VertexConsumer consumer, Camera camera, float partialTicks) {
            if (batch.scenario.cpuVariant == CpuVariant.DIRECT_LOOP && isAlive()) {
                for (int i = 0, count = batch.particles.size(); i < count; i++) {
                    batch.particles.get(i).renderOne(consumer, camera, partialTicks);
                }
            } else {
                renderOne(consumer, camera, partialTicks);
            }
        }

        @Override
        public boolean shouldCull() {
            return batch.scenario.cpuVariant.perParticleCull && super.shouldCull();
        }

        private void renderOne(VertexConsumer consumer, Camera camera, float partialTicks) {
            // The engine drains removed particles on its next tick, after the benchmark may have changed scenarios.
            if (!isAlive()) return;
            batch.rendered++;
            // Lock both renderers to the same tick endpoint for the screenshot frames.
            if (COMPUTE_SMOKE && batch.flipbookCaptureReady) partialTicks = 1f;
            if (batch.prototype == null) {
                super.render(consumer, camera, partialTicks);
            } else {
                batch.prototype.push(camera, Mth.lerp(partialTicks, xo, x), Mth.lerp(partialTicks, yo, y),
                        Mth.lerp(partialTicks, zo, z), Mth.lerp(partialTicks, oRoll, roll), data,
                        rCol, gCol, bCol, alpha, getLightColor(partialTicks));
            }
        }
    }

    private static final class Batch {
        private final Scenario scenario;
        private final long minWarmupNs;
        private final long minSampleNs;
        private final MolangWorldParticleRenderType delegate;
        private final ParticleGpuPrototype prototype;
        private final Path snapshot;
        private final List<BenchParticle> particles = new ArrayList<>();
        private final List<Sample> frames = new ArrayList<>();
        private final List<TickSample> ticks = new ArrayList<>();
        private final ArrayDeque<GpuQuery> pending = new ArrayDeque<>();
        private final ArrayDeque<Integer> free = new ArrayDeque<>();
        private final List<String> shaderSelections = new ArrayList<>();
        private int warmup;
        private int rendered;
        private int drawsThisFrame;
        private int particleTicks;
        private int flipbookSnapshots;
        private final long createdAt = System.nanoTime();
        private long renderStart;
        private long tickStart;
        private long particleTickNs;
        private long warmupNs;
        private long sampledNs;
        private boolean warmedThisFrame;
        private boolean snapshotReady;
        private boolean snapshotCaptured;
        private boolean flipbookCaptureReady;
        private Sample frameSample;
        private Instant profileStart;
        private Instant profileEnd;
        private boolean originalSodium;

        private Batch(Scenario scenario, boolean uncapped, ParticleDescription description,
                      ParticleGpuPrototype prototype, Path snapshot) {
            this.scenario = scenario;
            this.delegate = MolangWorldParticleRenderType.get(description.getMaterial(), description.getTexture());
            this.prototype = prototype;
            this.snapshot = snapshot;
            this.minWarmupNs = uncapped ? UNCAPPED_WARMUP_NS : 0;
            this.minSampleNs = sampleDuration(uncapped);
            for (int i = 0; i < MAX_PENDING_QUERIES; i++) free.add(GL15.glGenQueries());
        }

        private boolean samplingComplete() {
            return frames.size() >= FRAMES && sampledNs >= minSampleNs;
        }

        private boolean measuring() {
            return warmup >= WARMUP && warmupNs >= minWarmupNs && !samplingComplete()
                    && (!COMPUTE_SMOKE || flipbookSnapshots == FLIPBOOK_CAPTURE_TICKS.length);
        }

        private void finishFrame(long frameNs, Path output) throws IOException {
            if (SHADER_PACK != null && drawsThisFrame > 0 && drawsThisFrame != shaderSelections.size()) {
                throw new IllegalStateException("The number of benchmark particle draws per frame changed");
            }
            if (COMPUTE_SMOKE && drawsThisFrame > 0 && flipbookCaptureReady) {
                checkDrawError();
                captureFlipbook(output);
                flipbookCaptureReady = false;
            }
            drawsThisFrame = 0;
            if (snapshotReady) {
                checkDrawError();
                captureSnapshot(snapshot);
                snapshotReady = false;
            }
            if (warmedThisFrame) {
                warmup++;
                warmupNs += frameNs;
                warmedThisFrame = false;
            }
            if (frameSample != null) {
                frameSample.frameNs = frameNs;
                frames.add(frameSample);
                sampledNs += frameNs;
                frameSample = null;
                if (CPU_PROFILE && samplingComplete()) profileEnd = Instant.now();
            }
        }

        private void finishClientTick(long clientNs) {
            if (particleTickNs == 0) return;
            ticks.add(new TickSample(particleTickNs, clientNs));
            particleTickNs = 0;
        }

        private void begin(BufferBuilder builder, TextureManager textures) {
            if (CPU_PROFILE && profileStart == null && measuring()) profileStart = Instant.now();
            delegate.begin(builder, textures);
            if (particles.isEmpty()) return;
            if (scenario.renderer == Renderer.EMBEDDIUM && !(builder instanceof VertexBufferWriter)) {
                throw new IllegalStateException("Embeddium fast vertex writer is unavailable");
            }
            originalSodium = SodiumCompat.IS_SODIUM_INSTALLED;
            // This flag is scoped to the test's batch on the render thread.
            SodiumCompat.IS_SODIUM_INSTALLED = scenario.renderer == Renderer.EMBEDDIUM || PRODUCTION_COMPUTE;
            if (prototype != null) prototype.begin();
            if (COMPUTE_SMOKE && flipbookSnapshots < FLIPBOOK_CAPTURE_TICKS.length) {
                int targetTick = FLIPBOOK_CAPTURE_TICKS[flipbookSnapshots];
                if (particleTicks > targetTick) {
                    throw new IllegalStateException("Missed flipbook screenshot at particle tick " + targetTick);
                }
                flipbookCaptureReady = particleTicks == targetTick;
            }
            rendered = 0;
            renderStart = System.nanoTime();
        }

        private void end(Tesselator tesselator) {
            if (particles.isEmpty()) {
                delegate.end(tesselator);
                return;
            }
            long renderNs = System.nanoTime() - renderStart;
            SodiumCompat.IS_SODIUM_INSTALLED = originalSodium;
            if (warmup == 0 && rendered == 0 && System.nanoTime() - createdAt < 5_000_000_000L) {
                // ParticleEngine may render before its next tick drains particlesToAdd.
                delegate.end(tesselator);
                return;
            }
            if (rendered != scenario.count) {
                delegate.end(tesselator);
                throw new IllegalStateException("Bad particle sample: rendered=" + rendered + ", expected=" + scenario.count);
            }
            if (COMPUTE_COMPARISON) drawsThisFrame++;
            if (SHADER_PACK != null && warmup == 0) shaderSelections.add(selectedShaders());
            if (!measuring()) {
                draw(tesselator);
                // Give the particles time to initialize and then settle both interpolation endpoints.
                if (snapshot != null && !snapshotCaptured && warmupNs >= 250_000_000L) {
                    checkDrawError();
                    if (COMPUTE_COMPARISON) {
                        snapshotReady = true;
                    } else {
                        try {
                            captureSnapshot(snapshot);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                    snapshotCaptured = true;
                }
                warmedThisFrame = true;
                return;
            }
            if (free.isEmpty() || GL15.glGetQueryi(GL33.GL_TIME_ELAPSED, GL15.GL_CURRENT_QUERY) != 0) {
                delegate.end(tesselator);
                throw new IllegalStateException("Particle GPU timer unavailable; free queries=" + free.size());
            }
            int query = free.remove();
            GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, query);
            long submitStart = System.nanoTime();
            long submitNs;
            try {
                draw(tesselator);
            } finally {
                submitNs = System.nanoTime() - submitStart;
                GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
            }
            if (frameSample == null) frameSample = new Sample();
            frameSample.renderNs += renderNs;
            frameSample.submitNs += submitNs;
            pending.add(new GpuQuery(frameSample, query));
        }

        private void draw(Tesselator tesselator) {
            delegate.end(tesselator);
            if (prototype != null) prototype.draw();
        }

        private String selectedShaders() {
            ShaderInstance selected = Objects.requireNonNull(RenderSystem.getShader());
            String shaderClass = selected.getClass().getName();
            if (!shaderClass.startsWith("net.irisshaders.iris.pipeline.programs.")) {
                throw new IllegalStateException("Oculus did not select a particle shader: " + shaderClass);
            }
            ShaderInstance getter = Objects.requireNonNull(GameRenderer.getParticleShader());
            return selected.getName() + ',' + shaderClass + ',' + selected.getId() + ','
                    + getter.getName() + ',' + getter.getId();
        }

        private void checkDrawError() {
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                throw new IllegalStateException("Particle draw for " + scenario.renderer + " produced OpenGL error " + error);
            }
        }

        private void captureFlipbook(Path output) throws IOException {
            String image = String.format(Locale.ROOT, "verify-flipbook-%d-%s-%d-tick-%02d.png",
                    scenario.count, scenario.renderer.label(), scenario.repeat, particleTicks);
            captureSnapshot(output.resolve(image));
            ParticleInstance first = particles.get(0).data;
            Files.writeString(output.resolve("flipbook-snapshots.csv"),
                    image + ',' + particleTicks + ',' + first.u0 + ',' + first.v0 + ',' + first.u1 + ',' + first.v1 + '\n',
                    StandardOpenOption.APPEND);
            flipbookSnapshots++;
        }

        private void poll() {
            while (!pending.isEmpty()) {
                GpuQuery query = pending.peek();
                if (GL15.glGetQueryObjecti(query.id, GL15.GL_QUERY_RESULT_AVAILABLE) == 0) return;
                pending.remove();
                query.frame.gpuNs += GL33.glGetQueryObjectui64(query.id, GL15.GL_QUERY_RESULT);
                free.add(query.id);
            }
        }

        private void write(Path output, int index) throws IOException {
            String prefix = String.format(Locale.ROOT, "%02d-%d-%s-%d-%s", index, scenario.repeat,
                    scenario.coverage(), scenario.count,
                    scenario.resultMode());
            StringBuilder renderCsv = new StringBuilder("render_ns,submit_ns,gpu_ns,frame_ns\n");
            for (Sample sample : frames) {
                renderCsv.append(sample.renderNs).append(',').append(sample.submitNs).append(',')
                        .append(sample.gpuNs).append(',').append(sample.frameNs).append('\n');
            }
            Files.writeString(output.resolve(prefix + "-frames.csv"), renderCsv);
            StringBuilder tickCsv = new StringBuilder("tick_ns,client_tick_ns\n");
            for (TickSample tick : ticks) tickCsv.append(tick.particleNs).append(',').append(tick.clientNs).append('\n');
            Files.writeString(output.resolve(prefix + "-ticks.csv"), tickCsv);
            if (SHADER_PACK != null) {
                StringBuilder selections = new StringBuilder();
                for (int i = 0; i < shaderSelections.size(); i++) {
                    selections.append(prefix).append(',').append(i + 1).append(',')
                            .append(shaderSelections.get(i)).append('\n');
                }
                Files.writeString(output.resolve("shader-selection.csv"), selections, StandardOpenOption.APPEND);
            }
            if (CPU_PROFILE) {
                Files.writeString(output.resolve("profile-windows.csv"),
                        prefix + ',' + Objects.requireNonNull(profileStart) + ',' + Objects.requireNonNull(profileEnd) + '\n',
                        StandardOpenOption.APPEND);
            }
        }

        private void close() {
            particles.forEach(BenchParticle::remove);
            particles.clear();
            free.forEach(GL15::glDeleteQueries);
            pending.forEach(query -> GL15.glDeleteQueries(query.id));
            free.clear();
            pending.clear();
        }
    }

    private record GpuQuery(Sample frame, int id) {}

    private static final class Sample {
        private long renderNs;
        private long submitNs;
        private long gpuNs;
        private long frameNs;
    }
}
