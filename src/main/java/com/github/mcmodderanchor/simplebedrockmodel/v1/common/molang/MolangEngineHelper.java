package com.github.mcmodderanchor.simplebedrockmodel.v1.common.molang;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.MochaEngine;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangContext;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.binding.JavaObjectBinding;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.standard.MochaMath;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.value.MutableObjectBinding;

/**
 * 创建和管理 MolangEngine 的工具类。
 */
public final class MolangEngineHelper {
    private MolangEngineHelper() {}

    /**
     * 创建一个已绑定 {@link MolangContext} 的 MochaEngine。
     * 自动配置 math、query、variable 命名空间，返回即可用于编译和求值。
     *
     * @param context 要绑定的 Molang 上下文
     * @return 配置完毕的引擎
     */
    public static MochaEngine<?> createEngine(MolangContext<?> context) {
        MochaEngine<?> engine = createEngine();
        registerQueryBinding(engine, context.getQueryBinding());
        bindContext(engine, context);
        return engine;
    }

    /**
     * 创建一个标准的 MochaEngine，预配置 math 绑定。
     * query / variable 命名空间留空，由使用者通过 {@link #registerQueryBinding} 和 {@link #bindContext} 绑定。
     */
    public static MochaEngine<?> createEngine() {
        return MochaEngine.create(null, builder -> {
            builder.set("math", JavaObjectBinding.of(MochaMath.class, null, new MochaMath()));
        });
    }

    /**
     * 将 query binding 注册到 engine scope 上。
     * 通常在 engine 创建后、编译前调用一次，确保编译器能静态绑定 query 属性。
     */
    public static void registerQueryBinding(MochaEngine<?> engine, DynamicQueryBinding queryBinding) {
        engine.scope().set("query", queryBinding);
        engine.scope().set("q", queryBinding);
    }

    /**
     * 在求值前调用，将 MolangContext 的 variable 存储绑定到 engine scope。
     * 这样每个 context 的 variable 命名空间是独立的。
     */
    public static void bindContext(MochaEngine<?> engine, MolangContext<?> context) {
        MutableObjectBinding vars = context.getVariableStorage();
        engine.scope().set("variable", vars);
        engine.scope().set("v", vars);
    }

    /**
     * 编译 Molang 表达式为 {@link MolangExpression}。
     * 编译后的表达式可以对不同 {@link MolangContext} 复用。
     *
     * @param engine     已配置好 scope（含 query binding 结构）的引擎
     * @param expression Molang 表达式字符串
     * @return 编译后的表达式
     */
    public static MolangExpression compileExpression(MochaEngine<?> engine, String expression) {
        return engine.compile(expression, MolangExpression.class);
    }
}
