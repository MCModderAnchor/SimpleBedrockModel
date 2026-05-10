package com.github.mcmodderanchor.simplebedrockmodel.v1.common.model;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.acceleratedrendering.AcceleratedBedrockBoneCache;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.acceleratedrendering.AcceleratedRenderingCompat;
import com.maydaymemory.mae.basic.BoneTransform;
import com.maydaymemory.mae.basic.ZYXRotationView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.renderer.LightTexture;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;

public class BedrockBone {
    @OnlyIn(Dist.CLIENT)
    private static class ClientConstants {
        private static final Vector3f[] NORMALS = new Vector3f[6];
        private static final int MAX_LIGHT_TEXTURE = LightTexture.pack(15, 15);

        static {
            for (int i = 0; i < ClientConstants.NORMALS.length; i++) {
                ClientConstants.NORMALS[i] = new Vector3f();
            }
        }
    }

    public final ObjectList<BedrockCube> cubes = new ObjectArrayList<>();
    public final ObjectList<BedrockMesh> meshes = new ObjectArrayList<>();
    private final ObjectList<BedrockBone> children = new ObjectArrayList<>();
    public BedrockBone parent;
    public int index = -1;
    public float x;
    public float y;
    public float z;
    public Quaternionf rotation = new Quaternionf();
    /** 这个旋转不会应用到渲染，只会用来生成 bind pose。*/
    public Vector3f rotationInEuler = new Vector3f();
    public float xScale = 1;
    public float yScale = 1;
    public float zScale = 1;
    public boolean visible = true;
    public boolean illuminated = false;
    public boolean mirror;
    private Map<String, LocatorData> locators = Map.of();
    @OnlyIn(Dist.CLIENT)
    private transient AcceleratedBedrockBoneCache acceleratedCache;

