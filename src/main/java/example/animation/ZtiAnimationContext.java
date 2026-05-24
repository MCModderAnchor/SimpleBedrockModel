package example.animation;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClock;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation.BedrockAnimation;
import com.github.mcmodderanchor.simplebedrockmodel.v1.event.RegisterBedrockAnimationReloadListenerEvent;
import com.maydaymemory.mae.basic.DummyPose;
import com.maydaymemory.mae.basic.Pose;
import com.maydaymemory.mae.control.Tickable;
import com.maydaymemory.mae.control.runner.AnimationContext;
import com.maydaymemory.mae.control.runner.AnimationRunner;
import com.maydaymemory.mae.control.runner.LoopingState;
import com.maydaymemory.mae.control.runner.PlayingState;
import com.maydaymemory.mae.control.runner.StopState;
import example.client.render.entity.ZtiRenderer;
import example.entity.Zti;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ZtiAnimationContext implements Tickable {
    private static BedrockAnimation IDLE;
    private static BedrockAnimation RUNNING;
    private static final List<BedrockAnimation> ATTACKS = new ArrayList<>();

    @SubscribeEvent
    public static void onAnimationReloadListenerRegister(RegisterBedrockAnimationReloadListenerEvent event) {
        event.register(map -> {
            List<BedrockAnimation> animations = map.get(ZtiRenderer.ANIMATION);
            if (animations == null) {
                throw new IllegalStateException("Missing zti animations: " + ZtiRenderer.ANIMATION);
            }
            IDLE = animations.stream().filter(animation -> animation.getName().equals("idle")).findFirst().orElseThrow();
            RUNNING = animations.stream().filter(animation -> animation.getName().equals("running")).findFirst().orElseThrow();
            ATTACKS.clear();
            ATTACKS.addAll(animations.stream()
                    .filter(animation -> animation.getName().startsWith("attack_"))
                    .sorted(Comparator.comparing(BedrockAnimation::getName))
                    .toList());
            if (ATTACKS.isEmpty()) {
                throw new IllegalStateException("Missing zti attack animations: " + ZtiRenderer.ANIMATION);
            }
        });
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
