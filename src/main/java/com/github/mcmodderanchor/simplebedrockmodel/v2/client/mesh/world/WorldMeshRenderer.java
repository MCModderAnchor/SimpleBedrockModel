package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache.MeshCachePolicy;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.WorldMeshBuffers;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.WorldMeshPart;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.MeshVertexFormat;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.render.MeshBatchRenderer;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.config.WorldMeshConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 世界空间网格绘制的库侧入口（原型）。
 *
 * <p>职责：渲染组登记、世界阶段调度、剔除与统计；绘制交给共用 MeshBatchRenderer。{@link WorldMeshGroup} 管理对象、缓存、策略和失效，
 * 调用方只提供几何描述与对象状态。底层句柄和上传入口不作为公共 API。</p>
 *
 * <p>绘制阶段固定为 {@code AFTER_BLOCK_ENTITIES}：它由 {@code LevelRenderer.renderLevel} 直接派发，
 * 不受 Embeddium 替换地形管线影响，也不在 Iris/Oculus 的 shadow pass 中触发。</p>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = SimpleBedrockModel.MOD_ID, value = Dist.CLIENT)
public class WorldMeshRenderer {
    private static final Map<String, WorldMeshGroup<?>> MESH_GROUPS = new LinkedHashMap<>();

    public enum Invalidation { WORLD, RESOURCES, SHUTDOWN }

    private static final WorldMeshMetrics METRICS = new WorldMeshMetrics();
    public static final WorldMeshBuffers BUFFERS = new WorldMeshBuffers(METRICS);

    private static final List<CollectedShard> COLLECTED = new ArrayList<>();
    private static final LinkedHashMap<RenderType, List<CollectedShard>> GROUPS = new LinkedHashMap<>();
    private static final List<RenderType> GROUP_ORDER = new ArrayList<>();
    private static final Matrix4f SCRATCH_VIEW = new Matrix4f();
    private static int collectedCount;

    private static boolean enabled = true;
    private static long stateUpdateTick;
    private static long worldDrawSerial;
    private static ClientLevel level;

    /** 顶点格式漂移的探测间隔；光影开关会让 Iris/AR 换掉 NEW_ENTITY 对应的格式。 */
    private static final int FORMAT_CHECK_FRAMES = 60;
    private static int formatCheckCountdown = FORMAT_CHECK_FRAMES;
    private static VertexFormat probedFormat;

    /** 绘制枚举结果：句柄 + 本实例的原点与包围盒。去重后同一个句柄会被多个实例重复枚举。 */
    public static class CollectedShard {
        WorldMeshPart handle;
        Vec3 origin;
        Matrix4f localTransform;
        AABB bounds;
        /** 归一后的实例光照：INSTANCE 使用实例值，FIXED 归零。 */
        int light;
    }

    public WorldMeshRenderer() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static long nextWorldDrawSerial() {
        return worldDrawSerial + 1;
    }

    public static long currentWorldDrawSerial() {
        return worldDrawSerial;
    }

    /** 运行时开关；关闭时丢弃几何缓存，保留渲染组对象，重新打开后会重新捕获。 */
    public static void setEnabled(boolean value) {
        RenderSystem.assertOnRenderThread();
        if (value == enabled) {
            return;
        }
        enabled = value;
        if (!value) {
            invalidate(Invalidation.SHUTDOWN);
        }
    }

    public static <K> WorldMeshGroup<K> createGroup(String id, WorldMeshStrategy defaultStrategy) {
        return createGroup(id, defaultStrategy, EnumSet.allOf(WorldMeshStrategy.class));
    }

    /** 在渲染线程创建渲染组；同 ID 不允许重复创建，关闭后可重新创建。 */
    public static <K> WorldMeshGroup<K> createGroup(String id, WorldMeshStrategy defaultStrategy,
                                                Set<WorldMeshStrategy> supported) {
        return createGroup(id, defaultStrategy, supported, MeshCachePolicy.DEFAULT);
    }

    /** LRU 限额只约束空闲几何；active／pending 网格仍持有引用，不参与淘汰。 */
    public static <K> WorldMeshGroup<K> createGroup(String id, WorldMeshStrategy defaultStrategy,
                                                Set<WorldMeshStrategy> supported, MeshCachePolicy cachePolicy) {
        RenderSystem.assertOnRenderThread();
        if (MESH_GROUPS.containsKey(id)) throw new IllegalArgumentException("Duplicate group: " + id);
        WorldMeshGroup<K> group = new WorldMeshGroup<>(id, defaultStrategy, supported, cachePolicy);
        MESH_GROUPS.put(id, group);
        group.applyPolicy();
        return group;
    }

