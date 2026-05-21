package com.github.mcmodderanchor.simplebedrockmodel.v1.molang;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.compiled.MochaCompiledFunction;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.compiled.Named;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 非 double 返回类型的编译函数测试。
 * 移植自 team.unnamed.mocha.runtime.IntCompiledFunctionTest。
 */
class IntCompiledFunctionTest {

    @Test
    @DisplayName("int 返回类型的编译函数")
    void testIntReturn() {
        final IntFunction function = MochaEngine.createStandard()
                .compile("math.mod(value / 20, 3)", IntFunction.class);

        assertEquals(0, function.get(0));
        assertEquals(1, function.get(20));
        assertEquals(2, function.get(40));
        assertEquals(0, function.get(60));
        assertEquals(1, function.get(80));
        assertEquals(2, function.get(100));
        assertEquals(0, function.get(120));
    }

    @Test
    @DisplayName("long 返回类型的编译函数")
    void testLongReturn() {
        final LongFunction function = MochaEngine.createStandard()
                .compile("value * 2 + 1", LongFunction.class);

        assertEquals(3L, function.get(1));
        assertEquals(21L, function.get(10));
        assertEquals(201L, function.get(100));
    }

    @Test
    @DisplayName("double 参数 int 返回 — 截断测试")
    void testDoubleParamIntReturnTruncation() {
        final IntFunction function = MochaEngine.createStandard()
                .compile("value", IntFunction.class);

        // double 3.7 → int 3 (truncation)
        assertEquals(3, function.get(3.7));
        assertEquals(-2, function.get(-2.3));
    }

    public interface IntFunction extends MochaCompiledFunction {
        int get(@Named("value") double value);
    }

    public interface LongFunction extends MochaCompiledFunction {
        long get(@Named("value") double value);
    }
}
