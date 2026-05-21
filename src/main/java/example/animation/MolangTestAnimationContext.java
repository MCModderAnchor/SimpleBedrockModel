package example.animation;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClock;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClocks;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation.BedrockAnimation;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.molang.MolangEngineHelper;
import com.github.mcmodderanchor.simplebedrockmodel.v1.event.RegisterBedrockAnimationReloadListenerEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.MochaEngine;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangContext;
import com.maydaymemory.mae.basic.Pose;
import com.maydaymemory.mae.control.runner.AnimationContext;
import com.maydaymemory.mae.control.runner.AnimationRunner;
import com.maydaymemory.mae.control.runner.LoopingState;
import example.resource.KnownResources;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Molang 动画测试用上下文。
 */
@EventBusSubscriber
public class MolangTestAnimationContext {
    private static final MolangContext<Object> SHARED_CONTEXT = new MolangContext<>();
    private static final MochaEngine<?> SHARED_ENGINE = MolangEngineHelper.createEngine(SHARED_CONTEXT);
    private static final AnimationClock CLOCK = AnimationClocks.client();

    private static BedrockAnimation molangTestAnimation;
    @Nullable
    private static AnimationRunner runner;

    @Nullable
    public static BedrockAnimation getAnimation() {
        return molangTestAnimation;
    }

    public static MochaEngine<?> getSharedEngine() {
        return SHARED_ENGINE;
    }

    public static MolangContext<Object> getSharedContext() {
        return SHARED_CONTEXT;
    }

    @SubscribeEvent
    public static void onAnimationReloadListenerRegister(RegisterBedrockAnimationReloadListenerEvent event) {
        event.register(map -> {
            List<BedrockAnimation> animations = map.get(KnownResources.MOLANG_TEST);
            if (animations != null && !animations.isEmpty()) {
                molangTestAnimation = animations.stream()
                        .filter(a -> a.getName().equals("molang_test"))
                        .findFirst()
                        .orElse(null);
                if (molangTestAnimation != null) {
                    AnimationContext ctx = new AnimationContext(molangTestAnimation.getSpecifiedEndTimeS());
                    ctx.setState(new LoopingState(CLOCK));
                    runner = new AnimationRunner(molangTestAnimation, ctx);
                }
            }
        });
    }

    public static void tick() {
        if (!CLOCK.shouldTick()) {
            return;
        }
        if (runner != null) {
            runner.tick();
        }
    }

    @Nullable
    public static Pose evaluatePose() {
        if (runner != null) {
            // 每次求值时传入 context，不持有长期引用
            return runner.evaluate(SHARED_CONTEXT);
        }
        return null;
    }

    @Nullable
    public static AnimationRunner getRunner() {
        return runner;
    }
}
