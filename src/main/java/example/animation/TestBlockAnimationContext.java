package example.animation;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.BoneIndexProvider;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation.BedrockAnimation;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockAnimationFile;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClock;
import com.maydaymemory.mae.basic.ArrayPoseBuilder;
import com.maydaymemory.mae.basic.Keyframe;
import com.maydaymemory.mae.basic.Pose;
import com.maydaymemory.mae.basic.ZYXBoneTransformFactory;
import com.maydaymemory.mae.blend.CubicHermiteInterpolatorBlender;
import com.maydaymemory.mae.control.Tickable;
import com.maydaymemory.mae.control.misc.AnimationVelocityEstimatorNode;
import com.maydaymemory.mae.control.misc.RealtimeVelocityEstimatorNode;
import com.maydaymemory.mae.control.runner.AnimationRunner;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

public class TestBlockAnimationContext implements Tickable {
    public static final CubicHermiteInterpolatorBlender blender = new CubicHermiteInterpolatorBlender(new ZYXBoneTransformFactory(), ArrayPoseBuilder::new);

    private static final String[] ANIMATIONS = new String[]{
            "走路", "跑步"
//            "治疗魔法", "待机初始帧", "跑步", "抓取", "抓取 未命中", "跑步—>冲刺轻击",
//            "走路", "待机", "待机—>防空技能", "待机—>蹲击", "待机—>推击", "待机—>轻击",
//            "待机—>重击", "待机—>暗能量球", "待机—>格林爆破", "待机—>治疗魔法蓄力",
//            "治疗魔法蓄力ing", "治疗魔法蓄力—>被打断（待机）", "治疗魔法蓄力—>治疗魔法释放"
    };
    private static final BedrockAnimation[] ANIMATIONS_CACHE = new BedrockAnimation[ANIMATIONS.length];

    /**
     * 使用 v2 模型初始化动画，骨骼索引与烘焙后的模型一致。
     * 替代原 v1 {@code RegisterBedrockAnimationReloadListenerEvent} 流程。
     */
    public static void initialize(BedrockAnimationFile animationFile, BoneIndexProvider indexProvider) {
        List<BedrockAnimation> animations = BedrockAnimation.createAnimation(animationFile, indexProvider);
        for (int i = 0; i < ANIMATIONS.length; i++) {
            String animationName = ANIMATIONS[i];
            ANIMATIONS_CACHE[i] = animations.stream()
                    .filter(animation -> animation.getName().equals(animationName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Missing test animation: " + animationName));
        }
    }

    private final BlockEntity blockEntity;
    private final AnimationClock clock;

    private int currentAnimationIndex = 0;
    public boolean needTransition = false;

    public final RealtimeVelocityEstimatorNode velocityEstimatorNode;
    public final AnimationVelocityEstimatorNode targetVelocityEstimatorNode;

    private AnimationRunner runner;
    private Pose velocitySnapshot;

    public TestBlockAnimationContext(RealtimeVelocityEstimatorNode velocityEstimatorNode, BlockEntity blockEntity, AnimationClock clock) {
        this.velocityEstimatorNode = velocityEstimatorNode;
        this.targetVelocityEstimatorNode = new AnimationVelocityEstimatorNode(ArrayPoseBuilder::new);
        targetVelocityEstimatorNode.getAnimationSlot().connect(this::currentAnimation);
        targetVelocityEstimatorNode.getTimeSlot().connect(() -> getRunner().getProgressInSecond());
        this.blockEntity = blockEntity;
        this.clock = clock;
    }

    public BedrockAnimation nextAnimation() {
        currentAnimationIndex = (currentAnimationIndex + 1) % ANIMATIONS.length;
        return fromIndex(currentAnimationIndex);
    }

    public BedrockAnimation currentAnimation() {
        return fromIndex(currentAnimationIndex);
    }

    private BedrockAnimation fromIndex(int index) {
        return ANIMATIONS_CACHE[index];
    }

    public void snapshotVelocity() {
        if (velocityEstimatorNode != null) {
            velocitySnapshot = velocityEstimatorNode.getVelocityPose();
        }
    }

    public Pose getVelocitySnapshot() {
        return velocitySnapshot;
    }

    public AnimationRunner getRunner() {
        return runner;
    }

    public void setRunner(AnimationRunner runner) {
        this.runner = runner;
    }

    public AnimationClock getClock() {
        return clock;
    }

    @Override
    public void tick() {
        if (!clock.shouldTick()) {
            return;
        }
        if (runner != null) {
            runner.tick();
            Level level = blockEntity.getLevel();
            if (level != null && !level.isClientSide) {
                @SuppressWarnings("unchecked")
                Iterable<Keyframe<ResourceLocation>> sounds = runner.clip(BedrockAnimation.SOUND_CHANNEL_NAME);
                if (sounds != null) {
                    for (Keyframe<ResourceLocation> keyframe : sounds) {
                        BlockPos pos = blockEntity.getBlockPos();
                        SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(keyframe.getValue());
                        level.playSound(null, pos, soundEvent, SoundSource.BLOCKS);
                    }
                }
            }
        }
    }

    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("AnimationIndex", currentAnimationIndex);
        tag.putBoolean("NeedTransition", needTransition);
        return tag;
    }

    public void handleUpdateTag(CompoundTag tag) {
        currentAnimationIndex = tag.getInt("AnimationIndex");
        needTransition = tag.getBoolean("NeedTransition");
    }
}
