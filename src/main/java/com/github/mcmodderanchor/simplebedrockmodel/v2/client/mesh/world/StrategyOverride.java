package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world;

/** 客户端配置中的策略选择；AUTO 使用渲染组默认值。 */
public enum StrategyOverride {
    AUTO, INSTANCE, SECTION;

    public WorldMeshStrategy strategy() {
        return this == AUTO ? null : WorldMeshStrategy.valueOf(name());
    }
}
