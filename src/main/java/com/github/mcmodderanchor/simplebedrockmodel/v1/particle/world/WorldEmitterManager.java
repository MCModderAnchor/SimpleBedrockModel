package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClock;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClocks;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleEffectDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.EventExecutor;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleMolangEnvironment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

// 用来管理世界中的粒子发射器
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = SimpleBedrockModel.MOD_ID, value = Dist.CLIENT)
public class WorldEmitterManager {
    private static final AnimationClock CLOCK = AnimationClocks.client();

    private static WorldEmitterManager INSTANCE;

    private final List<ActiveWorldEmitter> emitters = new ArrayList<>();

    public static WorldEmitterManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new WorldEmitterManager();
        }
        return INSTANCE;
    }

    /**
     * 添加一个世界粒子发射器。
     *
     * @param level      客户端世界
     * @param pos        发射器世界坐标
     * @param velocity   发射器世界速度（blocks/second）
     * @param definition 粒子效果定义
     * @return 创建的发射器实例，可用于后续控制；如果 definition 为 null 则返回 null
     */
    @Nullable
    public ParticleEmitterInstance addEmitter(ClientLevel level, Vec3 pos, Vec3 velocity, @Nullable ParticleEffectDefinition definition) {
        if (definition == null) {
            SimpleBedrockModel.LOGGER.warn("Attempted to add world emitter with null definition");
            return null;
        }

        ParticleMolangEnvironment molang = new ParticleMolangEnvironment();
        ParticleEmitterInstance emitter = new ParticleEmitterInstance(definition, molang);

        // 设置事件上下文
        EventExecutor.EventContext eventCtx = new EventExecutor.EventContext(emitter, molang, level, pos);
        emitter.setEventContext(eventCtx);

        // 触发创建事件（必须在 setEventContext 之后调用）
        emitter.fireCreationEvents();

        ActiveWorldEmitter active = new ActiveWorldEmitter();
        active.emitter = emitter;
        active.molang = molang;
        active.level = level;
        active.worldX = pos.x;
        active.worldY = pos.y;
        active.worldZ = pos.z;
        active.velocityX = velocity.x;
        active.velocityY = velocity.y;
        active.velocityZ = velocity.z;
        active.definition = definition;

        updateEmitterTransform(active);

        // 设置外部粒子管理：新生成的粒子通过回调投递到 ParticleEngine
        emitter.setExternalParticleManagement(particle -> deliverParticle(active, particle));

        emitters.add(active);
        return emitter;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (!CLOCK.shouldTick()) {
            return;
        }
        getInstance().tick();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        getInstance().clear();
    }

    /**
     * 每 tick 驱动所有发射器。
     * <p>
     * 应在 {@code TickEvent.ClientTickEvent} 中调用。
     */
    public void tick() {
        float dt = 1f / 20f; // 固定 tick 步长

        for (int i = emitters.size() - 1; i >= 0; i--) {
            ActiveWorldEmitter active = emitters.get(i);

            // 检查世界是否仍然有效
            if (active.level != Minecraft.getInstance().level) {
                emitters.remove(i);
                continue;
            }

            active.worldX += active.velocityX * dt;
            active.worldY += active.velocityY * dt;
            active.worldZ += active.velocityZ * dt;
            updateEmitterTransform(active);

            // 更新事件上下文中的位置
            Vec3 currentPos = new Vec3(active.worldX, active.worldY, active.worldZ);
            active.emitter.setEventContext(new EventExecutor.EventContext(
                    active.emitter, active.molang, active.level, currentPos));

            active.emitter.tick(dt);

            if (active.emitter.isFinished()) {
                emitters.remove(i);
            }
        }
    }

    /**
     * 将发射器产出的粒子转换为 {@link SnowStormParticle} 并投递到 ParticleEngine。
     */
    private void deliverParticle(ActiveWorldEmitter active, ParticleInstance particle) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.particleEngine == null || active.level == null) {
            return;
        }

        if (active.emitter.isLocalVelocity()) {
            particle.vx += active.velocityX;
            particle.vy += active.velocityY;
            particle.vz += active.velocityZ;
        }

        // 粒子坐标已经在世界空间中（ParticleEmitterInstance 的 applyLocalSpaceOnSpawn 已处理）
        // 如果粒子是世界空间的，坐标已经是绝对世界坐标
        // 如果粒子是局部空间的（localPosition=true），需要加上发射器位置
        double px;
        double py;
        double pz;
        if (particle.worldSpace) {
            px = particle.x;
            py = particle.y;
            pz = particle.z;
        } else {
            px = active.worldX + particle.x;
            py = active.worldY + particle.y;
            pz = active.worldZ + particle.z;
        }

        // 更新粒子位置为世界坐标
        particle.x = (float) px;
        particle.y = (float) py;
        particle.z = (float) pz;

        SnowStormParticle worldParticle = new SnowStormParticle(active.level, particle, active.definition, active.emitter);
        mc.particleEngine.add(worldParticle);
    }

    private void updateEmitterTransform(ActiveWorldEmitter active) {
        Matrix4f identity = new Matrix4f();
        Matrix4f worldTransform = new Matrix4f().translation((float) active.worldX, (float) active.worldY, (float) active.worldZ);
        active.emitter.setEmitterTransform(identity, worldTransform);
    }

    /**
     * 清空所有发射器。
     */
    public void clear() {
        emitters.clear();
    }

    public List<ActiveWorldEmitter> getEmitters() {
        return emitters;
    }

    /**
     * 获取当前活跃的发射器数量。
     */
    public int getEmitterCount() {
        return emitters.size();
    }

    /**
     * 活跃的世界发射器上下文。
     */
    public static class ActiveWorldEmitter {
        public ParticleEmitterInstance emitter;
        public ParticleMolangEnvironment molang;
        public ClientLevel level;
        public double worldX, worldY, worldZ;
        public double velocityX, velocityY, velocityZ;
        public ParticleEffectDefinition definition;
    }
}
