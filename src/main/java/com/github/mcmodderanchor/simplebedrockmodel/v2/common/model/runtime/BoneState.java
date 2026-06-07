package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime;

import com.maydaymemory.mae.basic.BoneTransform;
import com.maydaymemory.mae.basic.RotationView;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3f;
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
        this.x = definition.bindX();
        this.y = definition.bindY();
        this.z = definition.bindZ();
        this.rotation.set(definition.bindRotation());
        this.rotationInEuler.set(definition.bindEulerRotation());
        this.xScale = 1.0f;
        this.yScale = 1.0f;
        this.zScale = 1.0f;
        this.visible = true;
        this.illuminated = false;
    }

    public void translateAndRotateAndScale(PoseStack poseStack) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        Matrix4f foldedParentTransform = definition.foldedParentTransform();
        if (foldedParentTransform != null) {
            pose.mul(foldedParentTransform);
            Matrix3f foldedParentNormalTransform = definition.foldedParentNormalTransform();
            normal.mul(foldedParentNormalTransform == null ? new Matrix3f(foldedParentTransform) : foldedParentNormalTransform);
        }
        applyCurrentSelfTransform(pose, normal);
    }

    public Matrix4f getLocalTransform() {
        Matrix4f matrix = new Matrix4f();
        Matrix4f foldedParentTransform = definition.foldedParentTransform();
        if (foldedParentTransform != null) {
            matrix.mul(foldedParentTransform);
        }
        return applyCurrentSelfTransform(matrix);
    }

    private Matrix4f applyCurrentSelfTransform(Matrix4f matrix) {
        if (x != 0 || y != 0 || z != 0) {
            matrix.translate(x / 16.0F, y / 16.0F, z / 16.0F);
        }
        if (definition.rotateAroundPivot()) {
            matrix.translate(definition.pivotX(), definition.pivotY(), definition.pivotZ());
            matrix.rotate(rotation);
            matrix.scale(xScale, yScale, zScale);
            matrix.translate(-definition.pivotX(), -definition.pivotY(), -definition.pivotZ());
        } else {
            matrix.rotate(rotation);
            matrix.scale(xScale, yScale, zScale);
        }
        return matrix;
    }

    private void applyCurrentSelfTransform(Matrix4f pose, Matrix3f normal) {
        applyCurrentSelfTransform(pose);
        normal.rotate(rotation);
        normal.scale(xScale, yScale, zScale);
    }

    public Matrix4f getBindLocalTransform() {
        Matrix4f bindLocalTransform = definition.bindLocalTransform();
        return bindLocalTransform == null ? new Matrix4f() : new Matrix4f(bindLocalTransform);
    }

    public Matrix4f getGlobalTransform(BoneTreeInstance instance) {
        return instance.getGlobalTransform(index());
    }

    public BoneTransform getBoneTransform() {
        return new BoneTransform(index(), new Vector3f(x, y, z), new BindRotationView(rotation, rotationInEuler), new Vector3f(xScale, yScale, zScale));
    }

    private record BindRotationView(Quaternionf quaternion, Vector3f euler) implements RotationView {
        private BindRotationView(Quaternionf quaternion, Vector3f euler) {
            this.quaternion = new Quaternionf(quaternion);
            this.euler = new Vector3f(euler);
        }

        @Override
        public Vector3f asEulerAngle() {
            return new Vector3f(euler);
        }

        @Override
        public Quaternionf asQuaternion() {
            return new Quaternionf(quaternion);
        }
    }
}
