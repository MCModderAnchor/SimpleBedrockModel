package com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.embeddium;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockBone;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockPolyMesh;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.PolyMeshItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public class EmbeddiumBedrockPolyMesh extends BedrockPolyMesh implements IEmbeddiumVertexWriter {
    public EmbeddiumBedrockPolyMesh(PolyMeshItem polyMesh, BedrockBone part, float texWidth, float texHeight) {
        super(polyMesh, part, texWidth, texHeight);
    }

    @Override
    public void compileTriangles(PoseStack.Pose pose, VertexConsumer consumer, int lightmap, int overlay, float red, float green, float blue, float alpha) {
        // Direct use of this compatibility class must also avoid the triangle fast path.
        super.compileTriangles(pose, consumer, lightmap, overlay, red, green, blue, alpha);
    }
}
