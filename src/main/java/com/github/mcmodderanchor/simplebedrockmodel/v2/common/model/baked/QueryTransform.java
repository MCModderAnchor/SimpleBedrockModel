package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked;

import org.joml.Matrix4f;

public record QueryTransform(
        String name,
        int attachBoneIndex,
        Matrix4f localTransform
) {
    public QueryTransform {
        localTransform = new Matrix4f(localTransform);
    }
}
