package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo;

import it.unimi.dsi.fastutil.doubles.Double2ObjectRBTreeMap;

import java.util.List;

/**
 * 基岩版动画的 {@code timeline} 时间轴事件关键帧。
 * <p>
 * 标准格式为「时间 -&gt; 字符串 或 字符串数组」，每个字符串是一条指令（Molang 表达式 / 斜杠命令 / 实体事件）。
 * 同一时间点可挂多条，因此每个关键帧的值统一存为字符串列表。
 * <pre>
 * "timeline": {
 *   "0.45": "action.notify('mag_out')",
 *   "3.0": [ "variable.a = 0;", "variable.b = 1;" ]
 * }
 * </pre>
 */
public class TimelineKeyframes {
    private final Double2ObjectRBTreeMap<List<String>> keyframes;

    public TimelineKeyframes(Double2ObjectRBTreeMap<List<String>> keyframes) {
        this.keyframes = keyframes;
    }

    public Double2ObjectRBTreeMap<List<String>> getKeyframes() {
        return keyframes;
    }
}
