package com.github.mcmodderanchor.simplebedrockmodel.v1.molang;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.molang.MolangEngineHelper;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MochaFunction;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangContext;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.binding.QueryBinding;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MoLang ASM 编译器测试。
 * 覆盖：算术、逻辑、数学、参数化编译、常量折叠、
 * MolangExpression 上下文、@QueryBinding 编译。
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

        @Test
        @DisplayName("&& 短路求值：左侧为 false 时不执行右侧")
        void andShortCircuit() {
            final MochaEngine<?> engine = MochaEngine.createStandard();
            final AtomicInteger sideEffectCount = new AtomicInteger(0);
            engine.scope().set("side_effect", (ObjectValue) name -> {
                if (name.equalsIgnoreCase("call")) {
                    return ObjectProperty.property((Function<?>) (ctx, args) -> {
                        sideEffectCount.incrementAndGet();
                        return NumberValue.of(1);
                    }, true);
                }
                return null;
            });

            // 0 && X → 左侧 false，X 不应被求值
            engine.eval("0 && side_effect.call()");
            assertEquals(0, sideEffectCount.get(), "短路求值失败：左侧=false 时不应求值右侧");
        }

        @Test
        @DisplayName("|| 短路求值：左侧为 true 时不执行右侧")
        void orShortCircuit() {
            final MochaEngine<?> engine = MochaEngine.createStandard();
            final AtomicInteger sideEffectCount = new AtomicInteger(0);
            engine.scope().set("side_effect", (ObjectValue) name -> {
                if (name.equalsIgnoreCase("call")) {
                    return ObjectProperty.property((Function<?>) (ctx, args) -> {
                        sideEffectCount.incrementAndGet();
                        return NumberValue.of(1);
                    }, true);
                }
                return null;
            });

            // 1 || X → 左侧 true，X 不应被求值
            engine.eval("1 || side_effect.call()");
            assertEquals(0, sideEffectCount.get(), "短路求值失败：左侧=true 时不应求值右侧");
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

        /** 嵌套三元运算符 */
        @Test
        @DisplayName("嵌套三元: a < 0 ? -1 : (a > 0 ? 1 : 0)")
        void nestedTernary() {
            final MochaEngine<?> engine = MochaEngine.createStandard();

            // 对参数化版本用 @Named 参数
            final NestedTernaryFn fn = engine.compile("a < 0 ? -1 : (a > 0 ? 1 : 0)", NestedTernaryFn.class);
            assertEquals(-1.0, fn.apply(-5));
            assertEquals(1.0, fn.apply(5));
            assertEquals(0.0, fn.apply(0));
        }

        public interface ScriptType extends MochaCompiledFunction {
            int eval(@Named("a") double a, @Named("b") double b);
        }

        public interface NestedTernaryFn extends MochaCompiledFunction {
            double apply(@Named("a") double a);
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
            ctx.prepareEvaluation(2.5f);
            MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);

            MolangExpression expr = MolangEngineHelper.compileExpression(engine, "query.anim_time");
            assertEquals(2.5, expr.evaluate(ctx));

            ctx.prepareEvaluation(5.0f);
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
            ctx.prepareEvaluation(1.0f);
            MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);

            MolangExpression expr = MolangEngineHelper.compileExpression(engine,
                    "query.anim_time * 2 + math.abs(-3)");
            assertEquals(5.0, expr.evaluate(ctx));

            ctx.prepareEvaluation(3.0f);
            assertEquals(9.0, expr.evaluate(ctx));
        }

        @Test
        @DisplayName("同一表达式用于不同 Context 类型")
        @SuppressWarnings("unchecked")
        void crossContextTypeReuse() {
            // 编译时使用基类 MolangContext — 只含 query.anim_time
            MochaEngine<?> engine = MolangEngineHelper.createEngine(
                    (Class<? extends MolangContext<?>>) (Class<?>) MolangContext.class);
            MolangExpression expr = MolangEngineHelper.compileExpression(engine, "query.anim_time * 2");

            // 求值时使用子类 TestEntityContext — 继承的 anim_time 仍然可用
            TestEntityContext ctx = new TestEntityContext();
            ctx.prepareEvaluation(3.0f);
            assertEquals(6.0, expr.evaluate(ctx));
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

    // ==================== @QueryBinding 注解编译测试 ====================

    /** 带自定义 @QueryBinding 的 context 子类 */
    public static class TestEntityContext extends MolangContext<TestEntityContext> {
        private double health = 100.0;
        private boolean grounded = true;

        public void setHealth(double health) { this.health = health; }
        public void setGrounded(boolean grounded) { this.grounded = grounded; }

        @QueryBinding("health")
        public double queryHealth() { return health; }

        @QueryBinding("is_grounded")
        public double queryIsGrounded() { return grounded ? 1.0 : 0.0; }

        @QueryBinding("constant_val")
        public double queryConstant() { return 42.0; }
    }

    /** 带自定义命名空间的 Context 子类 */
    public static class CustomNamespaceContext extends MolangContext<CustomNamespaceContext> {
        @QueryBinding(value = "is_jumping", namespace = "input")
        public double inputIsJumping() { return 1.0; }

        @QueryBinding(value = "score", namespace = "scoreboard")
        public double scoreboardScore() { return 99.0;
        }
    }

    /** 继承 TestEntityContext 的子类 */
    public static class ExtendedContext extends TestEntityContext {
        private double armor = 10.0;

        @QueryBinding("armor")
        public double queryArmor() { return armor; }

        public void setArmor(double armor) { this.armor = armor; }
    }

    @Nested
    class QueryBindingCompiled {
        @Test
        void customQueryBinding() {
            TestEntityContext ctx = new TestEntityContext();
            MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);
            MolangExpression expr = MolangEngineHelper.compileExpression(engine, "query.health");

            assertEquals(100.0, expr.evaluate(ctx));

            ctx.setHealth(50.0);
            assertEquals(50.0, expr.evaluate(ctx));
        }

        @Test
        void booleanStyleQuery() {
            TestEntityContext ctx = new TestEntityContext();
            MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);
            MolangExpression expr = MolangEngineHelper.compileExpression(engine, "query.is_grounded");

            assertEquals(1.0, expr.evaluate(ctx));

            ctx.setGrounded(false);
            assertEquals(0.0, expr.evaluate(ctx));
        }

        @Test
        void complexExpression() {
            TestEntityContext ctx = new TestEntityContext();
            MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);
            MolangExpression expr = MolangEngineHelper.compileExpression(engine,
                    "query.health * 2 + query.constant_val + query.anim_time");

            ctx.setHealth(10.0);
            ctx.prepareEvaluation(3.0f);
            assertEquals(65.0, expr.evaluate(ctx));
        }

        @Test
        void unannotatedPropertyReturnsZero() {
            TestEntityContext ctx = new TestEntityContext();
            MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);
            MolangExpression expr = MolangEngineHelper.compileExpression(engine, "query.unknown_property");
            assertEquals(0.0, expr.evaluate(ctx));
        }

        @Test
        void crossContextReuse() {
            MochaEngine<?> engine = MolangEngineHelper.createEngine(new MolangContext<>());
            MolangExpression expr = MolangEngineHelper.compileExpression(engine, "query.anim_time * 2");

            TestEntityContext ctx1 = new TestEntityContext();
            ctx1.prepareEvaluation(3.0f);
            assertEquals(6.0, expr.evaluate(ctx1));

            TestEntityContext ctx2 = new TestEntityContext();
            ctx2.prepareEvaluation(5.0f);
            assertEquals(10.0, expr.evaluate(ctx2));
        }

        @Test
        void inheritedAnimTime() {
            TestEntityContext ctx = new TestEntityContext();
            MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);
            MolangExpression expr = MolangEngineHelper.compileExpression(engine, "query.anim_time");

            ctx.prepareEvaluation(2.5f);
            assertEquals(2.5, expr.evaluate(ctx));
        }

        @Test
        void variableStillWorks() {
            TestEntityContext ctx = new TestEntityContext();
            MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);

            MolangExpression write = MolangEngineHelper.compileExpression(engine, "variable.x = 77");
            assertEquals(77.0, write.evaluate(ctx));

            MolangExpression read = MolangEngineHelper.compileExpression(engine, "variable.x");
            assertEquals(77.0, read.evaluate(ctx));
        }

        @Test
        @DisplayName("自定义命名空间 @QueryBinding(namespace=\"input\")")
        void customNamespace() {
            CustomNamespaceContext ctx = new CustomNamespaceContext();
            MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);

            MolangExpression expr = MolangEngineHelper.compileExpression(engine, "input.is_jumping");
            assertEquals(1.0, expr.evaluate(ctx));
        }

        @Test
        @DisplayName("多个自定义命名空间 @QueryBinding")
        void multipleCustomNamespaces() {
            CustomNamespaceContext ctx = new CustomNamespaceContext();
            MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);

            MolangExpression jumpingExpr = MolangEngineHelper.compileExpression(engine, "input.is_jumping");
            assertEquals(1.0, jumpingExpr.evaluate(ctx));

            MolangExpression scoreExpr = MolangEngineHelper.compileExpression(engine, "scoreboard.score");
            assertEquals(99.0, scoreExpr.evaluate(ctx));
        }

        @Test
        @DisplayName("子类继承父类的 @QueryBinding 属性")
        void inheritedQueryBinding() {
            ExtendedContext ctx = new ExtendedContext();
            MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);

            // 继承自 TestEntityContext
            MolangExpression healthExpr = MolangEngineHelper.compileExpression(engine, "query.health");
            assertEquals(100.0, healthExpr.evaluate(ctx));

            // 子类新增
            MolangExpression armorExpr = MolangEngineHelper.compileExpression(engine, "query.armor");
            assertEquals(10.0, armorExpr.evaluate(ctx));

            ctx.setArmor(5.0);
            assertEquals(5.0, armorExpr.evaluate(ctx));
        }
    }

    // ==================== @QueryBinding 带参方法测试 ====================

    /** 带参数的 @QueryBinding Context */
    public static class ParamQueryContext extends MolangContext<ParamQueryContext> {
        private double multiplier = 2.0;

        @QueryBinding(value = "scaled", namespace = "custom")
        public double scaled(@Named("x") double x) {
            return x * multiplier;
        }

        @QueryBinding(value = "distance", namespace = "custom")
        public double distance(@Named("x") double x, @Named("y") double y) {
            return Math.sqrt(x * x + y * y);
        }

        public void setMultiplier(double v) { this.multiplier = v; }
    }

    @Nested
    class QueryBindingWithParams {
        @Test
        @DisplayName("单参数 @QueryBinding 方法调用")
        void singleParam() {
            ParamQueryContext ctx = new ParamQueryContext();
            MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);
            MolangExpression expr = MolangEngineHelper.compileExpression(engine, "custom.scaled(10)");
            assertEquals(20.0, expr.evaluate(ctx));
        }

        @Test
        @DisplayName("多参数 @QueryBinding 方法调用")
        void multiParam() {
            ParamQueryContext ctx = new ParamQueryContext();
            MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);
            MolangExpression expr = MolangEngineHelper.compileExpression(engine, "custom.distance(3, 4)");
            assertEquals(5.0, expr.evaluate(ctx));
        }

        @Test
        @DisplayName("context 状态变化影响带参方法结果")
        void stateAffectsResult() {
            ParamQueryContext ctx = new ParamQueryContext();
            MochaEngine<?> engine = MolangEngineHelper.createEngine(ctx);
            MolangExpression expr = MolangEngineHelper.compileExpression(engine, "custom.scaled(5)");

            assertEquals(10.0, expr.evaluate(ctx));
            ctx.setMultiplier(3.0);
            assertEquals(15.0, expr.evaluate(ctx));
        }
    }

    // ==================== 编译错误传播测试 ====================

    /** 非接口类型：抽象类实现 MochaCompiledFunction */
    abstract static class NotAnInterface implements MochaCompiledFunction {
        public abstract double eval();
    }

    /** 多方法接口 */
    interface MultiMethod extends MochaCompiledFunction {
        double eval();
        int another();
    }

    /** 无抽象方法的接口（仅 default 方法） */
    interface NoAbstractMethod extends MochaCompiledFunction {
        default double eval() { return 0; }
    }

    @Nested
    class CompileErrorPropagation {
        @Test
        @DisplayName("非接口目标类型应抛 IllegalArgumentException")
        void nonInterfaceTargetType() {
            MochaEngine<?> engine = MochaEngine.createStandard();
            assertThrows(IllegalArgumentException.class,
                    () -> engine.compile("1 + 1", NotAnInterface.class),
                    "Target type must be an interface");
        }

        @Test
        @DisplayName("多方法接口应抛 IllegalArgumentException")
        void multiMethodInterface() {
            MochaEngine<?> engine = MochaEngine.createStandard();
            assertThrows(IllegalArgumentException.class,
                    () -> engine.compile("1 + 1", MultiMethod.class),
                    "Target type must have only one method");
        }

        @Test
        @DisplayName("无抽象方法接口应抛 IllegalArgumentException")
        void noMethodInterface() {
            MochaEngine<?> engine = MochaEngine.createStandard();
            assertThrows(IllegalArgumentException.class,
                    () -> engine.compile("1 + 1", NoAbstractMethod.class),
                    "Target type must have a method to implement");
        }
    }

    // ==================== postCompile 回调测试 ====================

    @Nested
    class PostCompileCallback {
        @Test
        @DisplayName("postCompile 回调收到字节码并仍能正常求值")
        void postCompileReceivesBytecode() {
            MochaEngine<?> engine = MochaEngine.createStandard();
            AtomicReference<byte[]> captured = new AtomicReference<>();
            engine.postCompile(captured::set);

            MochaFunction fn = engine.compile("42 + 1");
            assertNotNull(captured.get(), "postCompile 应收到字节码");
            assertTrue(captured.get().length > 0, "字节码不应为空");
            assertEquals(43.0, fn.evaluate());
        }
    }
}
