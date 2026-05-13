package example.animation;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClock;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClocks;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation.BedrockAnimation;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.ParticleEffectData;
import com.github.mcmodderanchor.simplebedrockmodel.v1.event.RegisterBedrockAnimationReloadListenerEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.event.RegisterBedrockModelReloadListenerEvent;
import com.maydaymemory.mae.basic.ArrayClipChannel;
import com.maydaymemory.mae.basic.ArrayPoseBuilder;
import com.maydaymemory.mae.basic.Keyframe;
import com.maydaymemory.mae.basic.Pose;
import com.maydaymemory.mae.basic.ZYXBoneTransformFactory;
import com.maydaymemory.mae.blend.EulerAdditiveBlender;
import com.maydaymemory.mae.blend.SkeletonDescendantAccessorAdapter;
import com.maydaymemory.mae.blend.LayerBlend;
import com.maydaymemory.mae.blend.SimpleEulerAdditiveBlender;
import com.maydaymemory.mae.blend.SkeletonBaseLayerBlend;
import com.maydaymemory.mae.control.montage.AnimationMontage;
import com.maydaymemory.mae.control.montage.AnimationMontageRunner;
import com.maydaymemory.mae.control.montage.AnimationMontageSection;
import com.maydaymemory.mae.control.montage.AnimationMontageTrack;
import com.maydaymemory.mae.control.montage.AnimationNotifyKeyframe;
import com.maydaymemory.mae.control.montage.AnimationSegment;
import com.maydaymemory.mae.control.montage.AnimationSegmentKeyframe;
import com.maydaymemory.mae.control.montage.IAnimationNotify;
import example.resource.KnownResources;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLLoader;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@EventBusSubscriber
public class DeagleAnimationGraph implements GunAnimationGraph {
    private static final EulerAdditiveBlender EULER_ADDITIVE_BLENDER = new SimpleEulerAdditiveBlender(new ZYXBoneTransformFactory(), ArrayPoseBuilder::new);

    private static Map<String, BedrockAnimation> animations;
    private static BedrockModel model;

    @SubscribeEvent
    public static void onRegisterModelReloadListener(RegisterBedrockModelReloadListenerEvent event) {
        event.register(map -> model = map.get(KnownResources.DEAGLE));
    }

    @SubscribeEvent
    public static void onRegisterAnimationReloadListener(RegisterBedrockAnimationReloadListenerEvent event) {
        event.register(map -> {
            animations = new HashMap<>();
            List<BedrockAnimation> bedrockAnimations = Objects.requireNonNull(map.get(KnownResources.DEAGLE));
            for (BedrockAnimation animation : bedrockAnimations) {
                animations.put(animation.getName(), animation);
            }
        });
    }

    private final FPGunAnimationInstance animationInstance;
    private final AnimationClock clock;
    private final Deque<AnimationMontageRunner<FPGunAnimationInstance>> shootMontageRunners = new LinkedList<>();
    private final List<ParticleEffectData> pendingParticles = new ArrayList<>();

    private AnimationMontage<FPGunAnimationInstance> shootMontage;
    private AnimationMontageRunner<FPGunAnimationInstance> drawMontageRunner;
    private SkeletonBaseLayerBlend handAndRootLayer;
    private LayerBlend noHandLayer;

    public DeagleAnimationGraph(FPGunAnimationInstance animationInstance) {
        this.animationInstance = animationInstance;
        this.clock = AnimationClocks.client();
        if (FMLLoader.getDist() == Dist.CLIENT) {
            handAndRootLayer = new SkeletonBaseLayerBlend(new SkeletonDescendantAccessorAdapter(model));
            handAndRootLayer.addControlPoint(model.getIndex("root"), 0, 1f);
            handAndRootLayer.addControlPoint(model.getIndex("lefthand"), 1, 1f);
            handAndRootLayer.addControlPoint(model.getIndex("righthand"), 1, 1f);
            noHandLayer = new LayerBlend() {
                final int leftHandIndex = model.getIndex("lefthand");
                final int rightHandIndex = model.getIndex("righthand");

                @Override
                public float getWeight(int boneIndex) {
                    if (leftHandIndex == boneIndex || rightHandIndex == boneIndex) {
                        return 0;
                    }
                    return 1;
                }
            };
        }
        // TODO 感觉还是需要抽空写一个图形化工具，用代码构建蒙太奇又臭又长...
        // 构建 draw 蒙太奇，包含 draw 动画和 idle 动画。播放一遍 draw 后会自动循环 idle
        initializeDrawMontage();
        // 构建 shoot 蒙太奇，只包含 shoot 动画，有一个 notify 通知开火
        initializeShootMontage();
    }

