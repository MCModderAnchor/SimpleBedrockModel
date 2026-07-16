package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked;

import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BoneDefinition;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record BakedBoneDefinition(
        String name,
        int index,
        int parentIndex,
        int[] children,
        float pivotX,
        float pivotY,
        float pivotZ,
        float bindX,
        float bindY,
        float bindZ,
        @Nullable Matrix4f bindLocalTransform,
        @Nullable Matrix3f bindLocalNormalTransform,
        @Nullable Matrix4f foldedParentTransform,
        @Nullable Matrix3f foldedParentNormalTransform,
        Quaternionf bindRotation,
        Vector3f bindEulerRotation,
        boolean hasQuadsInTree,
        boolean hasVerticesInTree
) implements BoneDefinition {
    public BakedBoneDefinition {
        bindLocalTransform = bindLocalTransform == null ? null : new Matrix4f(bindLocalTransform);
        bindLocalNormalTransform = bindLocalNormalTransform == null ? null : new Matrix3f(bindLocalNormalTransform);
        foldedParentTransform = foldedParentTransform == null ? null : new Matrix4f(foldedParentTransform);
        foldedParentNormalTransform = foldedParentNormalTransform == null ? null : new Matrix3f(foldedParentNormalTransform);
        bindRotation = new Quaternionf(bindRotation);
        bindEulerRotation = new Vector3f(bindEulerRotation);
        children = children.clone();
    }

    public boolean hasBindLocalTransform() {
        return bindLocalTransform != null;
    }
}
