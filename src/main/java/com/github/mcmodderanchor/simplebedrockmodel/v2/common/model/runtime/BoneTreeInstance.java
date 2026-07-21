package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime;

import com.maydaymemory.mae.basic.ArrayPoseBuilder;
import com.maydaymemory.mae.basic.BoneTransform;
import com.maydaymemory.mae.basic.Pose;
import com.maydaymemory.mae.basic.PoseBuilder;
import com.maydaymemory.mae.basic.Skeleton;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public abstract class BoneTreeInstance implements Skeleton {
    private final BoneState[] bones;
    private final Pose bindPose;

    protected BoneTreeInstance(BoneDefinition[] definitions, Pose bindPose) {
        this.bindPose = bindPose;
        this.bones = new BoneState[definitions.length];
        for (int i = 0; i < bones.length; i++) {
            bones[i] = new BoneState(definitions[i]);
        }
    }

    @Nullable
    public BoneState getBone(String name) {
        int index = getIndex(name);
        return index >= 0 ? bones[index] : null;
    }

    @Nullable
    public BoneState getBone(int index) {
        return index >= 0 && index < bones.length ? bones[index] : null;
    }

    public BoneState[] getBoneIndexes() {
        return bones.clone();
    }

    public int boneCount() {
        return bones.length;
    }

    public void resetPose() {
        for (BoneState bone : bones) {
            bone.reset();
        }
    }

    public Matrix4f getGlobalTransform(int index) {
        if (index < 0 || index >= bones.length) return new Matrix4f();
        ArrayList<BoneState> chain = new ArrayList<>();
        BoneState bone = bones[index];
        while (bone != null) {
            chain.add(bone);
            int parentIndex = bone.parentIndex();
            bone = parentIndex >= 0 && parentIndex < bones.length ? bones[parentIndex] : null;
        }
        Matrix4f matrix = new Matrix4f();
        for (int i = chain.size() - 1; i >= 0; i--) {
            matrix.mul(chain.get(i).getLocalTransform());
        }
        return matrix;
    }

    @Override
    public Collection<Integer> getChildren(int i) {
        BoneState bone = getBone(i);
        if (bone == null) return List.of();
        int[] children = bone.children();
        ArrayList<Integer> result = new ArrayList<>(children.length);
        for (int child : children) {
            result.add(child);
        }
        return result;
    }

    @Override
    public int getFather(int i) {
        BoneState bone = getBone(i);
        return bone == null ? -1 : bone.parentIndex();
    }

    @Override
    public void applyPose(Pose pose) {
        for (BoneTransform boneTransform : pose.getBoneTransforms()) {
            BoneState bone = getBone(boneTransform.boneIndex());
            if (bone == null) continue;
            Vector3fc translation = boneTransform.translation();
            Quaternionf rotation = new Quaternionf(boneTransform.rotation().asQuaternion());
            Vector3fc eulerRotation = boneTransform.rotation().asEulerAngle();
            Vector3fc scale = boneTransform.scale();
            bone.x = translation.x();
            bone.y = translation.y();
            bone.z = translation.z();
            bone.rotation.set(rotation);
            bone.rotationInEuler.set(eulerRotation);
            bone.xScale = scale.x();
            bone.yScale = scale.y();
            bone.zScale = scale.z();
        }
    }

    @Override
    public Pose getPose() {
        PoseBuilder poseBuilder = new ArrayPoseBuilder();
        for (BoneState bone : bones) {
            poseBuilder.addBoneTransform(bone.getBoneTransform());
        }
        return poseBuilder.toPose();
    }

    @Override
    public Pose getBindPose() {
        return bindPose;
    }

    /**
     * Traces the closed world-space line segment against the current cube pose and returns its nearest hit.
     */
    @Nullable
    public final ModelRayTraceResult rayTrace(Matrix4fc modelRotation, Vec3 modelOrigin, Vec3 rayStart, Vec3 rayEnd) {
        Objects.requireNonNull(modelRotation, "modelRotation");
        Objects.requireNonNull(modelOrigin, "modelOrigin");
        Objects.requireNonNull(rayStart, "rayStart");
        Objects.requireNonNull(rayEnd, "rayEnd");
        ModelRayTracer tracer = new ModelRayTracer(modelRotation, modelOrigin, rayStart, rayEnd);
        if (!tracer.isValid()) {
            return null;
        }
        rayTraceCubes(tracer);
        return tracer.result();
    }

    protected abstract void rayTraceCubes(ModelRayTracer tracer);

    public abstract int getIndex(String boneName);
}
