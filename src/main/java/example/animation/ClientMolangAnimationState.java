package example.animation;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation.AnimationRateLimiter;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation.BedrockAnimation;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClock;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangContext;
import com.maydaymemory.mae.basic.Pose;
import com.maydaymemory.mae.control.runner.AnimationContext;
import com.maydaymemory.mae.control.runner.AnimationRunner;
import com.maydaymemory.mae.control.runner.LoopingState;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;


@OnlyIn(Dist.CLIENT)
public class ClientMolangAnimationState {
    private final AnimationRunner runner;
    private final AnimationRateLimiter<Pose> poseRateLimiter;


    public ClientMolangAnimationState(BedrockAnimation animation, AnimationClock clock, long throttleIntervalNanos) {
        AnimationContext ctx = new AnimationContext(animation.getSpecifiedEndTimeS());
        ctx.setState(new LoopingState(clock));
        this.runner = new AnimationRunner(animation, ctx);
        this.poseRateLimiter = new AnimationRateLimiter<>(clock, throttleIntervalNanos);
    }

    @Nullable
    public Pose getOrEvaluatePose(MolangContext<Object> sharedContext) {
        return poseRateLimiter.update(() -> {
            runner.tick();
            return runner.evaluate(sharedContext);
        });
    }
}
