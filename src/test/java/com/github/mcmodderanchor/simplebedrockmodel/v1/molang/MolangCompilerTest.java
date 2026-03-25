package com.github.mcmodderanchor.simplebedrockmodel.v1.molang;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.molang.MolangEngineHelper;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MochaFunction;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangContext;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.compiled.MochaCompiledFunction;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.compiled.Named;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.standard.MochaMath;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.value.Function;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.value.NumberValue;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.value.ObjectProperty;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.value.ObjectValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.StringJoiner;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MoLang ASM 编译器迁移验证测试。
 * 移植自 mocha 原始测试 + 新增 MolangExpression 上下文测试。
 */
class MolangCompilerTest {

    // ==================== 移植自 mocha: ArithmeticCompiledRuntimeTest ====================

    @Nested
    class ArithmeticCompiled {
        @Test
        void comparisonWithBooleanReturn() {
            final MochaEngine<?> engine = MochaEngine.createStandard();

            final ComparisonFunction gt = engine.compile("a > b", ComparisonFunction.class);
            assertTrue(gt.compare(10, 5));
            assertFalse(gt.compare(-50, -20));
            assertFalse(gt.compare(3D, 3D));

            final ComparisonFunction lt = engine.compile("a < b", ComparisonFunction.class);
            assertFalse(lt.compare(10, 5));
            assertTrue(lt.compare(-50, -20));
            assertFalse(lt.compare(3D, 3D));

            final ComparisonFunction gte = engine.compile("a >= b", ComparisonFunction.class);
            assertTrue(gte.compare(10, 5));
            assertFalse(gte.compare(-50, -20));
            assertTrue(gte.compare(3D, 3D));

            final ComparisonFunction lte = engine.compile("a <= b", ComparisonFunction.class);
            assertFalse(lte.compare(10, 5));
            assertTrue(lte.compare(-50, -20));
            assertTrue(lte.compare(3D, 3D));

            final ComparisonFunction eq = engine.compile("a == b", ComparisonFunction.class);
            assertFalse(eq.compare(10, 5));
            assertFalse(eq.compare(-50, -20));
            assertTrue(eq.compare(3D, 3D));

            final ComparisonFunction neq = engine.compile("a != b", ComparisonFunction.class);
            assertTrue(neq.compare(10, 5));
            assertTrue(neq.compare(-50, -20));
            assertFalse(neq.compare(3D, 3D));
        }

        @Test
        void comparisonWithLongReturn() {
            final MochaEngine<?> engine = MochaEngine.createStandard();
            final StupidLongComparisonFunction gt = engine.compile("a > b", StupidLongComparisonFunction.class);
            assertEquals(1L, gt.compare(10, 5));
            assertEquals(0L, gt.compare(-50, -20));
            assertEquals(0L, gt.compare(3D, 3D));
        }

        public interface ComparisonFunction extends MochaCompiledFunction {
            boolean compare(@Named("a") double a, @Named("b") double b);
        }

        public interface StupidLongComparisonFunction extends MochaCompiledFunction {
            long compare(@Named("a") double a, @Named("b") double b);
        }
    }

    // ==================== 移植自 mocha: LogicalCompiledRuntimeTest ====================

    @Nested
    class LogicalCompiled {
        @Test
        void andOr() {
            final MochaEngine<?> engine = MochaEngine.createStandard();

            final LogicalFunction and = engine.compile("a && b", LogicalFunction.class);
            assertTrue(and.apply(true, true));
            assertFalse(and.apply(true, false));
            assertFalse(and.apply(false, true));
            assertFalse(and.apply(false, false));

            final LogicalFunction or = engine.compile("a || b", LogicalFunction.class);
            assertTrue(or.apply(true, true));
            assertTrue(or.apply(true, false));
            assertTrue(or.apply(false, true));
            assertFalse(or.apply(false, false));
        }

        public interface LogicalFunction extends MochaCompiledFunction {
            boolean apply(@Named("a") boolean a, @Named("b") boolean b);
        }
    }

    // ==================== 移植自 mocha: MathCompiledRuntimeTest ====================

    @Nested
    class MathCompiled {
        @Test
        void cosAndRound() {
            final MochaEngine<?> engine = MochaEngine.createStandard();

            final MathFunction cos = engine.compile("math.cos(x)", MathFunction.class);
            assertEquals(1D, cos.apply(0), 0.0001);
            assertEquals(4D / 5D, cos.apply(37), 0.01);
            assertEquals(3D / 5D, cos.apply(53), 0.01);
            assertEquals(0D, cos.apply(90), 0.0001);
            assertEquals(-1D, cos.apply(180), 0.0001);

            final MathFunction round = engine.compile("math.round(x)", MathFunction.class);
            assertEquals(5.0D, round.apply(5.4D));
            assertEquals(6.0D, round.apply(5.5D));
            assertEquals(6.0D, round.apply(5.6D));
            assertEquals(-5.0D, round.apply(-5.4D));
        }

