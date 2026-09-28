package example.client.worldmesh;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.BedrockModelRenderTypes;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.world.WorldMeshRenderer;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.world.WorldMeshGroup;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.world.MeshRenderable;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.world.GeometryCollector;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.world.WorldMeshStrategy;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.TreeModelInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.TreeBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.BedrockModelResources;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 压测挂具：在玩家前方生成一批"虚拟实例"（不依赖世界方块），用同一份数据在静态路径与动态路径之间切换。
 *
 * <p>四个模式用来分离变量：</p>
 * <ul>
 *   <li>{@code same}：同一模型 + 同一贴图 → 全部实例共享一个 VBO、一个材质组。</li>
 *   <li>{@code models}：不同模型 + 同一贴图 → 每实例一个 VBO，但仍是一个材质组。</li>
 *   <li>{@code all}：不同模型 + 不同贴图 → 每实例一个 VBO，且每个 RenderType 都要单独 setup/clearState。</li>
 *   <li>{@code dynamic}：同 {@code all}，但走原版/AR 的动态路径（每帧骨骼遍历 + MultiBufferSource）。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = SimpleBedrockModel.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ExampleStressMeshGroup {
    public static final String ID = "example:stress";

    public enum Mode {
        SAME, MODELS, ALL, DYNAMIC
    }

    private static WorldMeshGroup<Instance> group;
    private static final List<Instance> INSTANCES = new ArrayList<>();
    private static ClientLevel world;

    private static boolean registered;
    private static Mode mode = Mode.SAME;
    private static int spawnFailures;
    private static boolean lightCycling;
    private static ClientLevel lightLevel;
    private static int lightIntervalTicks = 20;
    /** 0 表示每轮更新全部实例，否则轮转更新指定数量。 */
    private static int lightBatchSize;
    private static int lightCountdown;
    private static int lightCursor;
    private static long lightRounds;
    private static long lightChanges;
    private static double lightUpdateMicros;

    private static final class Instance implements MeshRenderable {
        boolean valid = true;
        Object geometryKey;
        TreeBedrockModel model;
        TreeModelInstance pose;
        ResourceLocation modelId;
        ResourceLocation texture;
        Vec3 origin;
        int light;
        int sampledLight;
        int lightStep;

        @Override public boolean isValid() { return this.valid; }
        @Override public boolean needsUpdate() { return false; }
        @Override public Vec3 origin() { return this.origin; }
        @Override public int packedLight() { return this.light; }
        @Override public Object geometryKey() { return this.geometryKey; }
        @Override public boolean collectGeometry(GeometryCollector collector) {
            return collector.blockModel(this.modelId, this.texture, Direction.NORTH);
        }
    }

    private ExampleStressMeshGroup() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        group = WorldMeshRenderer.createGroup(ID, WorldMeshStrategy.INSTANCE);
    }

    static String stats() {
        return "stress{mode=" + mode + ", instances=" + INSTANCES.size()
                + ", spawnFailures=" + spawnFailures
                + ", lightCycle=" + lightCycling + ", intervalTicks=" + lightIntervalTicks
                + ", batchSize=" + (lightBatchSize == 0 ? "all" : lightBatchSize)
                + ", lightRounds=" + lightRounds + ", lightChanges=" + lightChanges
                + ", lightUpdateUs=" + String.format(Locale.ROOT, "%.1f", lightUpdateMicros) + "}";
    }

    /** 清空压测对象；具体缓冲生命周期由渲染组负责。 */
    static void clear() {
        for (Instance instance : INSTANCES) instance.valid = false;
        group.clear();
        INSTANCES.clear();
        stopLightCycle();
        lightRounds = lightChanges = 0;
        lightUpdateMicros = 0.0;
        lightCursor = 0;
    }

    /** 配置仅影响现有压测数据；使用客户端 tick，暂停游戏时停止计时。 */
    static boolean startLightCycle(int intervalTicks, int batchSize) {
        if (intervalTicks < 1 || batchSize < 0) throw new IllegalArgumentException("Invalid light cycle");
        if (INSTANCES.isEmpty() || Minecraft.getInstance().level == null) return false;
        lightIntervalTicks = intervalTicks;
        lightBatchSize = batchSize;
        lightCountdown = intervalTicks;
        lightCursor = 0;
        lightRounds = lightChanges = 0;
        lightUpdateMicros = 0.0;
        lightLevel = Minecraft.getInstance().level;
        lightCycling = true;
        return true;
    }

    /** 停止定时修改，保留当前合成光照，便于查看静态结果。 */
    static void stopLightCycle() {
        lightCycling = false;
        lightLevel = null;
        lightCountdown = 0;
    }

    /** 恢复生成压力场景时采样的光照，不重建模型。 */
    static void resetLightCycle() {
        stopLightCycle();
        for (Instance instance : INSTANCES) setLight(instance, instance.sampledLight);
    }

    private static void setLight(Instance instance, int light) {
        if (instance.light == light) return;
        instance.light = light;
        if (mode != Mode.DYNAMIC) group.markDirty(instance);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!registered || event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (world != minecraft.level) {
            clear();
            world = minecraft.level;
        }
        if (!lightCycling) return;
        if (minecraft.level != lightLevel || INSTANCES.isEmpty()) {
            stopLightCycle();
            return;
        }
        if (minecraft.isPaused() || --lightCountdown > 0) return;
        lightCountdown = lightIntervalTicks;
        long start = System.nanoTime();
        int count = lightBatchSize == 0 ? INSTANCES.size() : Math.min(lightBatchSize, INSTANCES.size());
        for (int i = 0; i < count; i++) {
            Instance instance = INSTANCES.get(lightCursor);
            instance.lightStep = (instance.lightStep + 1) & 15;
            int light = LightTexture.pack(instance.lightStep, instance.lightStep);
            if (light == instance.light) {
                instance.lightStep = (instance.lightStep + 1) & 15;
                light = LightTexture.pack(instance.lightStep, instance.lightStep);
            }
            setLight(instance, light);
            lightChanges++;
            lightCursor = (lightCursor + 1) % INSTANCES.size();
        }
        lightRounds++;
        lightUpdateMicros = (System.nanoTime() - start) / 1000.0;
    }

    static int spawn(Mode newMode, int count, Player player) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || player == null) {
            return 0;
        }
        clear();
        world = level;
        mode = newMode;
        spawnFailures = 0;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0, look.z);
        forward = forward.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);

        int columns = Mth.ceil(Math.sqrt(count));
        double spacing = 1.6;
        double distance = 7.0;
        Vec3 center = eye.add(forward.scale(distance));
        double baseY = Math.floor(eye.y) - 1.0;

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            int column = i % columns;
            int row = i / columns;
            double offset = (column - (columns - 1) / 2.0) * spacing;
            Vec3 origin = new Vec3(center.x + right.x * offset, baseY + row * spacing, center.z + right.z * offset);

            int modelIndex = newMode == Mode.SAME ? 0 : i % ExampleStressResources.COUNT;
            int textureIndex = newMode == Mode.ALL || newMode == Mode.DYNAMIC ? i % ExampleStressResources.COUNT : 0;
            ResourceLocation modelId = ExampleStressResources.model(modelIndex);
            ResourceLocation texture = ExampleStressResources.texture(textureIndex);
            TreeBedrockModel model = BedrockModelResources.getInstance().getTreeModel(modelId);
            if (model == null) {
                spawnFailures++;
                continue;
            }

            Instance instance = new Instance();
            instance.modelId = modelId;
            instance.texture = texture;
            instance.origin = origin;
            instance.light = LevelRenderer.getLightColor(level,
                    BlockPos.containing(origin.x, origin.y + 0.5, origin.z));
            instance.sampledLight = instance.light;
            instance.lightStep = i & 15;

            if (newMode == Mode.DYNAMIC) {
                instance.model = model;
                instance.pose = model.createInstance();
            }
            INSTANCES.add(instance);
            if (newMode != Mode.DYNAMIC) {
                instance.geometryKey = GeometryCollector.blockModelKey(modelId, texture, Direction.NORTH);
                group.track(instance);
            }
            spawned++;
        }
        return spawned;
    }

    /** 动态对照组：同一批实例走原版路径（装了 AR 时就是 AR 路径），姿势与静态路径逐字对齐。 */
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (mode != Mode.DYNAMIC || INSTANCES.isEmpty()) {
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PoseStack poseStack = new PoseStack();
        // 必须从事件里的 pose stack 起手：它含相机视图旋转，方块实体路径就是这么给顶点的
        // （BufferUploader 用的 ModelViewMat 是世界渲染期的单位阵）。少了这一步，顶点只有相机相对位移、
        // 没有视图旋转，几何会投到错误的屏幕位置。
        poseStack.mulPoseMatrix(event.getPoseStack().last().pose());
        for (Instance instance : INSTANCES) {
            TreeBedrockModel model = BedrockModelResources.getInstance().getTreeModel(instance.modelId);
            if (model == null) continue;
            if (model != instance.model || instance.pose == null) {
                instance.model = model;
                instance.pose = model.createInstance();
            }
            poseStack.pushPose();
            poseStack.translate(instance.origin.x - camera.x + 0.5, instance.origin.y - camera.y,
                    instance.origin.z - camera.z + 0.5);
            instance.pose.resetPose();
            instance.model.renderToBuffer(instance.pose, poseStack, buffers,
                    RenderType.entityCutout(instance.texture),
                    BedrockModelRenderTypes.polyMeshCutout(instance.texture),
                    instance.light, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        buffers.endBatch();
    }
}
