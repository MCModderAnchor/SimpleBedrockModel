package com.github.mcmodderanchor.simplebedrockmodel.v1.molang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Molang 大小写不敏感测试。
 * 移植自 team.unnamed.mocha.runtime.CaseSensitivityTest。
 */
class CaseSensitivityTest {

    private static void assertEvaluatesAndCompiles(double expected, String expression) {
        final MochaEngine<?> engine = MochaEngine.createStandard();
        assertEquals(expected, engine.eval(expression), "interpreter: " + expression);
        assertEquals(expected, engine.compile(expression).evaluate(), "compiled: " + expression);
    }

    @Test
    @DisplayName("math.PI 各种大小写组合")
    void mathPiCaseVariations() {
        assertEvaluatesAndCompiles(Math.PI, "MATH.PI");
        assertEvaluatesAndCompiles(Math.PI, "math.PI");
        assertEvaluatesAndCompiles(Math.PI, "Math.PI");
        assertEvaluatesAndCompiles(Math.PI, "Math.pi");
        assertEvaluatesAndCompiles(Math.PI, "math.pi");
        assertEvaluatesAndCompiles(Math.PI, "MATH.pi");
        assertEvaluatesAndCompiles(Math.PI, "MATH.Pi");
    }

    @Test
    @DisplayName("math.abs 各种大小写组合")
    void mathAbsCaseVariations() {
        assertEvaluatesAndCompiles(20D, "MATH.ABS(-20)");
        assertEvaluatesAndCompiles(20D, "math.ABS(-20)");
        assertEvaluatesAndCompiles(20D, "Math.ABS(-20)");
        assertEvaluatesAndCompiles(20D, "Math.abs(-20)");
    }

    @Test
    @DisplayName("math.clamp 各种大小写组合")
    void mathClampCaseVariations() {
        assertEvaluatesAndCompiles(50D, "Math.clamp(10, 50, 100)");
        assertEvaluatesAndCompiles(50D, "Math.CLAMP(10, 50, 100)");
        assertEvaluatesAndCompiles(50D, "math.clamp(10, 50, 100)");
        assertEvaluatesAndCompiles(50D, "math.CLAMP(10, 50, 100)");
    }

    @Test
    @DisplayName("混合大小写表达式")
    void mixedCaseExpression() {
        assertEvaluatesAndCompiles(100 + 5 + Math.PI, "MATH.ABS(-100) + Math.sqrt(25) + math.PI");
    }

    @Test
    @DisplayName("temp 变量大小写不敏感")
    void tempVariableCaseInsensitive() {
        assertEvaluatesAndCompiles(1D, "tEMP.x = 1; return Temp.X;");
        assertEvaluatesAndCompiles(1D, "temp.X = 2; tEMp.y = 3; return TEMP.Y - Temp.x;");
    }
}