    @OnlyIn(Dist.CLIENT)
    public void render(PoseStack poseStack, VertexConsumer consumer, int lightmap, int overlay) {
        this.render(poseStack, consumer, lightmap, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    @OnlyIn(Dist.CLIENT)
    public void render(PoseStack poseStack, VertexConsumer consumer, int lightmap, int overlay, float red, float green, float blue, float alpha) {
        int cubePackedLight = illuminated ?  ClientConstants.MAX_LIGHT_TEXTURE : lightmap;
        if (this.visible) {
            // 缩放过小时，直接退出渲染
            boolean xNearZero = -1E-5F < xScale && xScale < 1E-5F;
            boolean yNearZero = -1E-5F < yScale && yScale < 1E-5F;
            boolean zNearZero = -1E-5F < zScale && zScale < 1E-5F;
            if ((xNearZero && yNearZero) || (xNearZero && zNearZero) || (yNearZero && zNearZero)) {
                return;
            }

            if (!this.cubes.isEmpty() || !this.children.isEmpty()) {
                poseStack.pushPose();
                this.translateAndRotateAndScale(poseStack);
                PoseStack.Pose pose = poseStack.last();
                if (!AcceleratedRenderingCompat.renderCubes(this, getAcceleratedCache(), pose, consumer, cubePackedLight, overlay, red, green, blue, alpha)) {
                    this.compile(pose, consumer, cubePackedLight, overlay, red, green, blue, alpha);
                }

                for (BedrockBone part : this.children) {
                    part.render(poseStack, consumer, cubePackedLight, overlay, red, green, blue, alpha);
                }

                poseStack.popPose();
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public void render(PoseStack poseStack, VertexConsumer quadConsumer, VertexConsumer triangleConsumer, int lightmap, int overlay) {
        this.render(poseStack, quadConsumer, triangleConsumer, lightmap, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    @OnlyIn(Dist.CLIENT)
    public void render(PoseStack poseStack, VertexConsumer quadConsumer, VertexConsumer triangleConsumer, int lightmap, int overlay,
                       float red, float green, float blue, float alpha) {
        int cubePackedLight = illuminated ? ClientConstants.MAX_LIGHT_TEXTURE : lightmap;
        if (this.visible) {
            // 缩放过小时，直接退出渲染
            boolean xNearZero = -1E-5F < xScale && xScale < 1E-5F;
            boolean yNearZero = -1E-5F < yScale && yScale < 1E-5F;
            boolean zNearZero = -1E-5F < zScale && zScale < 1E-5F;
            if ((xNearZero && yNearZero) || (xNearZero && zNearZero) || (yNearZero && zNearZero)) {
                return;
            }

            if (!this.cubes.isEmpty() || !this.meshes.isEmpty() || !this.children.isEmpty()) {
                poseStack.pushPose();
                this.translateAndRotateAndScale(poseStack);
                PoseStack.Pose pose = poseStack.last();
                AcceleratedBedrockBoneCache cache = getAcceleratedCache();
                if (!AcceleratedRenderingCompat.renderCubes(this, cache, pose, quadConsumer, cubePackedLight, overlay, red, green, blue, alpha)) {
                    this.compile(pose, quadConsumer, cubePackedLight, overlay, red, green, blue, alpha);
                }
                if (!AcceleratedRenderingCompat.renderMeshes(this, cache, pose, triangleConsumer, cubePackedLight, overlay, red, green, blue, alpha)) {
                    this.compileMeshes(pose, triangleConsumer, cubePackedLight, overlay, red, green, blue, alpha);
                }

                for (BedrockBone part : this.children) {
                    part.render(poseStack, quadConsumer, triangleConsumer, cubePackedLight, overlay, red, green, blue, alpha);
                }

                poseStack.popPose();
            }
        }
    }

    public void translateAndRotateAndScale(PoseStack poseStack) {
        poseStack.translate(this.x / 16.0F, this.y / 16.0F, this.z / 16.0F);
        poseStack.last().pose().rotate(rotation);
        poseStack.last().normal().rotate(rotation);
        if (this.xScale != 0.0F || this.yScale != 0.0F || this.zScale != 0.0F) {
            poseStack.last().pose().scale(this.xScale, this.yScale, this.zScale);
            poseStack.last().normal().scale(this.xScale, this.yScale, this.zScale);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private AcceleratedBedrockBoneCache getAcceleratedCache() {
        if (acceleratedCache == null) {
            acceleratedCache = new AcceleratedBedrockBoneCache();
        }
        return acceleratedCache;
    }

    @OnlyIn(Dist.CLIENT)
    private void compile(PoseStack.Pose pose, VertexConsumer consumer, int lightmap, int overlay, float red, float green, float blue, float alpha) {
        Matrix3f normal = pose.normal();
        ClientConstants.NORMALS[0].set(-normal.m10, -normal.m11, -normal.m12);
        ClientConstants.NORMALS[1].set(normal.m10, normal.m11, normal.m12);
        ClientConstants.NORMALS[2].set(-normal.m20, -normal.m21, -normal.m22);
        ClientConstants.NORMALS[3].set(normal.m20, normal.m21, normal.m22);
        ClientConstants.NORMALS[4].set(-normal.m00, -normal.m01, -normal.m02);
        ClientConstants.NORMALS[5].set(normal.m00, normal.m01, normal.m02);
        for (BedrockCube bedrockCube : this.cubes) {
            bedrockCube.compile(pose, ClientConstants.NORMALS, consumer, lightmap, overlay, red, green, blue, alpha);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private void compileMeshes(PoseStack.Pose pose, VertexConsumer consumer, int lightmap, int overlay, float red, float green, float blue, float alpha) {
        for (BedrockMesh mesh : this.meshes) {
            mesh.compileTriangles(pose, consumer, lightmap, overlay, red, green, blue, alpha);
        }
    }

    public BoneTransform getBoneTransform() {
        return new BoneTransform(index, new Vector3f(x, y, z), new ZYXRotationView(rotation), new Vector3f(xScale, yScale, zScale));
    }

    public Matrix4f getGlobalTransform() {
        Matrix4f matrix = new Matrix4f();
        BedrockBone bone = this;
        while (bone != null) {
            matrix.scaleLocal(bone.xScale, bone.yScale, bone.zScale);
            matrix.rotateLocal(bone.rotation);
            matrix.translateLocal(bone.x / 16.0F, bone.y / 16.0F, bone.z / 16.0F);
            bone = bone.parent;
        }
        return matrix;
    }

    public boolean isEmpty() {
        return this.cubes.isEmpty() && this.meshes.isEmpty() && this.children.isEmpty();
    }

    public void addChild(BedrockBone model) {
        this.children.add(model);
    }

    public ObjectList<BedrockBone> getChildren() {
        return children;
    }

    public Map<String, LocatorData> getLocators() {
        return locators;
    }

    public void setLocators(Map<String, LocatorData> locators) {
        this.locators = locators;
    }
}
