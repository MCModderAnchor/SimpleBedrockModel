package com.github.mcmodderanchor.simplebedrockmodel.v1.molang;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.parser.ParseException;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MochaFunction;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.binding.Binding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 编译器边界情况测试。
 */
class MolangCompilerEdgeTest {

    @Nested
    class SafeMathHandling {
        @Test
        @DisplayName("除零返回 0（安全行为，不抛异常）")
        void divisionByZeroReturnsZero() {
            MochaEngine<?> engine = MochaEngine.createStandard();
            assertEquals(0.0, engine.eval("1 / 0"));
            assertEquals(0.0, engine.compile("1 / 0").evaluate());
        }

        @Test
        @DisplayName("math.sqrt(-1) 返回 0（安全行为，不产生 NaN）")
        void sqrtNegativeReturnsZero() {
            MochaEngine<?> engine = MochaEngine.createStandard();
            assertEquals(0.0, engine.eval("math.sqrt(-1)"));
            assertEquals(0.0, engine.compile("math.sqrt(-1)").evaluate());
        }
    }

    @Nested
    class ParseErrorHandling {
        @Test
        @DisplayName("语法错误时 eval 返回 0")
        void evalReturnsZeroOnParseError() {
            MochaEngine<?> engine = MochaEngine.createStandard();
            // strings require single quotes — double quotes are a parse error
            double result = engine.eval("query.log(1 1);"); // missing comma — syntax error
            assertEquals(0.0, result);
        }

        @Test
        @DisplayName("handleParseExceptions 回调在语法错误时被调用")
        void parseExceptionHandlerCalled() {
            MochaEngine<?> engine = MochaEngine.createStandard();
            AtomicReference<ParseException> captured = new AtomicReference<>();

            engine.handleParseExceptions(captured::set);
            engine.eval("this is invalid syntax!!");

            assertNotNull(captured.get(), "handler 应被调用");
            assertNotNull(captured.get().getMessage());
        }

        @Test
        @DisplayName("语法错误时 compile 返回 0 的无操作函数")
        void compileReturnsNopOnParseError() {
            MochaEngine<?> engine = MochaEngine.createStandard();
            // Double quotes are a lex error in Molang
            MochaFunction fn = engine.compile("\"invalid string\"");
            assertEquals(0.0, fn.evaluate());
        }
    }

    @Nested
    class BindingTests {
        @Test
        @DisplayName("bind：注解类静态字段和方法绑定到 scope")
        void bindStaticClass() {
            MochaEngine<?> engine = MochaEngine.createStandard();
            engine.bind(StaticQueryBindings.class);

            double result = engine.eval("query.answer");
            assertEquals(42.0, result);

            double calledResult = engine.eval("q.halve(10)");
            assertEquals(5.0, calledResult);
        }

        @Test
        @DisplayName("bindInstance：实例方法绑定到 scope")
        void bindInstanceMethod() {
            MochaEngine<?> engine = MochaEngine.createStandard();
            InstanceBindings instance = new InstanceBindings();
            engine.bindInstance(InstanceBindings.class, instance, "custom", "c");

            double result = engine.eval("custom.get_value()");
            assertEquals(77.0, result);

            // alias
            double aliasedResult = engine.eval("c.get_value()");
            assertEquals(77.0, aliasedResult);
        }

        @Binding({"query", "q"})
        public static class StaticQueryBindings {
            @Binding("answer")
            public static double answer = 42.0;

            @Binding("halve")
            public static double halve(double value) {
                return value / 2.0;
            }
        }

        public static class InstanceBindings {
            @Binding("get_value")
            public double getValue() {
                return 77.0;
            }
        }
    }

    @Nested
    class PrepareEvalTests {
        @Test
        @DisplayName("prepareEval 缓存解析结果并正确求值")
        void prepareEvalCachesAndEvaluates() {
            MochaEngine<?> engine = MochaEngine.createStandard();

            MochaFunction fn = engine.prepareEval("math.pow(3, 4)");
            assertEquals(81.0, fn.evaluate());
            assertEquals(81.0, fn.evaluate()); // cached, should be idempotent
        }

        @Test
        @DisplayName("prepareEval 对语法错误返回返回 0 的函数")
        void prepareEvalReturnsZeroOnError() {
            MochaEngine<?> engine = MochaEngine.createStandard();

            MochaFunction fn = engine.prepareEval("syntax error here !!!");
            assertEquals(0.0, fn.evaluate());
        }
    }

    @Nested
    class EmptyExpression {
        @Test
        @DisplayName("空字符串编译返回 0")
        void emptyExpressionReturnsZero() {
            MochaEngine<?> engine = MochaEngine.createStandard();

            MochaFunction fn = engine.compile("");
            assertEquals(0.0, fn.evaluate());
        }

        @Test
        @DisplayName("空字符串 eval 返回 0")
        void emptyExpressionEvalReturnsZero() {
            MochaEngine<?> engine = MochaEngine.createStandard();
            assertEquals(0.0, engine.eval(""));
        }
    }

    @Nested
    class DoublesAsBooleans {
        @Test
        @DisplayName("非零 double 值在条件中视为 true")
        void nonzeroDoubleIsTruthy() {
            MochaEngine<?> engine = MochaEngine.createStandard();

            assertEquals(10.0, engine.eval("3.5 ? 10 : 20"));
            assertEquals(20.0, engine.eval("0.0 ? 10 : 20"));
            assertEquals(10.0, engine.eval("-0.01 ? 10 : 20"));
        }
    }
}
