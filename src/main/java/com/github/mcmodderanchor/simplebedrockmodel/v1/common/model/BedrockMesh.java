package com.github.mcmodderanchor.simplebedrockmodel.v1.common.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public interface BedrockMesh {
    void compileTriangles(PoseStack.Pose pose, VertexConsumer consumer, int lightmap, int overlay,
                          float red, float green, float blue, float alpha);

    float width();

    float height();

    float depth();

    float x();

    float y();

    float z();
}
