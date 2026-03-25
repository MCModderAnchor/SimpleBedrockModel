package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.event;

import java.util.List;

/**
 * 加权随机事件节点。按权重随机选择一个子事件执行。
 *
 * @param entries 加权事件条目列表
 */
public record EventRandomize(List<WeightedEntry> entries) implements IEventNode {

    /**
     * 加权事件条目。
     *
     * @param weight 权重
     * @param node   事件节点
     */
    public record WeightedEntry(float weight, IEventNode node) {}
}
