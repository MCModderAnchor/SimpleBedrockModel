package com.github.mcmodderanchor.simplebedrockmodel.v2.client.world;

/** 提交网格时明确选择顶点 UV2 的含义，与库内部的光照缓冲布局无关。 */
public enum MeshLighting {
    /** UV2 为零的顶点跟随实例光照，非零顶点保留烘焙值（例如自发光）。 */
    INSTANCE,
    /** 实际 UV2 存入独立流，允许通过 ShardHandle.updateLight 更新顶点范围；忽略实例光照。 */
    MUTABLE;

    public static ShardHandle.LightMode classify(MeshSink mesh, MeshLighting requested) {
        if (requested == MUTABLE) return ShardHandle.LightMode.MUTABLE;
        boolean followsInstance = false;
        boolean fixed = false;
        for (int i = 0, count = mesh.lightRunCount(); i < count; i++) {
            if (mesh.lightRunValue(i) == 0) followsInstance = true;
            else fixed = true;
        }
        if (followsInstance && fixed) {
            throw new IllegalArgumentException("Mixed instance and fixed UV2 in one pass; "
                    + "use GeometryCollector.buffer(ordinary, emissive, mode)");
        }
        return followsInstance ? ShardHandle.LightMode.UNIFORM : ShardHandle.LightMode.FIXED;
    }
}
