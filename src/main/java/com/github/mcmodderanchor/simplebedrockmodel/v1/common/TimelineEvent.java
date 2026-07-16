package com.github.mcmodderanchor.simplebedrockmodel.v1.common;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangContext;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;

import java.util.List;

/**
 * 编译后的时间轴事件：某一时刻要执行的一组指令（Molang 表达式）。
 * <p>
 * 同时保留原始字符串（供调试 / 日志 / 编辑器回显）与编译后的 {@link MolangExpression}（供运行时直接求值）。
 * 编译后的表达式对同类型的不同 {@link MolangContext} 实例可复用，因此可在动画编译期一次性编好。
 */
public class TimelineEvent {
    private final List<String> raw;
    private final List<MolangExpression> compiled;

    public TimelineEvent(List<String> raw, List<MolangExpression> compiled) {
        this.raw = raw;
        this.compiled = compiled;
    }

    /**
     * 原始指令字符串列表（未编译），供调试 / 日志使用。
     */
    public List<String> getRaw() {
        return raw;
    }

    /**
     * 编译后的表达式列表，供运行时按上下文求值。
     */
    public List<MolangExpression> getCompiled() {
        return compiled;
    }

    /**
     * 在给定上下文中执行本事件的所有指令（按声明顺序）。
     */
    public void execute(MolangContext<?> context) {
        for (MolangExpression expression : compiled) {
            expression.evaluate(context);
        }
    }
}
