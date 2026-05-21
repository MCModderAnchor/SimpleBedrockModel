package example.animation;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.BoneIndexProvider;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation.BedrockAnimation;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockAnimationFile;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClock;
import com.maydaymemory.mae.basic.DummyPose;
import com.maydaymemory.mae.basic.Pose;
import com.maydaymemory.mae.control.Tickable;
import com.maydaymemory.mae.control.runner.AnimationContext;
import com.maydaymemory.mae.control.runner.AnimationRunner;
import com.maydaymemory.mae.control.runner.LoopingState;
import com.maydaymemory.mae.control.runner.PlayingState;
import com.maydaymemory.mae.control.runner.StopState;
import example.entity.Zti;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ZtiAnimationContext implements Tickable {
    private static BedrockAnimation IDLE;
    private static BedrockAnimation RUNNING;
    private static final List<BedrockAnimation> ATTACKS = new ArrayList<>();

    /**
     * 使用 v2 模型初始化动画，骨骼索引与烘焙后的模型一致。
     * 替代原 v1 {@code RegisterBedrockAnimationReloadListenerEvent} 流程。
     */
    public static void initialize(BedrockAnimationFile animationFile, BoneIndexProvider indexProvider) {
        List<BedrockAnimation> animations = BedrockAnimation.createAnimation(animationFile, indexProvider);
        IDLE = animations.stream()
                .filter(a -> a.getName().equals("idle"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing zti idle animation"));
        RUNNING = animations.stream()
                .filter(a -> a.getName().equals("running"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing zti running animation"));
        ATTACKS.clear();
        ATTACKS.addAll(animations.stream()
                .filter(a -> a.getName().startsWith("attack_"))
                .sorted(Comparator.comparing(BedrockAnimation::getName))
                .toList());
        if (ATTACKS.isEmpty()) {
            throw new IllegalStateException("Missing zti attack animations");
        }
    }

    private final Zti entity;
    private final AnimationClock clock;
    private AnimationRunner runner;
    private boolean previousAttackActive;

    public ZtiAnimationContext(Zti entity, AnimationClock clock) {
        this.entity = entity;
        this.clock = clock;
    }

    public BedrockAnimation idleAnimation() {
        return IDLE;
    }

    public BedrockAnimation runningAnimation() {
        return RUNNING;
    }

    public BedrockAnimation randomAttackAnimation() {
        return ATTACKS.get(0);
    }

    public void playLooping(BedrockAnimation animation) {
        runner = new AnimationRunner(animation, new AnimationContext(animation.getSpecifiedEndTimeS()));
        runner.setState(new LoopingState(clock));
    }

    public void playOnce(BedrockAnimation animation) {
        runner = new AnimationRunner(animation, new AnimationContext(animation.getSpecifiedEndTimeS()));
        runner.setState(new PlayingState(clock, StopState::new));
    }

    public boolean isMoving() {
        return entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D;
    }

    public boolean consumeAttackTrigger() {
        boolean active = entity.isAttackAnimationActive();
        boolean triggered = active && !previousAttackActive;
        previousAttackActive = active;
        return triggered;
    }

    public boolean isCurrentAnimationFinished() {
        return runner == null || runner.getAnimationContext().isEnd();
    }

    public Pose evaluateCurrentPose() {
        return runner == null ? DummyPose.INSTANCE : runner.evaluate();
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
        }
    }
}
