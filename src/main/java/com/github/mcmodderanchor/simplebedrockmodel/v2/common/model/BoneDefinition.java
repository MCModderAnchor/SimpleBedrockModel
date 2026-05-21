package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record BoneDefinition(
        String name,
        int index,
        int parentIndex,
        int[] children,
        float pivotX,
        float pivotY,
        float pivotZ,
        @Nullable Matrix4f bindLocalTransform,
        @Nullable Matrix3f bindLocalNormalTransform,
        Quaternionf bindRotation,
        Vector3f bindEulerRotation,
        float bindXScale,
        float bindYScale,
        float bindZScale
) {
    public BoneDefinition {
        bindLocalTransform = bindLocalTransform == null ? null : new Matrix4f(bindLocalTransform);
        bindLocalNormalTransform = bindLocalNormalTransform == null ? null : new Matrix3f(bindLocalNormalTransform);
        bindRotation = new Quaternionf(bindRotation);
        bindEulerRotation = new Vector3f(bindEulerRotation);
        children = children.clone();
    }

    public boolean hasBindLocalTransform() {
        return bindLocalTransform != null;
    }
}
