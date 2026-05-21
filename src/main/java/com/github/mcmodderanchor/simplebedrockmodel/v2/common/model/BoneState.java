package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model;

import com.maydaymemory.mae.basic.BoneTransform;
import com.maydaymemory.mae.basic.ZYXRotationView;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class BoneState {
    private final BoneDefinition definition;
    public float x;
    public float y;
    public float z;
    public final Quaternionf rotation = new Quaternionf();
    public final Vector3f rotationInEuler = new Vector3f();
    public float xScale = 1;
    public float yScale = 1;
    public float zScale = 1;
    public boolean visible = true;
    public boolean illuminated = false;

    BoneState(BoneDefinition definition) {
        this.definition = definition;
        reset();
    }

    public BoneDefinition definition() {
        return definition;
    }

    public String name() {
        return definition.name();
    }

    public int index() {
        return definition.index();
    }

    public int parentIndex() {
        return definition.parentIndex();
    }

    public int[] children() {
        return definition.children();
    }

    public void reset() {
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.rotation.identity();
        this.rotationInEuler.zero();
        this.xScale = 1;
        this.yScale = 1;
        this.zScale = 1;
        this.visible = true;
        this.illuminated = false;
    }

    public void translateAndRotateAndScale(PoseStack poseStack) {
        Matrix4f bindLocalTransform = definition.bindLocalTransform();
        if (bindLocalTransform != null) {
            poseStack.last().pose().mul(bindLocalTransform);
            poseStack.last().normal().mul(definition.bindLocalNormalTransform());
        }
        applyAnimationDelta(poseStack);
    }

    public Matrix4f getLocalTransform() {
        Matrix4f bindLocalTransform = definition.bindLocalTransform();
        Matrix4f matrix = bindLocalTransform == null ? new Matrix4f() : new Matrix4f(bindLocalTransform);
        return matrix.mul(getAnimationDeltaTransform());
    }

    private void applyAnimationDelta(PoseStack poseStack) {
        if (x != 0 || y != 0 || z != 0) {
            poseStack.translate(x / 16.0F, y / 16.0F, z / 16.0F);
        }
        poseStack.translate(definition.pivotX(), definition.pivotY(), definition.pivotZ());
        poseStack.last().pose().rotate(rotation);
        poseStack.last().normal().rotate(rotation);
        poseStack.scale(xScale, yScale, zScale);
        poseStack.translate(-definition.pivotX(), -definition.pivotY(), -definition.pivotZ());
    }

    private Matrix4f getAnimationDeltaTransform() {
        Matrix4f matrix = new Matrix4f();
        if (x != 0 || y != 0 || z != 0) {
            matrix.translate(x / 16.0F, y / 16.0F, z / 16.0F);
        }
        matrix.translate(definition.pivotX(), definition.pivotY(), definition.pivotZ());
        matrix.rotate(rotation);
        matrix.scale(xScale, yScale, zScale);
        matrix.translate(-definition.pivotX(), -definition.pivotY(), -definition.pivotZ());
        return matrix;
    }

    public Matrix4f getBindLocalTransform() {
        Matrix4f bindLocalTransform = definition.bindLocalTransform();
        return bindLocalTransform == null ? new Matrix4f() : new Matrix4f(bindLocalTransform);
    }

    public Matrix4f getGlobalTransform(BedrockModelInstance instance) {
        return instance.getGlobalTransform(index());
    }

    public BoneTransform getBoneTransform() {
        return new BoneTransform(index(), new Vector3f(x, y, z), new ZYXRotationView(rotation), new Vector3f(xScale, yScale, zScale));
    }
}