    private void initializeDrawMontage() {
        AnimationMontage<FPGunAnimationInstance> drawMontage = new AnimationMontage<>();

        ArrayList<Keyframe<AnimationSegment>> drawSegments = new ArrayList<>();
        drawSegments.add(constructSegmentKeyframe("draw", 0.0f, 0.0f, 0.63f));
        drawSegments.add(constructSegmentKeyframe("idle", 0.63f, 0.0f, 0.2f));
        AnimationMontageTrack drawTrack = new AnimationMontageTrack(drawSegments);
        drawTrack.setLayer(handAndRootLayer);
        drawMontage.setTracks(List.of(drawTrack));

        Map<String, AnimationMontageSection> drawMontageSections = new HashMap<>();
        drawMontageSections.put("draw", new AnimationMontageSection("draw", 0.0f, 0.63f, "idle"));
        drawMontageSections.put("idle", new AnimationMontageSection("idle", 0.63f, 0.83f, "idle"));
        drawMontage.setSections(drawMontageSections);

        ArrayList<Keyframe<IAnimationNotify<FPGunAnimationInstance>>> drawNotifies = new ArrayList<>();
        drawNotifies.add(new AnimationNotifyKeyframe<>(0.3f, ctx -> ctx.setRaisingGun(false)));
        drawMontage.setNotifyChannels(List.of(new ArrayClipChannel<>(drawNotifies)));

        drawMontageRunner = new AnimationMontageRunner<>(drawMontage, animationInstance, new ZYXBoneTransformFactory(), ArrayPoseBuilder::new, clock);
    }

    private void initializeShootMontage() {
        shootMontage = new AnimationMontage<>();

        ArrayList<Keyframe<AnimationSegment>> shotSegments = new ArrayList<>();
        shotSegments.add(constructSegmentKeyframe("shoot", 0.0f, 0.0f, 0.57f));
        AnimationMontageTrack shootTrack = new AnimationMontageTrack(shotSegments);
        shootTrack.setAdditive(true);
        shootTrack.setLayer(noHandLayer);
        shootMontage.setTracks(List.of(shootTrack));

        Map<String, AnimationMontageSection> shootMontageSections = new HashMap<>();
        shootMontageSections.put("shoot", new AnimationMontageSection("shoot", 0.0f, 0.57f, null));
        shootMontage.setSections(shootMontageSections);

        ArrayList<Keyframe<IAnimationNotify<FPGunAnimationInstance>>> coolingNotifies = new ArrayList<>();
        coolingNotifies.add(new AnimationNotifyKeyframe<>(0.26f, ctx -> ctx.setCooling(false)));
        ArrayList<Keyframe<IAnimationNotify<FPGunAnimationInstance>>> consumeAmmoNotifies = new ArrayList<>();
        consumeAmmoNotifies.add(new AnimationNotifyKeyframe<>(0.0f, ctx -> {
            int ammoInMag = ctx.getAmmoInMag();
            if (ctx.hasMagInstalled() && ammoInMag > 0) {
                ctx.setAmmoInMag(ammoInMag - 1);
            } else {
                ctx.setAmmoInGun(0);
            }
            // TODO 这里可以放发射子弹的逻辑
        }));
        shootMontage.setNotifyChannels(List.of(new ArrayClipChannel<>(coolingNotifies), new ArrayClipChannel<>(consumeAmmoNotifies)));
    }

