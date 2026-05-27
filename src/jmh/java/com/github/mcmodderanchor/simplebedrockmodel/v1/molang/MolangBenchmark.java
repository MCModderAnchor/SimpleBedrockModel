package com.github.mcmodderanchor.simplebedrockmodel.v1.molang;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.molang.MolangEngineHelper;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangContext;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.compiled.MochaCompiledFunction;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.compiled.Named;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.binding.Entity;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.parser.ast.Expression;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 基准测试：ASM 编译 vs Javassist 编译 vs AST 解释执行<br/>
 * 用于验证迁移后性能相比原实现是否存在明显退化
 * <p>
 * 场景设计原则：
 * - 避免常量折叠：所有表达式都包含运行时参数，确保测的是真实执行开销
 * - 覆盖实际使用场景：从简单到复杂，从纯算术到带上下文的动画表达式
 * <p>
 * 场景列表：
 * 1. simple_arith    — 简单算术 (a + b * 2)
 * 2. math_func       — 单个 math 函数调用 (math.sin(x))
 * 3. math_chain      — math 函数链式调用 (math.clamp(math.sin(x) * 10, -5, 5))
 * 4. ternary         — 三元 + 比较 ((a > b) ? a - b : b - a)
 * 5. poly            — 多项式 (3*x*x - 2*x*x*x)，参数化版本
 * 6. anim_simple     — 简单动画表达式 (query.anim_time * 90)
 * 7. anim_complex    — 复杂动画表达式 (math.sin(q.anim_time*360)*10 + math.cos(q.anim_time*180)*5)
 * 8. temp_heavy      — 大量 temp 变量 (temp.a=x*2; temp.b=temp.a+1; temp.c=temp.b*temp.a; return temp.c-temp.b)
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class MolangBenchmark {

    // ==================== 表达式定义 ====================

    // 1. 简单算术（带参数，不可折叠）
    private static final String EXPR_SIMPLE_ARITH = "a + b * 2";
    // 2. 单个 math 函数
    private static final String EXPR_MATH_FUNC = "math.sin(x)";
    // 3. math 链式
    private static final String EXPR_MATH_CHAIN = "math.clamp(math.sin(x) * 10, -5, 5)";
    // 4. 三元 + 比较
    private static final String EXPR_TERNARY = "(a > b) ? a - b : b - a";
    // 5. 多项式（参数化）
    private static final String EXPR_POLY = "3*x*x - 2*x*x*x";
    // 6. 简单动画
    private static final String EXPR_ANIM_SIMPLE = "query.anim_time * 90";
    // 7. 复杂动画
    private static final String EXPR_ANIM_COMPLEX =
            "math.sin(query.anim_time * 360) * 10 + math.cos(query.anim_time * 180) * 5";
    // 8. temp 变量密集
    private static final String EXPR_TEMP_HEAVY =
            "temp.a = x * 2; temp.b = temp.a + 1; temp.c = temp.b * temp.a; return temp.c - temp.b";

    // ==================== 编译函数接口 ====================

    public interface TwoArgFunc extends MochaCompiledFunction {
        double eval(@Named("a") double a, @Named("b") double b);
    }

    public interface OneArgFunc extends MochaCompiledFunction {
        double eval(@Named("x") double x);
    }

    // ==================== ASM 编译产物 ====================
    private TwoArgFunc asm_simpleArith;
    private OneArgFunc asm_mathFunc;
    private OneArgFunc asm_mathChain;
    private TwoArgFunc asm_ternary;
    private OneArgFunc asm_poly;
    private MolangExpression asm_animSimple;
    private MolangExpression asm_animComplex;
    private OneArgFunc asm_tempHeavy;

    // ==================== Javassist 编译产物 ====================
    private team.unnamed.mocha.runtime.compiled.MochaCompiledFunction javassist_simpleArith;
    private team.unnamed.mocha.runtime.compiled.MochaCompiledFunction javassist_mathFunc;
    private team.unnamed.mocha.runtime.compiled.MochaCompiledFunction javassist_mathChain;
    private team.unnamed.mocha.runtime.compiled.MochaCompiledFunction javassist_ternary;
    private team.unnamed.mocha.runtime.compiled.MochaCompiledFunction javassist_poly;
    private team.unnamed.mocha.runtime.compiled.MochaCompiledFunction javassist_tempHeavy;

    // ==================== 解释执行 ====================
    private MochaEngine<?> interpEngine;
    private List<Expression> parsed_simpleArith;
    private List<Expression> parsed_mathFunc;
    private List<Expression> parsed_mathChain;
    private List<Expression> parsed_ternary;
    private List<Expression> parsed_poly;
    private List<Expression> parsed_tempHeavy;

    private MochaEngine<?> animInterpEngine;
    private List<Expression> parsed_animSimple;
    private List<Expression> parsed_animComplex;

    // ==================== 上下文 ====================
    private MolangContext<Object> animContext;
    private double tick = 0;

    // ---- Javassist 接口（mocha 原版包名） ----
    public interface JTwoArgFunc extends team.unnamed.mocha.runtime.compiled.MochaCompiledFunction {
        double eval(@team.unnamed.mocha.runtime.compiled.Named("a") double a,
                     @team.unnamed.mocha.runtime.compiled.Named("b") double b);
    }
    public interface JOneArgFunc extends team.unnamed.mocha.runtime.compiled.MochaCompiledFunction {
        double eval(@team.unnamed.mocha.runtime.compiled.Named("x") double x);
    }

    public static void main(final String[] args) throws RunnerException {
        final Options opt = new OptionsBuilder()
                .include(MolangBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }

    @Setup(Level.Trial)
    public void prepare() throws Exception {
        // ---- ASM 编译 ----
        {
            MochaEngine<?> e = MochaEngine.createStandard();
            asm_simpleArith = e.compile(EXPR_SIMPLE_ARITH, TwoArgFunc.class);
            asm_mathFunc    = e.compile(EXPR_MATH_FUNC, OneArgFunc.class);
            asm_mathChain   = e.compile(EXPR_MATH_CHAIN, OneArgFunc.class);
            asm_ternary     = e.compile(EXPR_TERNARY, TwoArgFunc.class);
            asm_poly        = e.compile(EXPR_POLY, OneArgFunc.class);
            asm_tempHeavy   = e.compile(EXPR_TEMP_HEAVY, OneArgFunc.class);
        }
        {
            animContext = new MolangContext<>();
            animContext.prepareEvaluation(1.5f);
            MochaEngine<?> e = MolangEngineHelper.createEngine(animContext);
            asm_animSimple  = MolangEngineHelper.compileExpression(e, EXPR_ANIM_SIMPLE);
            asm_animComplex = MolangEngineHelper.compileExpression(e, EXPR_ANIM_COMPLEX);
        }

        // ---- Javassist 编译 (mocha 原版) ----
        {
            team.unnamed.mocha.MochaEngine<?> e = team.unnamed.mocha.MochaEngine.createStandard();
            javassist_simpleArith = e.compile(EXPR_SIMPLE_ARITH, JTwoArgFunc.class);
            javassist_mathFunc    = e.compile(EXPR_MATH_FUNC, JOneArgFunc.class);
            javassist_mathChain   = e.compile(EXPR_MATH_CHAIN, JOneArgFunc.class);
            javassist_ternary     = e.compile(EXPR_TERNARY, JTwoArgFunc.class);
            javassist_poly        = e.compile(EXPR_POLY, JOneArgFunc.class);
            javassist_tempHeavy   = e.compile(EXPR_TEMP_HEAVY, JOneArgFunc.class);
        }

        // ---- 解释执行 ----
        {
            interpEngine = MochaEngine.createStandard();
            parsed_simpleArith = interpEngine.parse(EXPR_SIMPLE_ARITH);
            parsed_mathFunc    = interpEngine.parse(EXPR_MATH_FUNC);
            parsed_mathChain   = interpEngine.parse(EXPR_MATH_CHAIN);
            parsed_ternary     = interpEngine.parse(EXPR_TERNARY);
            parsed_poly        = interpEngine.parse(EXPR_POLY);
            parsed_tempHeavy   = interpEngine.parse(EXPR_TEMP_HEAVY);
        }
        {
            animContext = new MolangContext<>();
            animContext.prepareEvaluation(1.5f);
            animInterpEngine = MolangEngineHelper.createEngine(animContext);
            parsed_animSimple  = animInterpEngine.parse(EXPR_ANIM_SIMPLE);
            parsed_animComplex = animInterpEngine.parse(EXPR_ANIM_COMPLEX);
        }
    }

    // ==================== 1. 简单算术 ====================

    @Benchmark public double simpleArith_asm() {
        return asm_simpleArith.eval(tick++, 3.14);
    }
    @Benchmark public double simpleArith_javassist() {
        return ((JTwoArgFunc) javassist_simpleArith).eval(tick++, 3.14);
    }
    @Benchmark public double simpleArith_interpreted() {
        return interpEngine.eval(parsed_simpleArith);
    }

    // ==================== 2. 单个 math 函数 ====================

    @Benchmark public double mathFunc_asm() {
        return asm_mathFunc.eval(tick++);
    }
    @Benchmark public double mathFunc_javassist() {
        return ((JOneArgFunc) javassist_mathFunc).eval(tick++);
    }
    @Benchmark public double mathFunc_interpreted() {
        return interpEngine.eval(parsed_mathFunc);
    }

    // ==================== 3. math 链式 ====================

    @Benchmark public double mathChain_asm() {
        return asm_mathChain.eval(tick++);
    }
    @Benchmark public double mathChain_javassist() {
        return ((JOneArgFunc) javassist_mathChain).eval(tick++);
    }
    @Benchmark public double mathChain_interpreted() {
        return interpEngine.eval(parsed_mathChain);
    }

    // ==================== 4. 三元 + 比较 ====================

    @Benchmark public double ternary_asm() {
        return asm_ternary.eval(tick++, tick + 5);
    }
    @Benchmark public double ternary_javassist() {
        return ((JTwoArgFunc) javassist_ternary).eval(tick++, tick + 5);
    }
    @Benchmark public double ternary_interpreted() {
        return interpEngine.eval(parsed_ternary);
    }

    // ==================== 5. 多项式 ====================

    @Benchmark public double poly_asm() {
        return asm_poly.eval(tick++);
    }
    @Benchmark public double poly_javassist() {
        return ((JOneArgFunc) javassist_poly).eval(tick++);
    }
    @Benchmark public double poly_interpreted() {
        return interpEngine.eval(parsed_poly);
    }

    // ==================== 6. 简单动画 ====================

    @Benchmark public double animSimple_asm() {
        animContext.prepareEvaluation((float) (tick++ * 0.05));
        return asm_animSimple.evaluate(animContext);
    }
    @Benchmark public double animSimple_interpreted() {
        animContext.prepareEvaluation((float) (tick++ * 0.05));
        return animInterpEngine.eval(parsed_animSimple);
    }

    // ==================== 7. 复杂动画 ====================

    @Benchmark public double animComplex_asm() {
        animContext.prepareEvaluation((float) (tick++ * 0.05));
        return asm_animComplex.evaluate(animContext);
    }
    @Benchmark public double animComplex_interpreted() {
        animContext.prepareEvaluation((float) (tick++ * 0.05));
        return animInterpEngine.eval(parsed_animComplex);
    }

    // ==================== 8. temp 变量密集 ====================

    @Benchmark public double tempHeavy_asm() {
        return asm_tempHeavy.eval(tick++);
    }
    @Benchmark public double tempHeavy_javassist() {
        return ((JOneArgFunc) javassist_tempHeavy).eval(tick++);
    }
    @Benchmark public double tempHeavy_interpreted() {
        return interpEngine.eval(parsed_tempHeavy);
    }
}
