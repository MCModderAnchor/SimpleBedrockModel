package com.github.mcmodderanchor.simplebedrockmodel.v1.molang;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.molang.MolangEngineHelper;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangContext;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 已知 Bug 的复现测试（仅使用 SBM 内置绑定）。
 * <p>
 * 这些测试验证 SimpleBedrockModel 中 Molang 实现的三个 Bug。
 * 修复代码前这些测试应该失败（抛出异常或返回错误结果）。
 */
class KnownBugReproTests {

    // ========================================================================
    // Bug 1: MolangParserImpl — BANG 解析未考虑后续函数调用
    //
    // !math.abs(0) 被解析为：
    //   CallExpression(
    //     UnaryExpression(LOGICAL_NEGATION, AccessExpression(math, abs)),
    //     [DoubleExpression(0)]
    //   )
    // 语义：(!math.abs)(0) → 0.0(0) → 因非函数调用回退到 0.0
    //
    // 正确应为：
    //   UnaryExpression(LOGICAL_NEGATION,
    //     CallExpression(AccessExpression(math, abs), [DoubleExpression(0)])
    //   )
    // 语义：!(math.abs(0)) → !0.0 → 1.0
    // ========================================================================

    @Test
    @DisplayName("Bug 1: !math.abs(0) 应为 !0.0 = 1.0，实际为 0.0")
    void bangWithFunctionCall_wrongResult() {
        MochaEngine<?> engine = MochaEngine.createStandard();

        // math.abs(0) = 0.0（假值）
        // 正确: !math.abs(0) = !0.0 = 1.0
        // Bug: (!math.abs)(0) → 0.0 作为函数调用 → 回退到 0.0
        double interpreted = engine.eval("!math.abs(0)");
        double compiled = engine.compile("!math.abs(0)").evaluate();

        assertEquals(1.0, interpreted, 0.0001,
                "解释器: !math.abs(0) = !0.0 = 1.0, 但Bug下返回 0.0");
        assertEquals(1.0, compiled, 0.0001,
                "编译器: !math.abs(0) = !0.0 = 1.0, 但Bug下返回 0.0");
    }

    // ========================================================================
    // Bug 2: MolangCompilingVisitor.visitCall — 参数循环修改 expectedType 未恢复
    //
    // 当 visitCall 被 AND/OR 处理器调用时，处理器已设置 expectedType = BOOLEAN_TYPE。
    // visitCall 的参数循环将 expectedType 覆盖为参数类型（如 DOUBLE_TYPE），
    // 循环结束后未恢复。返回值处理时比较的是被覆盖后的 expectedType（DOUBLE_TYPE），
    // 而非原始的 BOOLEAN_TYPE。此时跳过 addCast（double→double 无操作），
    // 但栈上留下 double，而 AND 处理器的 IFEQ 期望 int → ASM 帧合并失败。
    //
    // 用 math.random()（非纯函数，防常量折叠）作为 math.abs 的参数来触发。
    // ========================================================================

    @Test
    @DisplayName("Bug 2: 1 && math.abs(math.random()) ASM 帧合并失败")
    void andWithMathAbsOfRandom_throwsDuringCompile() {
        MochaEngine<?> engine = MochaEngine.createStandard();

        // math.abs 的参数循环将 expectedType 覆盖为 DOUBLE_TYPE，
        // 返回后 AND 的 IFEQ 期望 int 但栈上是 double → 崩溃
        assertThrows(Throwable.class, () -> {
            engine.compile("1 && math.abs(math.random())");
        }, "1 && math.abs(math.random()) 编译时应抛出异常");
    }

    @Test
    @DisplayName("Bug 2: 0 || math.max(math.random(), 1) 同样触发")
    void orWithMathMax_throwsDuringCompile() {
        MochaEngine<?> engine = MochaEngine.createStandard();

        assertThrows(Throwable.class, () -> {
            engine.compile("0 || math.max(math.random(), 1)");
        }, "0 || math.max(math.random(), 1) 编译时应抛出异常");
    }

    // ========================================================================
    // Bug 3: MolangCompilingVisitor.visitAccess — 未对 expectedType 做类型转换
    //
    // visitUnary(LOGICAL_NEGATION) 设置 expectedType = BOOLEAN_TYPE，
    // 然后访问内层表达式。当内层是 visitAccess（如 query.anim_time）时，
    // visitAccess 的路径直接返回 Type.DOUBLE_TYPE，推 double 上栈，
    // 但后续 LOGICAL_NEGATION 的 IFNE 期望 int → VerifyError。
    // ========================================================================

    @Test
    @DisplayName("Bug 3: !query.anim_time 导致 VerifyError")
    void bangQueryAccess_throwsVerifyError() {
        MolangContext<Object> ctx = new MolangContext<>();
        ctx.prepareEvaluation(2.5f);
        MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);

        assertThrows(Throwable.class, () -> {
            MolangExpression expr = MolangEngineHelper.compileExpression(engine, "!query.anim_time");
            expr.evaluate(ctx);
        }, "!query.anim_time 应抛出 VerifyError");
    }

    @Test
    @DisplayName("Bug 3: !variable.x（variable 路径也返回 double）")
    void bangVariableAccess_throwsVerifyError() {
        MolangContext<Object> ctx = new MolangContext<>();
        ctx.prepareEvaluation(1.0f);
        MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);

        // 先赋值
        MolangEngineHelper.compileExpression(engine, "variable.x = 42").evaluate(ctx);
        // 再取反 — variable 路径也返回 double 不检查 expectedType
        assertThrows(Throwable.class, () -> {
            MolangExpression expr = MolangEngineHelper.compileExpression(engine, "!variable.x");
            expr.evaluate(ctx);
        }, "!variable.x 应抛出 VerifyError");
    }
}
