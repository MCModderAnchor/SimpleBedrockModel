package example.animation;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation.BedrockAnimation;
import com.maydaymemory.mae.basic.DummyPose;
import com.maydaymemory.mae.basic.Pose;
import com.maydaymemory.mae.control.blend.EasingBlendCurve;
import com.maydaymemory.mae.control.blend.IBlendCurve;
import com.maydaymemory.mae.control.runner.AnimationContext;
import com.maydaymemory.mae.control.runner.AnimationRunner;
import com.maydaymemory.mae.control.runner.LoopingState;
import com.maydaymemory.mae.control.statemachine.IAnimationState;
import com.maydaymemory.mae.control.statemachine.IAnimationTransition;
import com.maydaymemory.mae.control.statemachine.TransferOutStrategy;
import com.maydaymemory.mae.util.Easing;

import java.util.List;

public class TestBlockStateMachineState implements IAnimationState<TestBlockAnimationContext> {
    public static final TestBlockStateMachineState INSTANCE = new TestBlockStateMachineState();

    private TestBlockStateMachineState() {}

    @Override
    public Iterable<IAnimationTransition<TestBlockAnimationContext>> transitions() {
        return List.of(new Transition());
    }

    @Override
    public void onEnter(TestBlockAnimationContext testBlockAnimationContext, IAnimationState<TestBlockAnimationContext> iAnimationState) {
        AnimationRunner runner = testBlockAnimationContext.getRunner();
        if (runner != null) {
            runner.setState(new LoopingState(testBlockAnimationContext.getClock()));
        }
    }

    @Override
    public void onExit(TestBlockAnimationContext testBlockAnimationContext, IAnimationTransition<TestBlockAnimationContext> iAnimationTransition) {}

    @Override
    public void onUpdate(TestBlockAnimationContext testBlockAnimationContext) {}

    @Override
    public Pose evaluatePose(TestBlockAnimationContext testBlockAnimationContext) {
        AnimationRunner runner = testBlockAnimationContext.getRunner();
        if (runner == null) {
            return DummyPose.INSTANCE;
        }
        return runner.evaluate();
    }

    public static class Transition implements IAnimationTransition<TestBlockAnimationContext> {
        @Override
        public IAnimationState<TestBlockAnimationContext> targetState() {
            return TestBlockStateMachineState.INSTANCE;
        }

        @Override
        public IBlendCurve curve() {
            return new EasingBlendCurve(Easing.LINEAR);
        }

        @Override
        public float duration() {
            return 0.3f;
        }

        @Override
        public TransferOutStrategy transferOutStrategy() {
            return TransferOutStrategy.TO_STATE;
        }

        @Override
        public boolean canTrigger(TestBlockAnimationContext testBlockAnimationContext) {
            if (testBlockAnimationContext.needTransition) {
                testBlockAnimationContext.needTransition = false;
                return true;
            }
            return false;
        }

        @Override
        public void afterTrigger(TestBlockAnimationContext testBlockAnimationContext) {
            testBlockAnimationContext.snapshotVelocity();
            BedrockAnimation animation = testBlockAnimationContext.nextAnimation();
            AnimationRunner runner = new AnimationRunner(animation, new AnimationContext(animation.getSpecifiedEndTimeS()));
            runner.setState(new LoopingState(testBlockAnimationContext.getClock()));
            testBlockAnimationContext.setRunner(runner);
        }

        @Override
        public Pose getInterpolatedPose(TestBlockAnimationContext testBlockAnimationContext, Pose pose, Pose pose1, float v) {
            Pose targetVelocity = testBlockAnimationContext.targetVelocityEstimatorNode.getVelocityPose();
            float time = duration() * v;
            return TestBlockAnimationContext.blender.blend(pose, testBlockAnimationContext.getVelocitySnapshot(), pose1, targetVelocity, time, duration());
        }
    }
}