        @Test
        void randomAcceptsReversedBounds() {
            for (int i = 0; i < 100; i++) {
                final double value = MochaMath.random(60, -60);
                assertTrue(value >= -60 && value < 60, () -> "unexpected value: " + value);
            }
        }

        @Test
        void randomIntegerAcceptsReversedBounds() {
            for (int i = 0; i < 100; i++) {
                final int value = MochaMath.randomInteger(10, 3);
                assertTrue(value >= 3 && value < 10, () -> "unexpected value: " + value);
            }
        }

        @Test
        void dieRollAcceptsReversedBounds() {
            for (int i = 0; i < 100; i++) {
                final double value = MochaMath.dieRoll(1, 60, -60);
                assertTrue(value >= -15 && value < 15, () -> "unexpected value: " + value);
            }
        }

        @Test
        void dieRollIntegerAcceptsReversedBounds() {
            for (int i = 0; i < 100; i++) {
                final double value = MochaMath.dieRollInteger(1, 10, 3);
                assertTrue(value >= 3 && value < 10, () -> "unexpected value: " + value);
            }
        }

        public interface MathFunction extends MochaCompiledFunction {
            double apply(@Named("x") double x);
        }
    }

    // ==================== 移植自 mocha: MolangCompilerTest ====================

    @Nested
    class ParameterizedCompile {
        @Test
        void ternaryWithIntReturn() {
            final MochaEngine<?> engine = MochaEngine.createStandard();
            final ScriptType script = engine.compile("false ? a : b", ScriptType.class);
            assertEquals(2, script.eval(1, 2));
            assertEquals(50, script.eval(20, 50));
            assertEquals(200, script.eval(50, 200));
        }

        @Test
        void interpolation() {
            final MochaEngine<?> engine = MochaEngine.createStandard();
            final ScriptType script2 = engine.compile("a + (b - a) * 0.5", ScriptType.class);
            assertEquals(5, script2.eval(1, 10));
            assertEquals(20, script2.eval(20, 20));
            assertEquals(50, script2.eval(-50, 150));
        }

        @Test
        void maxMin() {
            final MochaEngine<?> engine = MochaEngine.createStandard();
            final ScriptType script3 = engine.compile("(a > b) ? a : b", ScriptType.class);
            assertEquals(10, script3.eval(10, 5));
            assertEquals(50, script3.eval(50, -20));
            assertEquals(3, script3.eval(3D, 3D));

            final ScriptType script4 = engine.compile("(a < b) ? a : b", ScriptType.class);
            assertEquals(5, script4.eval(10, 5));
            assertEquals(-20, script4.eval(50, -20));
            assertEquals(3, script4.eval(3D, 3D));
        }

        @Test
        void nativeCall() {
            final MochaEngine<?> engine = MochaEngine.createStandard();
            assertEquals(76.0, engine.compile("3 * math.abs(5 * 5 * -1) + 1").evaluate());
        }

        public interface ScriptType extends MochaCompiledFunction {
            int eval(@Named("a") double a, @Named("b") double b);
        }
    }

    // ==================== 移植自 mocha: CompareTest (MolangJS 对比) ====================

    @Nested
    class CompareWithMolangJS {
        @Test
        @DisplayName("Compare interpreter and compiler results with MolangJS expectations")
        void compareWithMolangJS() throws IOException {
            final MochaEngine<?> engine = MochaEngine.createStandard();

            try (BufferedReader source = resourceReader("tests.txt");
                 BufferedReader expectations = resourceReader("expectations.txt")) {
                while (true) {
                    String expression = nextNonEmpty(source);
                    String expected = nextNonEmpty(expectations);
                    if (expression == null || expected == null) break;

                    float expectedValue = Float.parseFloat(expected);

                    // interpreter
                    final double interpreted = engine.eval(expression);
                    assertEquals(expectedValue, (float) interpreted,
                            () -> "INTERPRETED: " + expression);

                    // compiler
                    try {
                        final double compiled = engine.compile(expression).evaluate();
                        assertEquals(expectedValue, (float) compiled,
                                () -> "COMPILED: " + expression);
                    } catch (Throwable e) {
                        throw new IllegalStateException("Error compiling: " + expression, e);
                    }
                }
            }
        }

