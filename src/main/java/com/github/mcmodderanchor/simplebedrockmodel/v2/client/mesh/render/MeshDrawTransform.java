package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.render;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** 提交时保存独立的变换副本；调用方随后可以立即复用矩阵或弹出姿势栈。 */
public class MeshDrawTransform {
    public final Matrix4f modelView;
    public final Matrix3f inverseLinear;

    public MeshDrawTransform(Matrix4fc baseView, Matrix4fc pose) {
        modelView = new Matrix4f(baseView).mul(pose);
        inverseLinear = new Matrix3f(pose);
        float determinant = inverseLinear.determinant();
        if (Float.isFinite(determinant) && Math.abs(determinant) > 1.0E-10F) inverseLinear.invert();
        else inverseLinear.identity();
    }
}
