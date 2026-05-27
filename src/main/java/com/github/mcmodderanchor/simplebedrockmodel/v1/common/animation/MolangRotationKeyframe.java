package com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangContext;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.maydaymemory.mae.basic.BaseKeyframe;
import com.maydaymemory.mae.basic.IEvaluationContext;
import com.maydaymemory.mae.basic.InterpolatableKeyframe;
import com.maydaymemory.mae.basic.Interpolator;
import com.maydaymemory.mae.basic.Rotation;
import org.joml.Vector3f;

/**
 * 支持 Molang 表达式的 Rotation 关键帧。
 * 每次获取 pre/post 值时，会实时求值 Molang 表达式并转换为弧度。
 * <p>
 * 必须通过 {@link IEvaluationContext} 参数传入上下文（{@link #getPre(IEvaluationContext)} /
 * {@link #getPost(IEvaluationContext)}）。无参版本返回零旋转。
 */
public class MolangRotationKeyframe extends BaseKeyframe<Rotation> implements InterpolatableKeyframe<Rotation> {
    private static final float DEGREE_TO_RADIAN = (float) (Math.PI / 180);
    private static final Rotation ZERO = new Rotation(new Vector3f());

    private final MolangExpression[] preFunctions;
    private final MolangExpression[] postFunctions;
    private final float mulX, mulY, mulZ;
    private final Interpolator<Rotation> interpolator;

    /**
     * @param timeS         关键帧时间（秒）
     * @param preFunctions  pre 值的 3 个 Molang 表达式 (x, y, z)，求值结果为角度
     * @param postFunctions post 值的 3 个 Molang 表达式 (x, y, z)，求值结果为角度
     * @param mulX          x 轴符号乘数（用于坐标系转换）
     * @param mulY          y 轴符号乘数
     * @param mulZ          z 轴符号乘数
     * @param interpolator  插值器
     */
    public MolangRotationKeyframe(float timeS,
                                  MolangExpression[] preFunctions,
                                  MolangExpression[] postFunctions,
                                  float mulX, float mulY, float mulZ,
                                  Interpolator<Rotation> interpolator) {
        super(timeS);
        this.preFunctions = preFunctions;
        this.postFunctions = postFunctions;
        this.mulX = mulX;
        this.mulY = mulY;
        this.mulZ = mulZ;
        this.interpolator = interpolator;
    }

    /**
     * @deprecated 无 Molang 上下文时无法求值，返回零旋转。请使用 {@link #getPre(IEvaluationContext)}。
     */
    @Override
    @Deprecated
    public Rotation getPre() {
        return ZERO;
    }

    /**
     * @deprecated 无 Molang 上下文时无法求值，返回零旋转。请使用 {@link #getPost(IEvaluationContext)}。
     */
    @Override
    @Deprecated
    public Rotation getPost() {
        return ZERO;
    }

    @Override
    public Rotation getPre(IEvaluationContext ctx) {
        if (ctx instanceof MolangContext<?> mc) {
            return evaluate(preFunctions, mc);
        }
        return ZERO;
    }

    @Override
    public Rotation getPost(IEvaluationContext ctx) {
        if (ctx instanceof MolangContext<?> mc) {
            return evaluate(postFunctions, mc);
        }
        return ZERO;
    }

    @Override
    public Interpolator<Rotation> getInterpolator() {
        return interpolator;
    }

    @Override
    public Rotation getValue() {
        return getPre();
    }

    private Rotation evaluate(MolangExpression[] functions, MolangContext<?> ctx) {
        float x = (float) functions[0].evaluate(ctx) * mulX * DEGREE_TO_RADIAN;
        float y = (float) functions[1].evaluate(ctx) * mulY * DEGREE_TO_RADIAN;
        float z = (float) functions[2].evaluate(ctx) * mulZ * DEGREE_TO_RADIAN;
        return new Rotation(new Vector3f(x, y, z));
    }
}