        private BufferedReader resourceReader(String name) {
            InputStream stream = getClass().getClassLoader().getResourceAsStream(name);
            assertNotNull(stream, "Resource not found: " + name);
            return new BufferedReader(new InputStreamReader(stream));
        }

        private String nextNonEmpty(BufferedReader reader) throws IOException {
            String value;
            do {
                value = reader.readLine();
                if (value == null) break;
                value = value.trim();
            } while (value.isEmpty() || value.charAt(0) == '#');
            return value;
        }
    }

    // ==================== 移植自 mocha: FibonacciTest ====================

    @Nested
    class Fibonacci {
        @Test
        @DisplayName("Fibonacci with loop, variable, temp, return")
        void fibonacci() {
            final String code = "v.x = 0;\n" +
                    "v.y = 1;\n" +
                    "loop(10, {\n" +
                    "    query.log(v.x);\n" +
                    "    t.x = v.x + v.y;\n" +
                    "    v.x = v.y;\n" +
                    "    v.y = t.x;\n" +
                    "});\n" +
                    "return v.y;";

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PrintStream stdout = new PrintStream(out);

            String expected;
            {
                ByteArrayOutputStream expectedOut = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(expectedOut);
                ps.println("0.0");
                ps.println("1.0");
                ps.println("1.0");
                ps.println("2.0");
                ps.println("3.0");
                ps.println("5.0");
                ps.println("8.0");
                ps.println("13.0");
                ps.println("21.0");
                ps.println("34.0");
                expected = expectedOut.toString();
            }

            MochaEngine<?> engine = MochaEngine.createStandard();
            engine.scope().set("query", (ObjectValue) name -> {
                if (name.equalsIgnoreCase("log")) {
                    return ObjectProperty.property((Function<?>) (ctx, args) -> {
                        int i = 0;
                        final StringJoiner joiner = new StringJoiner(" ");
                        while (i++ < args.length()) {
                            joiner.add(args.next().eval().getAsString());
                        }
                        stdout.println(joiner);
                        return NumberValue.zero();
                    }, true);
                }
                return null;
            });
            final double result = engine.eval(code);

            assertEquals(expected, out.toString());
            assertEquals(89D, result);
        }
    }

    // ==================== 移植自 mocha: ConstantValuesTest ====================

    @Nested
    class ConstantValues {
        @Test
        void constantFoldingCompile() {
            // Should not throw — constant expressions should be folded
            MochaEngine.createStandard().compile("math.abs(-5) + math.abs(5) + math.sqrt(25)");
        }
    }

    // ==================== MolangExpression 上下文测试 ====================

    @Nested
    class MolangExpressionTests {
        @Test
        void queryAccess() {
            MolangContext<Object> ctx = new MolangContext<>();
            ctx.setAnimTime(2.5);
            MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);

            MolangExpression expr = MolangEngineHelper.compileExpression(engine, "query.anim_time");
            assertEquals(2.5, expr.evaluate(ctx));

            ctx.setAnimTime(5.0);
            assertEquals(5.0, expr.evaluate(ctx));
        }

        @Test
        void variableReadWrite() {
            MolangContext<Object> ctx = new MolangContext<>();
            MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);

            MolangExpression writeExpr = MolangEngineHelper.compileExpression(engine, "variable.test = 42");
            assertEquals(42.0, writeExpr.evaluate(ctx));

