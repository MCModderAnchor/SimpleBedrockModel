package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.value.NumberValue;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleEffectDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.*;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.lifetime.*;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.motion.*;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.rate.*;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.shape.*;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.curve.ParticleCurve;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.event.IEventNode;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ParticleEmitterInstance {
    public static final int MAX_PARTICLES = 16384;
    public static final int POOL_SIZE = 16384 / 4;
    private static final Random RANDOM = new Random();

    private final ParticleEffectDefinition definition;
    private final ParticleMolangEnvironment molang;

    // 发射器状态
    private float emitterAge;
    private float emitterLifetime;
    private boolean active = true;
    private boolean removed;
    private boolean sleeping;
    private float sleepTimer;
    private float currentDt;

    // Runtime 组件调度
    private final List<IEmitterComponent> emitterUpdateComponents = new ArrayList<>();

    private final int emitterRandom1 = RANDOM.nextInt();
    private final int emitterRandom2 = RANDOM.nextInt();
    private final int emitterRandom3 = RANDOM.nextInt();
    private final int emitterRandom4 = RANDOM.nextInt();

    private final List<ParticleInstance> particles = new ArrayList<>();
    private final List<ParticleInstance> pool = new ArrayList<>();

    private final Vector4f tempSpawnVec = new Vector4f();
    private final Matrix4f emitterTransform = new Matrix4f();
    private final Matrix4f worldTransform = new Matrix4f();
    private boolean hasTransform;

    private boolean localPosition;
    private boolean localRotation;
    private boolean localVelocity;

    private boolean fpMode;
    private boolean fpLocalPosition;
    private boolean fpLocalRotation;
    private boolean fpLocalVelocity;
    private boolean fpToWorld;

    private final Map<String, ParticleCurve> curves;

    @Nullable
    private EventExecutor.EventContext eventContext;

    @Nullable
    private ParticleSpawnCallback spawnCallback;
    private boolean externalParticleManagement;

    @FunctionalInterface
    public interface ParticleSpawnCallback {
        void onParticleSpawned(ParticleInstance particle);
    }

    // ==================== 构造 ====================

    public ParticleEmitterInstance(ParticleEffectDefinition definition, ParticleMolangEnvironment molang) {
        this.definition = definition;
        this.molang = molang;
        this.curves = definition.getCurves();
        createRuntimeComponents();
        restartCycle();
    }

    private void createRuntimeComponents() {
        for (IEmitterComponentDefinition def : definition.emitterPreset().components()) {
            IEmitterComponent runtime = def.createRuntime();
            runtime.apply(this);
            if (def.requireUpdate()) {
                emitterUpdateComponents.add(runtime);
            }
        }
    }

    // ==================== 主循环 ====================

    public void tick(float dt) {
        if (removed) {
            updateParticles(dt);
            return;
        }
        if (sleeping) {
            sleepTimer -= dt;
            if (sleepTimer <= 0) restartCycle();
            updateParticles(dt);
            return;
        }

        this.currentDt = dt;
        bindEmitterContext();
        for (IEmitterComponent c : emitterUpdateComponents) {
            c.update(this);
        }
        updateParticles(dt);
    }

    private void restartCycle() {
        removed = false;
        sleeping = false;
        active = true;
        emitterAge = 0;
        bindEmitterContext();

        emitterUpdateComponents.clear();
        for (IEmitterComponentDefinition def : definition.emitterPreset().components()) {
            IEmitterComponent runtime = def.createRuntime();
            runtime.apply(this);
            if (def.requireUpdate()) {
                emitterUpdateComponents.add(runtime);
            }
        }

        bindEmitterContext();
        // 发射器创建事件（由 EmitterLifetimeEvents.Runtime 处理，这里通过预设中的记录触发）
        EmitterLifetimeEvents evts = definition.emitterPreset().find(EmitterLifetimeEvents.class);
        if (evts != null && eventContext != null && !evts.creationEvent().isEmpty()) {
            EventExecutor.fireEvents(evts.creationEvent(), definition, eventContext);
        }
    }

    private void bindEmitterContext() {
        molang.bindEmitter(emitterAge, emitterLifetime, emitterRandom1, emitterRandom2, emitterRandom3, emitterRandom4);
        for (Map.Entry<String, ParticleCurve> entry : curves.entrySet()) {
            float value = CurveEvaluator.evaluate(entry.getValue(), molang.getContext(), entry.getKey());
            molang.getVariableStorage().set(entry.getKey(), NumberValue.of(value));
        }
    }

    // ==================== 粒子管理 ====================

    private void spawnParticleInternal() {
        ParticleInstance p = obtainParticle();
        p.reset();
        p.random1 = RANDOM.nextFloat();
        p.random2 = RANDOM.nextFloat();
        p.random3 = RANDOM.nextFloat();
        p.random4 = RANDOM.nextFloat();

        p.emitter = this;

        // 绑定粒子变量到 emitter 的共享 molang
        molang.bindParticle(0, 1, p.random1, p.random2, p.random3, p.random4);

        // 发射器形状 → 粒子初始位置（emit 时采样一次）
        EmitterShape shape = definition.emitterPreset().find(EmitterShape.class);
        if (shape != null) {
            shape.applyPosition(p, molang.getContext(), RANDOM);
        }

        // 粒子 Runtime 组件
        p.updateComponents = new ArrayList<>();
        for (IParticleComponentDefinition def : definition.particlePreset().components()) {
            IParticleComponent runtime = def.createRuntime();
            runtime.apply(p);
            if (def.requireUpdate()) {
                p.updateComponents.add(runtime);
            }
        }

        if (hasTransform) applyLocalSpaceOnSpawn(p);

        if (spawnCallback != null && (externalParticleManagement || p.worldSpace)) {
            spawnCallback.onParticleSpawned(p);
        } else {
            particles.add(p);
        }
    }

    /** 公共入口，供 Runtime 组件调用 */
    public void spawnParticle() {
        spawnParticleInternal();
    }

    public int emitManual(int count) {
        EmitterRateManual manual = definition.emitterPreset().find(EmitterRateManual.class);
        if (manual == null || count <= 0 || removed || sleeping || !active) return 0;

        bindEmitterContext();
        int maxParticles = (int) manual.maxParticles().evaluate(molang.getContext());
        int effectiveMax = Math.min(maxParticles, MAX_PARTICLES);
        int available = Math.max(0, effectiveMax - particles.size());
        int spawnCount = Math.min(count, available);
        for (int i = 0; i < spawnCount; i++) {
            spawnParticleInternal();
        }
        return spawnCount;
    }

    private void updateParticles(float dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            ParticleInstance p = particles.get(i);
            updateSingleParticle(p, dt);
            if (!p.alive) {
                fireParticleExpirationEvents(p);
                int last = particles.size() - 1;
                if (i != last) particles.set(i, particles.get(last));
                particles.remove(last);
                recycleParticle(p);
            }
        }
    }

    public void updateSingleParticle(ParticleInstance p, float dt) {
        molang.bindParticle(p.age, p.maxLifetime, p.random1, p.random2, p.random3, p.random4);

        for (IParticleComponent c : p.updateComponents) {
            c.update(p);
        }

        p.tick(dt);

        // 过期事件由 ParticleLifetimeExpression.Runtime.update() 处理
        // killPlane 由 ParticleLifetimeKillPlane.update() 处理
        // timeline 事件由 ParticleLifetimeEvents.Runtime.update() 处理
    }

    // ==================== 局部空间变换 ====================

    private void applyLocalSpaceOnSpawn(ParticleInstance p) {
        boolean effectiveLocalPos = fpMode ? fpLocalPosition : localPosition;
        boolean effectiveLocalRot = fpMode ? fpLocalRotation : localRotation;
        if (!effectiveLocalPos) {
            float scale = extractScale(worldTransform);
            p.spawnScale = scale;
            worldTransform.transform(tempSpawnVec.set(p.x, p.y, p.z, 1));
            p.x = tempSpawnVec.x; p.y = tempSpawnVec.y; p.z = tempSpawnVec.z;
            p.worldSpace = true;
            if (!effectiveLocalRot) transformVelocityByMatrix(p, worldTransform, scale);
            else { p.vx *= scale; p.vy *= scale; p.vz *= scale; }
        } else if (fpMode) {
            Matrix4f ref = fpToWorld ? worldTransform : emitterTransform;
            float scale = extractScale(ref);
            p.spawnScale = scale;
            ref.transform(tempSpawnVec.set(p.x, p.y, p.z, 1));
            p.x = tempSpawnVec.x; p.y = tempSpawnVec.y; p.z = tempSpawnVec.z;
            if (fpToWorld) p.worldSpace = true; else p.fpDetached = true;
            if (!effectiveLocalRot) transformVelocityByMatrix(p, ref, scale);
            else { p.vx *= scale; p.vy *= scale; p.vz *= scale; }
        } else {
            p.spawnScale = extractScale(emitterTransform);
        }
    }

    private static float extractScale(Matrix4f mat) {
        float m00 = mat.m00(), m01 = mat.m01(), m02 = mat.m02();
        return (float) Math.sqrt(m00 * m00 + m01 * m01 + m02 * m02);
    }

    private static void transformVelocityByMatrix(ParticleInstance p, Matrix4f mat, float scale) {
        float c0x = mat.m00(), c0y = mat.m01(), c0z = mat.m02();
        float c1x = mat.m10(), c1y = mat.m11(), c1z = mat.m12();
        float c2x = mat.m20(), c2y = mat.m21(), c2z = mat.m22();
        float s0 = (float) Math.sqrt(c0x * c0x + c0y * c0y + c0z * c0z);
        float s1 = (float) Math.sqrt(c1x * c1x + c1y * c1y + c1z * c1z);
        float s2 = (float) Math.sqrt(c2x * c2x + c2y * c2y + c2z * c2z);
        if (s0 > 1e-6f && s1 > 1e-6f && s2 > 1e-6f) {
            float rx = (c0x / s0) * p.vx + (c1x / s1) * p.vy + (c2x / s2) * p.vz;
            float ry = (c0y / s0) * p.vx + (c1y / s1) * p.vy + (c2y / s2) * p.vz;
            float rz = (c0z / s0) * p.vx + (c1z / s1) * p.vy + (c2z / s2) * p.vz;
            p.vx = rx * scale;
            p.vy = ry * scale;
            p.vz = rz * scale;
        }
    }

    // ==================== 粒子池 ====================

    private ParticleInstance obtainParticle() {
        return !pool.isEmpty() ? pool.remove(pool.size() - 1) : new ParticleInstance();
    }

    private void recycleParticle(ParticleInstance p) {
        if (pool.size() < POOL_SIZE) pool.add(p);
    }

    // ==================== 公共 API ====================

    public void setEmitterTransform(Matrix4f locatorTransform, Matrix4f worldTransformIn) {
        emitterTransform.set(locatorTransform);
        worldTransform.set(worldTransformIn);
        hasTransform = true;
    }

    public Matrix4f getEmitterTransform() { return emitterTransform; }
    public Matrix4f getWorldTransform() { return worldTransform; }

    public void setLocalSpaceFlags(boolean pos, boolean rot, boolean vel) {
        this.localPosition = pos;
        this.localRotation = rot;
        this.localVelocity = vel;
    }

    public boolean isLocalPosition() { return localPosition; }
    public boolean isLocalRotation() { return localRotation; }
    public boolean isLocalVelocity() { return localVelocity; }

    public float getDt() { return currentDt; }
    public int getParticleCount() { return particles.size(); }
    public boolean isActive() { return active; }
    public boolean isFinished() { return removed && particles.isEmpty(); }

    public void setRemoved(boolean removed) { this.removed = removed; }
    public void setActive(boolean active) { this.active = active; }
    public void setSleeping(boolean sleeping) { this.sleeping = sleeping; }
    public void setSleepTimer(float t) { this.sleepTimer = t; }
    public float getSleepTimer() { return sleepTimer; }

    public float getEmitterAge() { return emitterAge; }
    public void setEmitterAge(float age) { this.emitterAge = age; }
    public float getEmitterLifetime() { return emitterLifetime; }
    public void setEmitterLifetime(float lt) { this.emitterLifetime = lt; }

    public void bindContextAndCurves() { bindEmitterContext(); }

    public ParticleEffectDefinition getDefinition() { return definition; }
    public ParticleMolangEnvironment getMolang() { return molang; }
    public List<ParticleInstance> getParticles() { return particles; }

    /** 暴露 emitter 级 Runtime 组件列表，供 Loop 重启等场景遍历重置。 */
    public List<IEmitterComponent> getEmitterUpdateComponents() { return emitterUpdateComponents; }

    public void restart() {
        active = true;
        removed = false;
        sleeping = false;
        sleepTimer = 0;
        particles.clear();
        hasTransform = false;
        emitterTransform.identity();
        worldTransform.identity();
        restartCycle();
    }

    public void setExternalParticleManagement(@Nullable ParticleSpawnCallback callback) {
        this.spawnCallback = callback;
        this.externalParticleManagement = callback != null;
    }

    public void setWorldSpaceParticleCallback(@Nullable ParticleSpawnCallback callback) {
        this.spawnCallback = callback;
        this.externalParticleManagement = false;
    }

    public void enableFPMode() {
        FPEmitterLocalSpace fpSpace = definition.findComponent(FPEmitterLocalSpace.class);
        if (fpSpace != null) fpSpace.apply(this);
    }

    public void enableFPMode(boolean pos, boolean rot, boolean vel, boolean toWorld) {
        this.fpMode = true;
        this.fpLocalPosition = pos;
        this.fpLocalRotation = rot;
        this.fpLocalVelocity = vel;
        this.fpToWorld = toWorld;
    }

    public boolean isFPMode() { return fpMode; }

    // ==================== 事件系统 ====================

    public void setEventContext(@Nullable EventExecutor.EventContext context) { this.eventContext = context; }

    @Nullable
    public EventExecutor.EventContext getEventContext() { return eventContext; }

    public void fireCreationEvents() {
        if (eventContext == null) return;
        EmitterLifetimeEvents evts = definition.emitterPreset().find(EmitterLifetimeEvents.class);
        if (evts != null && !evts.creationEvent().isEmpty()) {
            EventExecutor.fireEvents(evts.creationEvent(), definition, eventContext);
        }
    }

    public void fireExpirationEvents() {
        if (eventContext == null) return;
        EmitterLifetimeEvents evts = definition.emitterPreset().find(EmitterLifetimeEvents.class);
        if (evts != null && !evts.expirationEvent().isEmpty()) {
            EventExecutor.fireEvents(evts.expirationEvent(), definition, eventContext);
        }
    }

    public void fireParticleExpirationEvents(ParticleInstance p) {
        if (eventContext == null) return;
        ParticleLifetimeEvents evts = definition.particlePreset().find(ParticleLifetimeEvents.class);
        if (evts == null || evts.expirationEvent().isEmpty()) return;
        molang.bindParticle(p.age, p.maxLifetime, p.random1, p.random2, p.random3, p.random4);
        EventExecutor.fireEvents(evts.expirationEvent(), definition, eventContext);
    }

    public void fireCollisionEvents(ParticleInstance p, float speed) {
        if (eventContext == null) return;
        ParticleMotionCollision collision = definition.findComponent(ParticleMotionCollision.class);
        if (collision == null || collision.events().isEmpty()) return;
        molang.bindParticle(p.age, p.maxLifetime, p.random1, p.random2, p.random3, p.random4);
        for (ParticleMotionCollision.CollisionEvent event : collision.events()) {
            if (speed >= event.minSpeed()) {
                List<IEventNode> nodes = definition.getEvents().get(event.event());
                if (nodes != null) {
                    for (IEventNode node : nodes) EventExecutor.execute(node, eventContext);
                }
            }
        }
    }

    public void applyViewerOffset(float dx, float dy, float dz) {
        for (ParticleInstance p : particles) {
            if (p.worldSpace) { p.x -= dx; p.y -= dy; p.z -= dz; }
        }
    }
}
