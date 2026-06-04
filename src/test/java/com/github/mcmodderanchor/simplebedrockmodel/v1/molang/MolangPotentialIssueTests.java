package com.github.mcmodderanchor.simplebedrockmodel.v1.molang;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.molang.MolangEngineHelper;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangContext;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.binding.Binding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Molang 实现扫描中发现的潜在问题回归测试。
 */
class MolangPotentialIssueTests {

    @Test
    @DisplayName("同优先级减法应按左结合求值")
    void subtractionShouldBeLeftAssociative() {
        MochaEngine<?> engine = MochaEngine.createStandard();

        assertEquals(5.0, engine.eval("10 - 3 - 2"), 0.0001,
                "解释器: 10 - 3 - 2 应等价于 (10 - 3) - 2");
        assertEquals(5.0, engine.compile("10 - 3 - 2").evaluate(), 0.0001,
                "编译器: 10 - 3 - 2 应等价于 (10 - 3) - 2");
    }

    @Test
    @DisplayName("同优先级除法应按左结合求值")
    void divisionShouldBeLeftAssociative() {
        MochaEngine<?> engine = MochaEngine.createStandard();

        assertEquals(2.0, engine.eval("20 / 5 / 2"), 0.0001,
                "解释器: 20 / 5 / 2 应等价于 (20 / 5) / 2");
        assertEquals(2.0, engine.compile("20 / 5 / 2").evaluate(), 0.0001,
                "编译器: 20 / 5 / 2 应等价于 (20 / 5) / 2");
    }

    @Test
    @DisplayName("null coalescing 表达式应能编译并求值（避免常量折叠）")
    void nullCoalescingShouldCompileAndEvaluate() {
        MolangContext<Object> ctx = new MolangContext<>();
        MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);
        MolangEngineHelper.compileExpression(engine, "variable.cond = 0").evaluate(ctx);

