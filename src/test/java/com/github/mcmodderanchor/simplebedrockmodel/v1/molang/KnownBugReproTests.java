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

    @Test
    @DisplayName("Bug 1 回归: !math.min(2, 0) 应先调用函数再取反")
    void bangWithMultiArgumentFunctionCall_bindsToCallResult() {
        MochaEngine<?> engine = MochaEngine.createStandard();

        assertEquals(1.0, engine.eval("!math.min(2, 0)"), 0.0001,
                "解释器: !math.min(2, 0) 应等价于 !(math.min(2, 0))");
        assertEquals(1.0, engine.compile("!math.min(2, 0)").evaluate(), 0.0001,
                "编译器: !math.min(2, 0) 应等价于 !(math.min(2, 0))");
    }

    @Test
    @DisplayName("Bug 1 回归: -math.abs(-3) 应先调用函数再取负")
    void arithmeticNegationWithFunctionCall_bindsToCallResult() {
        MochaEngine<?> engine = MochaEngine.createStandard();

        assertEquals(-3.0, engine.eval("-math.abs(-3)"), 0.0001,
                "解释器: -math.abs(-3) 应等价于 -(math.abs(-3))");
        assertEquals(-3.0, engine.compile("-math.abs(-3)").evaluate(), 0.0001,
                "编译器: -math.abs(-3) 应等价于 -(math.abs(-3))");
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
    // 用 math.random(0, 1)（非纯函数，防常量折叠）作为 math.abs 的参数来触发。
    // ========================================================================

    @Test
    @DisplayName("Bug 2: 1 && math.abs(math.random(0, 1)) 应正常编译并求值")
    void andWithMathAbsOfRandom_compilesAndEvaluates() {
        MolangContext<Object> ctx = new MolangContext<>();
        MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);

        MolangExpression compiled = assertDoesNotThrow(() ->
                MolangEngineHelper.compileExpression(engine, "1 && math.abs(math.random(0, 1))"),
                "1 && math.abs(math.random(0, 1)) 应正常编译");
        assertDoesNotThrow(() -> compiled.evaluate(ctx),
                "1 && math.abs(math.random(0, 1)) 应正常求值");
    }

    @Test
    @DisplayName("Bug 2: 0 || math.max(math.random(0, 1), 1) 应正常编译并返回 1.0")
    void orWithMathMax_compilesAndEvaluates() {
        MolangContext<Object> ctx = new MolangContext<>();
        MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);

        MolangExpression compiled = assertDoesNotThrow(() ->
                MolangEngineHelper.compileExpression(engine, "0 || math.max(math.random(0, 1), 1)"),
                "0 || math.max(math.random(0, 1), 1) 应正常编译");
        assertEquals(1.0, assertDoesNotThrow(() -> compiled.evaluate(ctx)), 0.0001,
                "0 || math.max(math.random(0, 1), 1) 应返回 1.0");
    }

    @Test
    @DisplayName("Bug 2 回归: !math.max(math.random(0, 1), 1) 应正常编译并返回 0.0")
    void bangWithMathMaxOfRandom_compilesAndEvaluates() {
        MolangContext<Object> ctx = new MolangContext<>();
        MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);

        MolangExpression compiled = assertDoesNotThrow(() ->
                MolangEngineHelper.compileExpression(engine, "!math.max(math.random(0, 1), 1)"),
                "!math.max(math.random(0, 1), 1) 应正常编译");
        assertEquals(0.0, assertDoesNotThrow(() -> compiled.evaluate(ctx)), 0.0001,
                "!math.max(math.random(0, 1), 1) 应返回 0.0");
    }

    @Test
    @DisplayName("Bug 2 回归: 函数调用位于 AND 左侧时也应恢复 expectedType")
    void andWithFunctionCallOnLeft_compilesAndEvaluates() {
        MolangContext<Object> ctx = new MolangContext<>();
        MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);

        MolangExpression compiled = assertDoesNotThrow(() ->
                MolangEngineHelper.compileExpression(engine, "math.max(math.random(0, 1), 1) && 1"),
                "math.max(math.random(0, 1), 1) && 1 应正常编译");
        assertEquals(1.0, assertDoesNotThrow(() -> compiled.evaluate(ctx)), 0.0001,
                "math.max(math.random(0, 1), 1) && 1 应返回 1.0");
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
    @DisplayName("Bug 3: !query.anim_time 应正常返回 0.0")
    void bangQueryAccess_evaluatesToFalse() {
        MolangContext<Object> ctx = new MolangContext<>();
        ctx.prepareEvaluation(2.5f);
        MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);

        MolangExpression expr = assertDoesNotThrow(() ->
                MolangEngineHelper.compileExpression(engine, "!query.anim_time"),
                "!query.anim_time 应正常编译");
        assertEquals(0.0, assertDoesNotThrow(() -> expr.evaluate(ctx)), 0.0001,
                "!query.anim_time 应返回 0.0");
    }

    @Test
    @DisplayName("Bug 3: !variable.x 应正常返回 0.0")
    void bangVariableAccess_evaluatesToFalse() {
        MolangContext<Object> ctx = new MolangContext<>();
        ctx.prepareEvaluation(1.0f);
        MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);

        // 先赋值
        MolangEngineHelper.compileExpression(engine, "variable.x = 42").evaluate(ctx);
        // 再取反 — 非零值取反应为 0.0
        MolangExpression expr = assertDoesNotThrow(() ->
                MolangEngineHelper.compileExpression(engine, "!variable.x"),
                "!variable.x 应正常编译");
        assertEquals(0.0, assertDoesNotThrow(() -> expr.evaluate(ctx)), 0.0001,
                "!variable.x 应返回 0.0");
    }

    @Test
    @DisplayName("Bug 3 回归: !variable.zero 应正常返回 1.0")
    void bangVariableZero_evaluatesToTrue() {
        MolangContext<Object> ctx = new MolangContext<>();
        MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);

        MolangEngineHelper.compileExpression(engine, "variable.zero = 0").evaluate(ctx);
        MolangExpression expr = assertDoesNotThrow(() ->
                MolangEngineHelper.compileExpression(engine, "!variable.zero"),
                "!variable.zero 应正常编译");
        assertEquals(1.0, assertDoesNotThrow(() -> expr.evaluate(ctx)), 0.0001,
                "!variable.zero 应返回 1.0");
    }

    @Test
    @DisplayName("Bug 3 回归: query access 位于 AND 左侧时应正常转 boolean")
    void queryAccessInAnd_compilesAndEvaluates() {
        MolangContext<Object> ctx = new MolangContext<>();
        ctx.prepareEvaluation(2.5f);
        MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);

        MolangExpression expr = assertDoesNotThrow(() ->
                MolangEngineHelper.compileExpression(engine, "query.anim_time && 1"),
                "query.anim_time && 1 应正常编译");
        assertEquals(1.0, assertDoesNotThrow(() -> expr.evaluate(ctx)), 0.0001,
                "query.anim_time && 1 应返回 1.0");
    }

    @Test
    @DisplayName("Bug 3 回归: variable access 位于 OR 右侧时应正常转 boolean")
    void variableAccessInOr_compilesAndEvaluates() {
        MolangContext<Object> ctx = new MolangContext<>();
        MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);

        MolangEngineHelper.compileExpression(engine, "variable.enabled = 1").evaluate(ctx);
        MolangExpression expr = assertDoesNotThrow(() ->
                MolangEngineHelper.compileExpression(engine, "0 || variable.enabled"),
                "0 || variable.enabled 应正常编译");
        assertEquals(1.0, assertDoesNotThrow(() -> expr.evaluate(ctx)), 0.0001,
                "0 || variable.enabled 应返回 1.0");
    }
}
