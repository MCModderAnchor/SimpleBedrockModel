package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public interface BoneDefinition {
    String name();

    int index();

    int parentIndex();

    int[] children();

    float pivotX();

    float pivotY();

    float pivotZ();

    float bindX();

    float bindY();

    float bindZ();

    Quaternionf bindRotation();

    Vector3f bindEulerRotation();

    @Nullable
    default Matrix4f bindLocalTransform() {
        return null;
    }

    @Nullable
    default Matrix3f bindLocalNormalTransform() {
        return null;
    }

    @Nullable
    default Matrix4f foldedParentTransform() {
        return null;
    }

    @Nullable
    default Matrix3f foldedParentNormalTransform() {
        return null;
    }

    default boolean rotateAroundPivot() {
        return true;
    }
}
