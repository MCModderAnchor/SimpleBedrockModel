package com.github.mcmodderanchor.simplebedrockmodel.v1.molang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * break/continue 控制流测试。
 * 移植自 team.unnamed.mocha.runtime.BreakContinueTest。
 */
class BreakContinueTest {

    @Test
    @DisplayName("break: 在 loop 中提前跳出")
    void testBreak() {
        final double value = MochaEngine.createStandard().eval(String.join("\n",
                "t.i = 0;",
                "loop(10, {",
                "    t.i = t.i + 1;",
                "    (t.i >= 5) ? break;",
                "});",
                "return t.i;"
        ));
        assertEquals(5.0D, value);
    }

    @Test
    @DisplayName("continue: 跳过 loop 中部分迭代")
    void testContinue() {
        final double value = MochaEngine.createStandard().eval(
                "t.i = 0;" +
                        "t.sum = 0;" +
                        "loop(20, {" +
                        "t.i = t.i + 1;" +
                        "((t.i < 8) || (t.i > 17)) ? continue;" +
                        "t.sum = t.sum + t.i;" +
                        "});" +
                        "return t.sum;"
        );
        // t.i loop 1..20, skip 1-7 and 18-20
        // accumulate 8+9+...+17 = (8+17)*10/2 = 125
        assertEquals(125.0D, value);
    }

    @Test
    @DisplayName("break 在嵌套 loop 中只跳出内层")
    void testBreakNestedLoop() {
        final double value = MochaEngine.createStandard().eval(String.join("\n",
                "t.count = 0;",
                "loop(3, {",
                "    loop(2, {",
                "        t.count = t.count + 1;",
                "        (t.count >= 4) ? break;",
                "    });",
                "});",
                "return t.count;"
        ));
        // outer=1: inner 1→2 (count=2, break at >=4 no), inner end
        // outer=2: inner 3→4 (count=4, break triggered) 
        // outer=3: inner starts, count=5 >=4, break immediately
        // result: 5
        assertEquals(5.0D, value);
    }
}
