package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.render;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.render.MeshDrawTransform;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MeshDrawTransformTest {
    @Test
    void nestedDroppedItemTransformsSurviveReuseOfBothInputMatrices() {
        Matrix4f base = new Matrix4f().rotateX(-0.3F).rotateY(0.7F);
        Matrix4f pose = new Matrix4f().translate(12.5F, -3, 7).rotateY(0.29F).rotateX(-0.14F)
                .scale(0.85F).translate(0, 0.32F, 0).rotateY(1.854F)
                .translate(0.075F, 0, 0.05F).translate(-0.5F, -0.5F, -0.5F);
        Vector3f local = new Vector3f(0.8F, 0.2F, -0.4F);
        Vector3f expected = new Vector3f(local).mulPosition(pose).mulPosition(base);
        MeshDrawTransform captured = new MeshDrawTransform(base, pose);
        base.identity().translate(100, 100, 100);
        pose.identity(); // 等价于在 endBatch 前弹出或重置调用方的姿势栈。
        assertTrue(expected.equals(new Vector3f(local).mulPosition(captured.modelView), 1e-5F));
    }

    @Test
    void perInstanceLightTransformPreservesRotatedUniformScaleLighting() {
        Matrix4f pose = new Matrix4f().rotateY(0.8F).rotateX(-0.2F).scale(0.85F);
        MeshDrawTransform captured = new MeshDrawTransform(new Matrix4f(), pose);
        Vector3f worldLight = new Vector3f(0.2F, 1, -0.7F).normalize();
        Vector3f localNormal = new Vector3f(0, 0, 1);
        Vector3f transformedNormal = new Vector3f(localNormal).mul(new Matrix3f(pose).invert().transpose()).normalize();
        float expected = worldLight.dot(transformedNormal);
        pose.identity();
        Vector3f localLight = new Vector3f(worldLight).mul(captured.inverseLinear).normalize();
        assertEquals(expected, localLight.dot(localNormal), 1e-6F);
    }

    @Test
    void singularScaleUsesFiniteLightingFallbackAndKeepsPositionTransform() {
        Matrix4f pose = new Matrix4f().translate(4, 5, 6).scale(0);
        MeshDrawTransform captured = new MeshDrawTransform(new Matrix4f(), pose);
        assertEquals(new Matrix3f(), captured.inverseLinear);
        assertEquals(new Vector3f(4, 5, 6), new Vector3f(1, 2, 3).mulPosition(captured.modelView));
    }
}