        assertEquals(5.0, engine.eval("variable.cond ?? 5"), 0.0001,
                "解释器: variable.cond 为 0 时应返回右侧值");
        MolangExpression expr = assertDoesNotThrow(() ->
                MolangEngineHelper.compileExpression(engine, "variable.cond ?? 5"),
                "编译器: variable.cond ?? 5 应正常编译");
        assertEquals(5.0, assertDoesNotThrow(() -> expr.evaluate(ctx)), 0.0001,
                "编译器: variable.cond 为 0 时应返回右侧值");
    }

    @Test
    @DisplayName("二元 conditional 表达式应能编译并求值（避免常量折叠）")
    void binaryConditionalShouldCompileAndEvaluate() {
        MolangContext<Object> trueCtx = new MolangContext<>();
        MochaEngine<?> trueEngine = MolangEngineHelper.createEngine(trueCtx);
        MolangEngineHelper.compileExpression(trueEngine, "variable.cond = 1").evaluate(trueCtx);
        MolangContext<Object> falseCtx = new MolangContext<>();
        MochaEngine<?> falseEngine = MolangEngineHelper.createEngine(falseCtx);
        MolangEngineHelper.compileExpression(falseEngine, "variable.cond = 0").evaluate(falseCtx);

        assertEquals(7.0, trueEngine.eval("variable.cond ? 7"), 0.0001,
                "解释器: variable.cond 非零时应返回 7");
        assertEquals(0.0, falseEngine.eval("variable.cond ? 7"), 0.0001,
                "解释器: variable.cond 为 0 时应返回 0");
        MolangExpression trueExpr = assertDoesNotThrow(() ->
                MolangEngineHelper.compileExpression(trueEngine, "variable.cond ? 7"),
                "编译器: variable.cond ? 7 应正常编译");
        MolangExpression falseExpr = assertDoesNotThrow(() ->
                MolangEngineHelper.compileExpression(falseEngine, "variable.cond ? 7"),
                "编译器: variable.cond ? 7 应正常编译");
        assertEquals(7.0, assertDoesNotThrow(() -> trueExpr.evaluate(trueCtx)), 0.0001,
                "编译器: variable.cond 非零时应返回 7");
        assertEquals(0.0, assertDoesNotThrow(() -> falseExpr.evaluate(falseCtx)), 0.0001,
                "编译器: variable.cond 为 0 时应返回 0");
    }

    @Test
    @DisplayName("三元 conditional 表达式应能编译并求值（避免常量折叠）")
    void ternaryConditionalShouldCompileAndEvaluate() {
        MolangContext<Object> trueCtx = new MolangContext<>();
        MochaEngine<?> trueEngine = MolangEngineHelper.createEngine(trueCtx);
        MolangEngineHelper.compileExpression(trueEngine, "variable.cond = 1").evaluate(trueCtx);
        MolangContext<Object> falseCtx = new MolangContext<>();
        MochaEngine<?> falseEngine = MolangEngineHelper.createEngine(falseCtx);
        MolangEngineHelper.compileExpression(falseEngine, "variable.cond = 0").evaluate(falseCtx);

        assertEquals(7.0, trueEngine.eval("variable.cond ? 7 : 9"), 0.0001,
                "解释器: true 分支应返回 7");
        assertEquals(9.0, falseEngine.eval("variable.cond ? 7 : 9"), 0.0001,
                "解释器: false 分支应返回 9");
        MolangExpression trueExpr = assertDoesNotThrow(() ->
                MolangEngineHelper.compileExpression(trueEngine, "variable.cond ? 7 : 9"),
                "编译器: variable.cond ? 7 : 9 应正常编译");
        MolangExpression falseExpr = assertDoesNotThrow(() ->
                MolangEngineHelper.compileExpression(falseEngine, "variable.cond ? 7 : 9"),
                "编译器: variable.cond ? 7 : 9 应正常编译");
        assertEquals(7.0, assertDoesNotThrow(() -> trueExpr.evaluate(trueCtx)), 0.0001,
                "编译器: true 分支应返回 7");
        assertEquals(9.0, assertDoesNotThrow(() -> falseExpr.evaluate(falseCtx)), 0.0001,
                "编译器: false 分支应返回 9");
    }

    @Test
    @DisplayName("三元 conditional 分支包含非纯函数时应能编译并求值")
    void ternaryConditionalWithRuntimeBranchesShouldCompileAndEvaluate() {
        MolangContext<Object> ctx = new MolangContext<>();
        MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);
        MolangEngineHelper.compileExpression(engine, "variable.cond = 1").evaluate(ctx);

        assertEquals(1.0, engine.eval("variable.cond ? math.max(math.random(0, 1), 1) : 0"), 0.0001,
                "解释器: true 分支应返回 1");
        MolangExpression expr = assertDoesNotThrow(() ->
                MolangEngineHelper.compileExpression(engine, "variable.cond ? math.max(math.random(0, 1), 1) : 0"),
                "编译器: 非纯分支三元表达式应正常编译");
        assertEquals(1.0, assertDoesNotThrow(() -> expr.evaluate(ctx)), 0.0001,
                "编译器: true 分支应返回 1");
    }

    @Test
    @DisplayName("temp 赋值作为最终表达式时应返回右侧值")
    void tempAssignmentShouldReturnAssignedValue() {
        MochaEngine<?> engine = MochaEngine.createStandard();

        assertEquals(5.0, engine.eval("temp.x = 5"), 0.0001,
                "解释器: temp.x = 5 应返回 5");
        assertEquals(5.0, assertDoesNotThrow(() -> engine.compile("temp.x = 5").evaluate()), 0.0001,
                "编译器: temp.x = 5 应返回 5");
    }

    @Test
    @DisplayName("temp 赋值作为子表达式时应保留右侧值")
    void tempAssignmentShouldLeaveAssignedValueOnStack() {
        MochaEngine<?> engine = MochaEngine.createStandard();

        assertEquals(6.0, engine.eval("(temp.x = 5) + 1"), 0.0001,
                "解释器: (temp.x = 5) + 1 应返回 6");
        assertEquals(6.0, assertDoesNotThrow(() -> engine.compile("(temp.x = 5) + 1").evaluate()), 0.0001,
                "编译器: (temp.x = 5) + 1 应返回 6");
    }

    @Test
    @DisplayName("variable 赋值后续表达式不应被 expectedType 污染")
    void variableAssignmentShouldNotPolluteFollowingExpression() {
        MolangContext<Object> ctx = new MolangContext<>();
        ctx.prepareEvaluation(2.5f);
        MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);

        MolangExpression expr = assertDoesNotThrow(() ->
                MolangEngineHelper.compileExpression(engine, "variable.x = 1; !query.anim_time"),
                "variable.x = 1 后续表达式应正常编译");
        assertEquals(0.0, assertDoesNotThrow(() -> expr.evaluate(ctx)), 0.0001,
                "最后的 !query.anim_time 应返回 0.0");
    }

    @Test
    @DisplayName("无 entity 类型信息时 query access 应 fallback 为 0 而不是 NPE")
    void queryAccessWithoutEntityTypeShouldFallbackToZero() {
        MochaEngine<?> engine = MochaEngine.createStandard();

        assertEquals(0.0, engine.eval("query.anim_time"), 0.0001,
                "解释器: 未绑定 query 时应返回 0");
        assertEquals(0.0, assertDoesNotThrow(() -> engine.compile("query.anim_time").evaluate()), 0.0001,
                "编译器: 未绑定 query 时应返回 0");
    }

    @Test
    @DisplayName("variable 赋值作为 AND 左操作数时应按布尔值转换")
    void variableAssignmentAsAndOperandShouldCastToBoolean() {
        MolangContext<Object> ctx = new MolangContext<>();
        MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);

        assertEquals(1.0, engine.eval("(variable.flag = math.max(math.random(0, 1), 1)) && 1"), 0.0001,
                "解释器: 赋值结果非零时 AND 应为 true");
        MolangExpression expr = assertDoesNotThrow(() ->
                MolangEngineHelper.compileExpression(engine, "(variable.flag = math.max(math.random(0, 1), 1)) && 1"),
                "编译器: variable 赋值作为 AND 操作数应正常编译");
        assertEquals(1.0, assertDoesNotThrow(() -> expr.evaluate(ctx)), 0.0001,
                "编译器: 赋值结果非零时 AND 应返回 1");
    }

    @Test
    @DisplayName("variable 赋值作为一元非操作数时应按布尔值转换")
    void variableAssignmentAsNegationOperandShouldCastToBoolean() {
        MolangContext<Object> ctx = new MolangContext<>();
        MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);

        assertEquals(0.0, engine.eval("!(variable.flag = math.max(math.random(0, 1), 1))"), 0.0001,
                "解释器: 非零赋值结果取反应为 0");
        MolangExpression expr = assertDoesNotThrow(() ->
                MolangEngineHelper.compileExpression(engine, "!(variable.flag = math.max(math.random(0, 1), 1))"),
                "编译器: variable 赋值作为一元非操作数应正常编译");
        assertEquals(0.0, assertDoesNotThrow(() -> expr.evaluate(ctx)), 0.0001,
                "编译器: 非零赋值结果取反应返回 0");
    }

    @Test
    @DisplayName("temp 命名空间编译时应大小写不敏感")
    void tempNamespaceShouldBeCaseInsensitiveWhenCompiled() {
        MochaEngine<?> engine = MochaEngine.createStandard();

        assertEquals(6.0, engine.eval("Temp.x = math.max(math.random(0, 1), 5); T.x + 1"), 0.0001,
                "解释器: Temp/T 应与 temp/t 等价");
        assertEquals(6.0, assertDoesNotThrow(() ->
                        engine.compile("Temp.x = math.max(math.random(0, 1), 5); T.x + 1").evaluate()), 0.0001,
                "编译器: Temp/T 应与 temp/t 等价");
    }

    @Test
    @DisplayName("无 entity 类型信息时 query call 应 fallback 为 0 而不是 NPE")
    void queryCallWithoutEntityTypeShouldFallbackToZero() {
        MochaEngine<?> engine = MochaEngine.createStandard();

        assertEquals(0.0, engine.eval("query.missing(math.max(math.random(0, 1), 1))"), 0.0001,
                "解释器: 未绑定 query 函数时应返回 0");
        assertEquals(0.0, assertDoesNotThrow(() ->
                        engine.compile("query.missing(math.max(math.random(0, 1), 1))").evaluate()), 0.0001,
                "编译器: 未绑定 query 函数时应返回 0");
    }

    @Test
    @DisplayName("实例绑定方法编译时应以正确 receiver/参数顺序调用")
    void instanceBindingMethodShouldCompileWithCorrectReceiverOrder() {
        MochaEngine<?> engine = MochaEngine.createStandard();
        engine.bindInstance(InstanceBinding.class, new InstanceBinding(), "inst");

        assertEquals(6.0, engine.eval("inst.add(math.max(math.random(0, 1), 2), 4)"), 0.0001,
                "解释器: 实例绑定方法应正常求值");
        assertEquals(6.0, assertDoesNotThrow(() ->
                        engine.compile("inst.add(math.max(math.random(0, 1), 2), 4)").evaluate()), 0.0001,
                "编译器: 实例绑定方法应正常求值");
    }

    public static class InstanceBinding {
        @Binding(value = "add", pure = true)
        public double add(double left, double right) {
            return left + right;
        }
    }
}
