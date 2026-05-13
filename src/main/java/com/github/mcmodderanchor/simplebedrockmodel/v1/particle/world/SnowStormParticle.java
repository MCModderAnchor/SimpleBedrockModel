package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world;

import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleDescription;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleEffectDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.*;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.motion.*;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.embeddium.EmbeddiumCompat;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.embeddium.EmbeddiumParticleVertexWriter;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.sodium.SodiumCompat;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.sodium.SodiumParticleVertexWriter;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.util.math.MathUtil;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.*;

import java.lang.Math;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class SnowStormParticle extends TextureSheetParticle {

    private final ParticleInstance particleData;
    private final ParticleEffectDefinition definition;
    private final ParticleEmitterInstance emitter;

    // billboard 朝向模式
    private final ParticleAppearanceBillboard.FaceCameraMode faceCameraMode;

    // 碰撞参数
    @Nullable
    private final ParticleMotionCollision collisionComponent;
    private boolean hasCollision;
    private final float collisionDrag;
    private final float coefficientOfRestitution;
    private final boolean expireOnContact;

    // 光照
    private final boolean environmentLighting;

    // 方块过期组件
    @Nullable
    private final ParticleExpireIfInBlocks expireIfInBlocks;
    @Nullable
    private final ParticleExpireIfNotInBlocks expireIfNotInBlocks;

    // 自定义 RenderType（按纹理+材质缓存）
    private final ParticleRenderType renderType;

    public SnowStormParticle(ClientLevel level, ParticleInstance particleData,
                             ParticleEffectDefinition definition,
                             ParticleEmitterInstance emitter) {
        super(level, particleData.x, particleData.y, particleData.z);
        this.particleData = particleData;
        this.definition = definition;
        this.emitter = emitter;

        // 初始速度（blocks/tick）
        this.xd = particleData.vx / 20f;
        this.yd = particleData.vy / 20f;
        this.zd = particleData.vz / 20f;

        // 颜色
        this.rCol = particleData.r;
        this.gCol = particleData.g;
        this.bCol = particleData.b;
        this.alpha = particleData.a;

        // 生命周期（tick 为单位）
        this.lifetime = (int) (particleData.maxLifetime * 20);
        this.age = 0;

        // 不使用原版的重力和摩擦
        this.gravity = 0;
        this.friction = 1.0f;

        // billboard 朝向模式
        ParticleAppearanceBillboard billboard = definition.findComponent(ParticleAppearanceBillboard.class);
        this.faceCameraMode = billboard != null
                ? billboard.faceCameraMode()
                : ParticleAppearanceBillboard.FaceCameraMode.ROTATE_XYZ;

        // 碰撞
        ParticleMotionCollision collision = definition.findComponent(ParticleMotionCollision.class);
        this.collisionComponent = collision;
        if (collision != null) {
            // enabled 初始值：如果有 enabled 表达式则求值，否则默认启用
            if (collision.enabled() != null) {
                this.hasCollision = collision.enabled().evaluate(emitter.getMolang().getContext()) != 0;
            } else {
                this.hasCollision = true;
            }
            this.collisionDrag = collision.collisionDrag();
            this.coefficientOfRestitution = collision.coefficientOfRestitution();
            this.expireOnContact = collision.expireOnContact();
            this.hasPhysics = true;
            float radius = collision.collisionRadius();
            this.setBoundingBox(this.getBoundingBox().inflate(radius));
        } else {
            this.hasCollision = false;
            this.collisionDrag = 0;
            this.coefficientOfRestitution = 1;
            this.expireOnContact = false;
        }

        // 光照：组件存在时使用世界光照，否则全亮
        this.environmentLighting = definition.findComponent(ParticleAppearanceLighting.class) != null;

        // 方块过期组件
        this.expireIfInBlocks = definition.findComponent(ParticleExpireIfInBlocks.class);
        this.expireIfNotInBlocks = definition.findComponent(ParticleExpireIfNotInBlocks.class);

        // RenderType
        ParticleDescription desc = definition.getDescription();
        this.renderType = MolangWorldParticleRenderType.get(desc.getMaterial(), desc.getTexture());
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.oRoll = this.roll;

        float dt = 1f / 20f;

        // 每帧更新 collision.enabled
        if (collisionComponent != null && collisionComponent.enabled() != null) {
            this.hasCollision = collisionComponent.enabled().evaluate(emitter.getMolang().getContext()) != 0;
        }

        float savedX = particleData.x, savedY = particleData.y, savedZ = particleData.z;

        emitter.updateSingleParticle(particleData, dt);

        particleData.x = savedX;
        particleData.y = savedY;
        particleData.z = savedZ;

        this.xd = particleData.vx / 20f;
        this.yd = particleData.vy / 20f;
        this.zd = particleData.vz / 20f;

        this.move(this.xd, this.yd, this.zd);

        particleData.vx = (float) this.xd * 20f;
        particleData.vy = (float) this.yd * 20f;
        particleData.vz = (float) this.zd * 20f;

        particleData.x = (float) this.x;
        particleData.y = (float) this.y;
        particleData.z = (float) this.z;

        this.roll = (float) Math.toRadians(particleData.rotation);

        this.rCol = particleData.r;
        this.gCol = particleData.g;
        this.bCol = particleData.b;
        this.alpha = particleData.a;

        // 方块过期检测
        if (particleData.alive) {
            checkBlockExpiration();
        }

        if (!particleData.alive) {
            emitter.fireParticleExpirationEvents(particleData);
            this.remove();
        }

        this.age++;
    }

    @Override
    public void move(double x, double y, double z) {
        if (!hasCollision) {
            if (x != 0.0 || y != 0.0 || z != 0.0) {
                this.setBoundingBox(this.getBoundingBox().move(x, y, z));
                this.setLocationFromBoundingbox();
            }
            return;
        }

        double origX = x, origY = y, origZ = z;

        // 速度上限
        double velSqr = x * x + y * y + z * z;
        if (this.hasPhysics && (x != 0.0 || y != 0.0 || z != 0.0) && velSqr < 10000.0) {
            Vec3 collided = Entity.collideBoundingBox(null, new Vec3(x, y, z), this.getBoundingBox(), this.level, List.of());

            if (x != collided.x) {
                this.xd = -Mth.sign(xd) * (Math.abs(xd) - collisionDrag / 20f) * coefficientOfRestitution;
            }
            if (y != collided.y) {
                this.yd *= -coefficientOfRestitution;
            }
            if (z != collided.z) {
                this.zd = -Mth.sign(zd) * (Math.abs(zd) - collisionDrag / 20f) * coefficientOfRestitution;
            }

            x = collided.x;
            y = collided.y;
            z = collided.z;
        }

        if (x != 0.0 || y != 0.0 || z != 0.0) {
            this.setBoundingBox(this.getBoundingBox().move(x, y, z));
            this.setLocationFromBoundingbox();
        }

        // 检测碰撞发生
        boolean collided = origX != x || origY != y || origZ != z;
        if (collided) {
            this.onGround = origY != y && origY < 0.0;

            // 触发碰撞事件
            float speed = (float) Math.sqrt(
                    particleData.vx * particleData.vx +
                    particleData.vy * particleData.vy +
                    particleData.vz * particleData.vz
            );
            emitter.fireCollisionEvents(particleData, speed);

            if (expireOnContact) {
                this.remove();
            }
        }
    }

    private static final Quaternionf QUATERNION = new Quaternionf();
    private static final Vector3f TEMP_VEC = new Vector3f();
    private static final Vector3f TEMP_VEC2 = new Vector3f();
    private static final Vector3f TEMP_VEC3 = new Vector3f();
    private static final Vector3f AXIS_X = new Vector3f();
    private static final Vector3f AXIS_Y = new Vector3f();
    private static final Vector4f TEMP_VEC4 = new Vector4f();
    private static final Matrix4f TEMP_MAT = new Matrix4f();
    private static final Matrix3f TEMP_MAT3 = new Matrix3f();

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 camPos = camera.getPosition();
        float cx = (float) (Mth.lerp(partialTicks, this.xo, this.x) - camPos.x());
        float cy = (float) (Mth.lerp(partialTicks, this.yo, this.y) - camPos.y());
        float cz = (float) (Mth.lerp(partialTicks, this.zo, this.z) - camPos.z());

        // 构建朝向四元数
        QUATERNION.identity();
        applyFacingMode(QUATERNION, camera);

        // 应用粒子自旋
        float currentRoll = Mth.lerp(partialTicks, this.oRoll, this.roll);
        if (currentRoll != 0) {
            QUATERNION.rotateZ(currentRoll);
        }

        // 粒子尺寸
        float hw = particleData.width * particleData.spawnScale;
        float hh = particleData.height * particleData.spawnScale;

        // UV
        float u0 = particleData.u0;
        float v0 = particleData.v0;
        float u1 = particleData.u1;
        float v1 = particleData.v1;

        int light = getLightColor(partialTicks);

        AXIS_X.set(1, 0, 0).rotate(QUATERNION).mul(hw);
        AXIS_Y.set(0, 1, 0).rotate(QUATERNION).mul(hh);

        float ax = AXIS_X.x(), ay = AXIS_X.y(), az = AXIS_X.z();
        float bx = AXIS_Y.x(), by = AXIS_Y.y(), bz = AXIS_Y.z();

        float x0 = cx - ax - bx;
        float y0 = cy - ay - by;
        float z0 = cz - az - bz;
        float x1 = cx - ax + bx;
        float y1 = cy - ay + by;
        float z1 = cz - az + bz;
        float x2 = cx + ax + bx;
        float y2 = cy + ay + by;
        float z2 = cz + az + bz;
        float x3 = cx + ax - bx;
        float y3 = cy + ay - by;
        float z3 = cz + az - bz;

        if (SodiumCompat.isSodiumInstalled() && SodiumParticleVertexWriter.tryRender(
                buffer,
                x0, y0, z0, u0, v1,
                x1, y1, z1, u0, v0,
                x2, y2, z2, u1, v0,
                x3, y3, z3, u1, v1,
                rCol, gCol, bCol, alpha, light)) {
            return;
        }
        if (EmbeddiumCompat.isEmbeddiumInstalled() && EmbeddiumParticleVertexWriter.tryRender(
                buffer,
                x0, y0, z0, u0, v1,
                x1, y1, z1, u0, v0,
                x2, y2, z2, u1, v0,
                x3, y3, z3, u1, v1,
                rCol, gCol, bCol, alpha, light)) {
            return;
        }

        renderVertex(buffer, x0, y0, z0, u0, v1, light);
        renderVertex(buffer, x1, y1, z1, u0, v0, light);
        renderVertex(buffer, x2, y2, z2, u1, v0, light);
        renderVertex(buffer, x3, y3, z3, u1, v1, light);
    }

    private void renderVertex(VertexConsumer buffer, float x, float y, float z, float u, float v, int light) {
        buffer.addVertex(x, y, z)
                .setUv(u, v)
                .setColor(rCol, gCol, bCol, alpha)
                .setLight(light);
    }

    // 应用朝向模式
    private void applyFacingMode(Quaternionf q, Camera camera) {
        switch (faceCameraMode) {
            case ROTATE_XYZ -> q.set(camera.rotation());
            case ROTATE_Y -> {
                Quaternionf camRot = camera.rotation();
                q.set(0, camRot.y, 0, camRot.w).normalize();
            }
            case LOOKAT_XYZ -> {
                // 真正的 lookat：从粒子位置看向摄像机
                Vec3 camPos = camera.getPosition();
                TEMP_VEC.set(
                        (float) (camPos.x - this.x),
                        (float) (camPos.y - this.y),
                        (float) (camPos.z - this.z)
                ).normalize(); // toCamera
                TEMP_VEC2.set(0, 1, 0); // up
                TEMP_VEC2.cross(TEMP_VEC, TEMP_VEC3); // right = up × toCamera
                TEMP_VEC3.normalize();
                TEMP_VEC.cross(TEMP_VEC3, TEMP_VEC2); // correctedUp = toCamera × right
                q.setFromNormalized(TEMP_MAT3.set(
                        TEMP_VEC3.x, TEMP_VEC2.x, TEMP_VEC.x,
                        TEMP_VEC3.y, TEMP_VEC2.y, TEMP_VEC.y,
                        TEMP_VEC3.z, TEMP_VEC2.z, TEMP_VEC.z
                ).invert());
            }
            case LOOKAT_Y -> {
                // lookat 但只保留 Y 轴旋转分量
                Vec3 camPos = camera.getPosition();
                TEMP_VEC.set(
                        (float) (camPos.x - this.x),
                        (float) (camPos.y - this.y),
                        (float) (camPos.z - this.z)
                ).normalize(); // toCamera
                TEMP_VEC2.set(0, 1, 0); // up
                TEMP_VEC2.cross(TEMP_VEC, TEMP_VEC3); // right = up × toCamera
                TEMP_VEC3.normalize();
                TEMP_VEC.cross(TEMP_VEC3, TEMP_VEC2); // correctedUp = toCamera × right
                q.setFromNormalized(TEMP_MAT3.set(
                        TEMP_VEC3.x, TEMP_VEC2.x, TEMP_VEC.x,
                        TEMP_VEC3.y, TEMP_VEC2.y, TEMP_VEC.y,
                        TEMP_VEC3.z, TEMP_VEC2.z, TEMP_VEC.z
                ).invert());
                q.x = 0;
                q.z = 0;
                q.normalize();
            }
            case LOOKAT_DIRECTION -> {
                // X 轴沿速度方向（facingDirection），然后绕 X 轴旋转使面片尽量朝向摄像机
                TEMP_VEC.set(particleData.vx, particleData.vy, particleData.vz);
                float len = TEMP_VEC.length();
                if (len > 0.0001f) {
                    TEMP_VEC.normalize();
                    // 从 X 轴 (1,0,0) 旋转到速度方向
                    TEMP_VEC2.set(1, 0, 0);
                    MathUtil.setFromUnitVectors(TEMP_VEC2, TEMP_VEC, q);
                    // 把摄像机方向变换到粒子局部空间，计算绕 X 轴的旋转使面片朝向摄像机
                    Vec3 camPos = camera.getPosition();
                    TEMP_VEC4.set(
                            (float) (camPos.x - this.x),
                            (float) (camPos.y - this.y),
                            (float) (camPos.z - this.z),
                            0
                    ).mul(TEMP_MAT.rotation(q).invert());
                    q.rotateX((float) Mth.atan2(-TEMP_VEC4.y, TEMP_VEC4.z));
                } else {
                    q.set(camera.rotation());
                }
            }
            case DIRECTION_X -> q.rotationXYZ(0, Mth.HALF_PI, 0);
            case DIRECTION_Y -> q.rotationXYZ(Mth.HALF_PI, Mth.PI, 0);
            case DIRECTION_Z -> q.identity();
            default -> q.set(camera.rotation());
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return renderType;
    }

    @Override
    protected float getU0() {
        return particleData.u0;
    }

    @Override
    protected float getU1() {
        return particleData.u1;
    }

    @Override
    protected float getV0() {
        return particleData.v0;
    }

    @Override
    protected float getV1() {
        return particleData.v1;
    }

    /**
     * 获取关联的 ParticleInstance 数据。
     */
    public ParticleInstance getParticleData() {
        return particleData;
    }

    /**
     * 获取关联的发射器实例。
     */
    public ParticleEmitterInstance getEmitter() {
        return emitter;
    }

    @Override
    public int getLightColor(float partialTick) {
        return environmentLighting ? super.getLightColor(partialTick) : LightTexture.FULL_BRIGHT;
    }

    /**
     * 检查粒子是否因所在方块而过期。
     */
    private void checkBlockExpiration() {
        if (expireIfInBlocks == null && expireIfNotInBlocks == null) return;

        BlockPos pos = BlockPos.containing(this.x, this.y, this.z);
        String blockId = BuiltInRegistries.BLOCK.getKey(this.level.getBlockState(pos).getBlock()).toString();

        if (expireIfInBlocks != null && expireIfInBlocks.blocks().contains(blockId)) {
            particleData.alive = false;
        }
        if (expireIfNotInBlocks != null && !expireIfNotInBlocks.blocks().contains(blockId)) {
            particleData.alive = false;
        }
    }
}