    public static void unregister(WorldMeshGroup<?> group) {
        if (!MESH_GROUPS.remove(group.id(), group)) return;
        BUFFERS.removeOwner(group.id());
        clearFrameData();
        if (MESH_GROUPS.isEmpty()) {
            BUFFERS.clear();
        }
    }

    /** 修改客户端配置中的策略选择，并立即更新已注册渲染组。 */
    public static void setStrategy(StrategyOverride value) {
        RenderSystem.assertOnRenderThread();
        WorldMeshConfig.STRATEGY.set(Objects.requireNonNull(value, "value"));
        WorldMeshConfig.STRATEGY.save();
        for (WorldMeshGroup<?> group : MESH_GROUPS.values()) group.applyPolicy();
    }

    public static List<WorldMeshGroupStats> groupStats() {
        RenderSystem.assertOnRenderThread();
        return MESH_GROUPS.values().stream().map(WorldMeshGroup::stats).toList();
    }

    /** 保留逻辑对象，重新解析几何描述并重建缓存。 */
    public static void clearCaches() { invalidate(Invalidation.RESOURCES); }

    /**
     * 顶点格式漂移自愈：每隔 {@link #FORMAT_CHECK_FRAMES} 帧用一次 1 个 quad 的探针确认"现在
     * {@code NEW_ENTITY} 会被哪个格式接手"，一变就整体失效重烘。
     *
     * <p>为什么需要它：Iris 在有光影包时无条件打开扩展顶点格式，AR 还会把 Iris 的 ENTITY 换成自己的
     * 扩展版本。已上传的 VBO 不会自动跟随格式变化；这里只检测格式对象变化，
     * 不能覆盖所有 shader 语义变化或兼容性问题。资源内容重载由独立监听处理。</p>
     */
    public static void checkFormatDrift() {
        if (--formatCheckCountdown > 0) {
            return;
        }
        formatCheckCountdown = FORMAT_CHECK_FRAMES;
        VertexFormat probed = MeshVertexFormat.probe();
        if (probed == null) {
            return;
        }
        if (probedFormat == null) {
            probedFormat = probed;
            return;
        }
        if (probedFormat != probed) {
            probedFormat = probed;
            METRICS.recordFormatChange();
            invalidate(Invalidation.RESOURCES);
        }
    }

    /** 失效全部渲染组缓存；世界切换、资源重载与运行开关关闭都会走这里。 */
    public static void invalidate(Invalidation kind) {
        RenderSystem.assertOnRenderThread();
        Objects.requireNonNull(kind, "kind");
        BUFFERS.clear();
        for (WorldMeshGroup<?> group : MESH_GROUPS.values()) {
            group.invalidate(kind);
        }
        clearFrameData();
    }

    public static void clearFrameData() {
        COLLECTED.clear();
        GROUPS.clear();
        GROUP_ORDER.clear();
        collectedCount = 0;
        METRICS.clearFrame();
    }

    /** 按需获取统计快照；与渲染状态一样，应在渲染线程读取。 */
    public static WorldMeshStats stats() {
        RenderSystem.assertOnRenderThread();
        return METRICS.snapshot(enabled, MESH_GROUPS.size(), BUFFERS.size(), BUFFERS.pool);
    }

    public static long stateUpdateTick() { return stateUpdateTick; }

