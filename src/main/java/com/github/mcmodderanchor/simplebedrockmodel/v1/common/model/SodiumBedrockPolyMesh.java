package com.github.mcmodderanchor.simplebedrockmodel.v1.common.model;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.PolyMeshItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public class SodiumBedrockPolyMesh extends BedrockPolyMesh implements ISodiumVertexWriter {
    public SodiumBedrockPolyMesh(PolyMeshItem polyMesh, BedrockBone part, float texWidth, float texHeight) {
        super(polyMesh, part, texWidth, texHeight);
    }

    @Override
    public void compileTriangles(PoseStack.Pose pose, VertexConsumer consumer, int lightmap, int overlay, float red, float green, float blue, float alpha) {
        // ModelVertex 与 polymesh 三角形缓冲格式不兼容，直接实例化此类也必须使用普通顶点写入。
        super.compileTriangles(pose, consumer, lightmap, overlay, red, green, blue, alpha);
    }
}
