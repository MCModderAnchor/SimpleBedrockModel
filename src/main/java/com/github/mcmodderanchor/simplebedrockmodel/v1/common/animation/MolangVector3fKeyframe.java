package com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangContext;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.maydaymemory.mae.basic.BaseKeyframe;
import com.maydaymemory.mae.basic.InterpolatableKeyframe;
import com.maydaymemory.mae.basic.Interpolator;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * 支持 Molang 表达式的 Vector3f 关键帧。
 * 每次获取 pre/post 值时，会实时求值 Molang 表达式。
 * 通过 {@link MolangContext#getCurrent()} 从 ThreadLocal 获取当前上下文。
 */
public class MolangVector3fKeyframe extends BaseKeyframe<Vector3fc> implements InterpolatableKeyframe<Vector3fc> {
    private final MolangExpression[] preFunctions;
    private final MolangExpression[] postFunctions;
    private final Interpolator<Vector3fc> interpolator;

    /**
     * @param timeS         关键帧时间（秒）
     * @param preFunctions  pre 值的 3 个 Molang 表达式 (x, y, z)
     * @param postFunctions post 值的 3 个 Molang 表达式 (x, y, z)
     * @param interpolator  插值器
     */
    public MolangVector3fKeyframe(float timeS,
                                  MolangExpression[] preFunctions,
                                  MolangExpression[] postFunctions,
                                  Interpolator<Vector3fc> interpolator) {
        super(timeS);
        this.preFunctions = preFunctions;
        this.postFunctions = postFunctions;
        this.interpolator = interpolator;
    }

    @Override
    public Vector3fc getPre() {
        return evaluate(preFunctions);
    }

    @Override
    public Vector3fc getPost() {
        return evaluate(postFunctions);
    }

    @Override
    public Interpolator<Vector3fc> getInterpolator() {
        return interpolator;
    }

    @Override
    public Vector3fc getValue() {
        return getPre();
    }

    private static Vector3fc evaluate(MolangExpression[] functions) {
        MolangContext<?> ctx = MolangContext.getCurrent();
        return new Vector3f(
                (float) functions[0].evaluate(ctx),
                (float) functions[1].evaluate(ctx),
                (float) functions[2].evaluate(ctx)
        );
    }
}
