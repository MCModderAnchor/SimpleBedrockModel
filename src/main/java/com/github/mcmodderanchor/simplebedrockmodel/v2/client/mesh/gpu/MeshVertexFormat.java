package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.MeshSink;

import com.mojang.blaze3d.vertex.VertexFormat;

/** 探测渲染兼容钩子提供的实际 BufferBuilder 顶点布局。 */
public class MeshVertexFormat {
    public MeshVertexFormat() {}
    /** 用最小代价问一次"当前生效的顶点格式"：构造 1 个 quad 再丢弃，不碰 GL。 */
    public static VertexFormat probe() {
        MeshSink probe = new MeshSink().begin(VertexFormat.Mode.QUADS);
        for (int i = 0; i < 4; i++) {
            probe.addVertex(0.0F, 0.0F, 0.0F, -1, 0.0F, 0.0F, 0, 0, 0.0F, 1.0F, 0.0F);
        }
        try (MeshUpload.Payload rendered = probe.buildBuffer()) {
            return rendered.data().drawState().format();
        }
    }

}