            MolangExpression readExpr = MolangEngineHelper.compileExpression(engine, "variable.test");
            assertEquals(42.0, readExpr.evaluate(ctx));
        }

        @Test
        void tempVariables() {
            MolangContext<Object> ctx = new MolangContext<>();
            MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);

            MolangExpression expr = MolangEngineHelper.compileExpression(engine, "temp.x = 10; temp.x * 2");
            assertEquals(20.0, expr.evaluate(ctx));
        }

        @Test
        void complexWithContext() {
            MolangContext<Object> ctx = new MolangContext<>();
            ctx.setAnimTime(1.0);
            MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);

            MolangExpression expr = MolangEngineHelper.compileExpression(engine,
                    "query.anim_time * 2 + math.abs(-3)");
            assertEquals(5.0, expr.evaluate(ctx));

            ctx.setAnimTime(3.0);
            assertEquals(9.0, expr.evaluate(ctx));
        }
    }

    // ==================== 解释器 vs 编译器一致性 ====================

    @Nested
    class InterpreterCompilerConsistency {
        @Test
        void consistency() {
            MochaEngine<?> engine = MochaEngine.createStandard();
            String[] expressions = {
                    "0", "1 + 2 * 3", "math.sqrt(25)",
                    "math.abs(-7) + math.floor(2.9)",
                    "(5 > 3) ? 10 : 20", "1 && 0 || 1", "!0",
                    "-(3 + 4)", "math.max(math.min(10, 5), 3)",
                    "math.pow(2, 8)",
            };
            for (String expr : expressions) {
                double interpreted = engine.eval(expr);
                MochaFunction compiled = engine.compile(expr);
                assertEquals(interpreted, compiled.evaluate(),
                        "interpreter vs compiler: " + expr);
            }
        }
    }

    // ==================== 解释器回退测试 ====================

    @Nested
    class InterpreterFallback {
        @Test
        void forceInterpreterMochaFunction() {
            MochaEngine<?> engine = MochaEngine.createStandard();
            engine.forceInterpreter(true);

            MochaFunction fn = engine.compile("1 + 2 * 3");
            assertEquals(7.0, fn.evaluate());

            MochaFunction fn2 = engine.compile("math.abs(-5) + math.floor(3.7)");
            assertEquals(8.0, fn2.evaluate());

            MochaFunction fn3 = engine.compile("(3 > 2) ? 100 : 200");
            assertEquals(100.0, fn3.evaluate());
        }

        @Test
        void forceInterpreterMolangExpression() {
            MolangContext<Object> ctx = new MolangContext<>();
            ctx.setAnimTime(2.0);
            MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);
            engine.forceInterpreter(true);

            MolangExpression expr = MolangEngineHelper.compileExpression(engine, "query.anim_time * 3");
            assertEquals(6.0, expr.evaluate(ctx));

            ctx.setAnimTime(4.0);
            assertEquals(12.0, expr.evaluate(ctx));
        }

        @Test
        void forceInterpreterVariableReadWrite() {
            MolangContext<Object> ctx = new MolangContext<>();
            MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);
            engine.forceInterpreter(true);

            MolangExpression writeExpr = MolangEngineHelper.compileExpression(engine, "variable.x = 99");
            assertEquals(99.0, writeExpr.evaluate(ctx));

            MolangExpression readExpr = MolangEngineHelper.compileExpression(engine, "variable.x");
            assertEquals(99.0, readExpr.evaluate(ctx));
        }

        @Test
        void forceInterpreterEmpty() {
            MochaEngine<?> engine = MochaEngine.createStandard();
            engine.forceInterpreter(true);

            MochaFunction fn = engine.compile("");
            assertEquals(0.0, fn.evaluate());
        }

        @Test
        void fallbackConsistencyWithCompiler() {
            // 同一组表达式，编译模式和解释器回退模式结果应一致
            String[] expressions = {
                    "0", "42", "1 + 2 * 3", "math.sqrt(25)",
                    "math.abs(-7) + math.floor(2.9)",
                    "(5 > 3) ? 10 : 20", "1 && 0 || 1", "!0",
                    "-(3 + 4)", "math.pow(2, 8)",
            };

            MochaEngine<?> compiled = MochaEngine.createStandard();
            MochaEngine<?> interpreted = MochaEngine.createStandard();
            interpreted.forceInterpreter(true);

            for (String expr : expressions) {
                double compiledResult = compiled.compile(expr).evaluate();
                double interpretedResult = interpreted.compile(expr).evaluate();
                assertEquals(compiledResult, interpretedResult,
                        "compiled vs fallback: " + expr);
            }
        }

        @Test
        void fallbackConsistencyMolangExpression() {
            String[] expressions = {
                    "query.anim_time",
                    "query.anim_time * 2 + 1",
                    "math.sin(query.anim_time * 360)",
            };

            for (String expr : expressions) {
                MolangContext<Object> ctx1 = new MolangContext<>();
                ctx1.setAnimTime(1.5);
                MochaEngine<?> compiledEngine = MolangEngineHelper.createEngine(ctx1);
                MolangExpression compiledExpr = MolangEngineHelper.compileExpression(compiledEngine, expr);

                MolangContext<Object> ctx2 = new MolangContext<>();
                ctx2.setAnimTime(1.5);
                MochaEngine<?> interpEngine = MolangEngineHelper.createEngine(ctx2);
                interpEngine.forceInterpreter(true);
                MolangExpression interpExpr = MolangEngineHelper.compileExpression(interpEngine, expr);

                assertEquals(compiledExpr.evaluate(ctx1), interpExpr.evaluate(ctx2),
                        "compiled vs fallback MolangExpression: " + expr);
            }
        }
    }
}