    private AnimationSegmentKeyframe constructSegmentKeyframe(String animationName, float keyframeTime, float startTimeS, float endTimeS) {
        return new AnimationSegmentKeyframe(keyframeTime, new AnimationSegment(animations.get(animationName), startTimeS, endTimeS));
    }

    @Override
    public void notifyDraw() {
        // 重置各个属性
        animationInstance.setCooling(false);
        animationInstance.setRaisingGun(true);
        animationInstance.setAmmoInGun(1);
        animationInstance.setAmmoInMag(3);
        animationInstance.setMagInstalled(true);
        // 播放抬枪动画
        drawMontageRunner.stop();
        drawMontageRunner.start("draw");
    }

    @Override
    public void notifyTrigger() {
        if (animationInstance.isCooling() || animationInstance.isRaisingGun()) {
            return;
        }
        // 设置正在冷却。动画中的 notify 会在合适时机将冷却设置为 false
        animationInstance.setCooling(true);
        // 播放射击动画（粒子触发由动画通道的 particle_effects 关键帧驱动）
        AnimationMontageRunner<FPGunAnimationInstance> shootRunner = new AnimationMontageRunner<>(shootMontage, animationInstance, new ZYXBoneTransformFactory(), ArrayPoseBuilder::new, clock);
        shootRunner.start("shoot");
        shootMontageRunners.push(shootRunner);
    }

    @Override
    public void tick() {
        if (!clock.shouldTick()) {
            return;
        }
        drawMontageRunner.tick();
        consumeSounds(drawMontageRunner.clip(BedrockAnimation.SOUND_CHANNEL_NAME));
        // 弹掉已经播放完的 shoot montage runner
        while (!shootMontageRunners.isEmpty()) {
            AnimationMontageRunner<FPGunAnimationInstance> shootRunner = shootMontageRunners.peek();
            if (!shootRunner.isPlaying()) {
                shootMontageRunners.poll();
            } else {
                break;
            }
        }
        // tick shoot montage runner
        for (AnimationMontageRunner<FPGunAnimationInstance> shootRunner : shootMontageRunners) {
            shootRunner.tick();
            consumeSounds(shootRunner.clip(BedrockAnimation.SOUND_CHANNEL_NAME));
            consumeParticleKeyframes(shootRunner.clip(BedrockAnimation.PARTICLE_CHANNEL_NAME));
        }
    }

    @Override
    public Pose getPose() {
        // draw montage 包含 draw 和 idle，它输出的 Pose 作为混合基底
        Pose animationPose = drawMontageRunner.getPose();
        // 将 shoot montage 输出的 Pose 逐个混合（shoot 动画对应的轨道是 additive 轨道）
        for (AnimationMontageRunner<FPGunAnimationInstance> shootRunner : shootMontageRunners) {
            if (shootRunner.isPlaying()) {
                shootRunner.getBasePoseSlot().setDefaultValue(animationPose);
                animationPose = shootRunner.getPose();
            }
        }
        return EULER_ADDITIVE_BLENDER.blend(model.getBindPose(), animationPose);
    }

    private void consumeSounds(Iterable<Keyframe<ResourceLocation>> sounds) {
        Player player = animationInstance.getPlayer();
        Level level = player.level();
        for (Keyframe<ResourceLocation> keyframe : sounds) {
            SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(keyframe.getValue());
            level.playSound(player, player, soundEvent, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    private void consumeParticleKeyframes(Iterable<? extends Keyframe<?>> particles) {
        for (Keyframe<?> keyframe : particles) {
            Object value = keyframe.getValue();
            if (value instanceof ParticleEffectData data) {
                pendingParticles.add(data);
            }
        }
    }

    /**
     * 获取并清空待发射的粒子效果列表。
     */
    public List<ParticleEffectData> consumePendingParticles() {
        if (pendingParticles.isEmpty()) {
            return List.of();
        }
        List<ParticleEffectData> result = new ArrayList<>(pendingParticles);
        pendingParticles.clear();
        return result;
    }
}