    @SubscribeEvent
    @org.jetbrains.annotations.ApiStatus.Internal
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!MESH_GROUPS.isEmpty()) stateUpdateTick++;
    }

    @SubscribeEvent
    @org.jetbrains.annotations.ApiStatus.Internal
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        Runnable unload = () -> {
            if (event.getLevel() == level) {
                level = null;
                invalidate(Invalidation.WORLD);
            }
        };
        if (RenderSystem.isOnRenderThread()) {
            unload.run();
        } else {
            RenderSystem.recordRenderCall(unload::run);
        }
    }

    /**
     * 在渲染组登记实例前同步世界身份；可能触发 WORLD 失效，故须先调用再写入实例数据。
     * 返回是否存在客户端世界，不代表渲染器开启或上传就绪。
     */
    public static boolean ensureLevel() {
        RenderSystem.assertOnRenderThread();
        ClientLevel current = Minecraft.getInstance().level;
        if (current == level) {
            return current != null;
        }
        level = current;
        invalidate(Invalidation.WORLD);
        return current != null;
    }

    @SubscribeEvent
    @org.jetbrains.annotations.ApiStatus.Internal
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (MESH_GROUPS.isEmpty() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        worldDrawSerial++;
        if (!ensureLevel() || MESH_GROUPS.isEmpty()) return;
        if (!enabled) {
            for (WorldMeshGroup<?> group : MESH_GROUPS.values()) group.refreshObjects();
            return;
        }
        long now = System.nanoTime();
        METRICS.beginFrame(now);
        checkFormatDrift();
        long passStart = now;

        for (WorldMeshGroup<?> group : MESH_GROUPS.values()) {
            group.prepareFrame();
        }

        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        Frustum frustum = event.getFrustum();
        Matrix4f baseView = event.getModelViewMatrix();
        Matrix4f projection = event.getProjectionMatrix();

        collectedCount = 0;
        for (WorldMeshGroup<?> group : MESH_GROUPS.values()) {
            group.collect(WorldMeshRenderer::collect);
        }

        for (List<CollectedShard> group : GROUPS.values()) {
            group.clear();
        }
        GROUP_ORDER.clear();

        int pending = 0;
        int failed = 0;
        int culled = 0;
        int shards = 0;
        for (int i = 0; i < collectedCount; i++) {
            CollectedShard shard = COLLECTED.get(i);
            WorldMeshPart handle = shard.handle;
            if (!handle.isAlive()) {
                continue;
            }
            if (handle.uploadFailed()) {
                failed++;
                continue;
            }
            if (!handle.isUploaded()) {
                pending++;
                continue;
            }
            if (shard.bounds != null && !frustum.isVisible(shard.bounds)) {
                culled++;
                continue;
            }
            RenderType material = handle.material();
            List<CollectedShard> group = GROUPS.computeIfAbsent(material, ignored -> new ArrayList<>());
            if (group.isEmpty()) {
                GROUP_ORDER.add(material);
            }
            group.add(shard);
            shards++;
        }

        METRICS.recordPrepared(shards, culled, pending, failed);
        if (GROUP_ORDER.isEmpty()) {
            METRICS.endFrame(0, 0, passStart);
            return;
        }

        GROUP_ORDER.sort(Comparator.comparingInt(WorldMeshRenderer::materialOrder));

        MeshBatchRenderer batch = new MeshBatchRenderer(projection);
        try (batch) {
            for (RenderType type : GROUP_ORDER) {
                List<CollectedShard> shardsOfType = GROUPS.get(type);
                shardsOfType.sort(Comparator
                        .comparingInt((CollectedShard shard) -> shard.handle.id())
                        .thenComparingInt(shard -> shard.light)
                        .thenComparingDouble(shard -> shard.bounds == null ? 0.0 : shard.bounds.distanceToSqr(cameraPosition)));
                if (!batch.beginMaterial(type, baseView)) continue;
                WorldMeshPart previous = null;
                int previousLight = 0;
                for (CollectedShard shard : shardsOfType) {
                    WorldMeshPart handle = shard.handle;
                    batch.bind(handle.buffer());
                    if (previous != handle || previousLight != shard.light) {
                        if (!BUFFERS.prepareDraw(handle, shard.light)) continue;
                        previous = handle;
                        previousLight = shard.light;
                    }
                    Vec3 origin = shard.origin;
                    SCRATCH_VIEW.set(baseView).translate((float) (origin.x - cameraPosition.x),
                            (float) (origin.y - cameraPosition.y), (float) (origin.z - cameraPosition.z))
                            .mul(shard.localTransform);
                    batch.drawWorld(SCRATCH_VIEW, shard.localTransform);
                }
            }
        }
        METRICS.endFrame(batch.draws(), batch.setups(), passStart);
    }

    public static void collect(WorldMeshPart handle, Vec3 origin, Matrix4f localTransform,
                                AABB worldBounds, int packedLight) {
        CollectedShard shard;
        if (collectedCount < COLLECTED.size()) {
            shard = COLLECTED.get(collectedCount);
        } else {
            shard = new CollectedShard();
            COLLECTED.add(shard);
        }
        shard.handle = handle;
        shard.origin = origin;
        shard.localTransform = localTransform;
        shard.bounds = worldBounds;
        // FIXED / MUTABLE 使用网格自身的光照，忽略实例值；
        // UNIFORM 从整数参数表读取实例光照，按光照排序可减少同一 VAO 的参数偏移更新。
        shard.light = handle.lightMode() == WorldMeshPart.LightMode.FIXED
                || handle.lightMode() == WorldMeshPart.LightMode.MUTABLE ? 0 : packedLight;
        collectedCount++;
    }

    public static int materialOrder(RenderType type) {
        int chunkLayer = type.getChunkLayerId();
        return chunkLayer >= 0 ? chunkLayer : 100;
    }
}
