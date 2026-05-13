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
    private boolean hasCubesInTree;
    private boolean hasMeshesInTree;
    @OnlyIn(Dist.CLIENT)
    private transient AcceleratedBedrockBoneCache acceleratedCache;

    @OnlyIn(Dist.CLIENT)
    public void render(PoseStack poseStack, VertexConsumer consumer, int lightmap, int overlay) {
        this.render(poseStack, consumer, lightmap, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    @OnlyIn(Dist.CLIENT)
    public void render(PoseStack poseStack, VertexConsumer consumer, int lightmap, int overlay, float red, float green, float blue, float alpha) {
        this.renderGeometryPass(poseStack, consumer, lightmap, overlay, red, green, blue, alpha, false);
    }

    @OnlyIn(Dist.CLIENT)
    public void renderCubes(PoseStack poseStack, VertexConsumer quadConsumer, int lightmap, int overlay,
                            float red, float green, float blue, float alpha) {
        this.renderGeometryPass(poseStack, quadConsumer, lightmap, overlay, red, green, blue, alpha, false);
    }

    @OnlyIn(Dist.CLIENT)
    public void renderMeshes(PoseStack poseStack, VertexConsumer triangleConsumer, int lightmap, int overlay,
                             float red, float green, float blue, float alpha) {
        this.renderGeometryPass(poseStack, triangleConsumer, lightmap, overlay, red, green, blue, alpha, true);
    }

    @OnlyIn(Dist.CLIENT)
    private void renderGeometryPass(PoseStack poseStack, VertexConsumer consumer, int lightmap, int overlay,
                                    float red, float green, float blue, float alpha, boolean meshesPass) {
        if (!this.hasGeometryInTree(meshesPass)) {
            return;
        }
        if (!this.visible || isScaleTooSmallToRender()) {
            return;
        }

        int packedLight = illuminated ? ClientConstants.MAX_LIGHT_TEXTURE : lightmap;
        poseStack.pushPose();
        this.translateAndRotateAndScale(poseStack);
        PoseStack.Pose pose = poseStack.last();

        if (meshesPass) {
            if (!this.meshes.isEmpty() && !AcceleratedRenderingCompat.renderMeshes(this, getAcceleratedCache(), pose, consumer, packedLight, overlay, red, green, blue, alpha)) {
                this.compileMeshes(pose, consumer, packedLight, overlay, red, green, blue, alpha);
            }
        } else if (!this.cubes.isEmpty() && !AcceleratedRenderingCompat.renderCubes(this, getAcceleratedCache(), pose, consumer, packedLight, overlay, red, green, blue, alpha)) {
            this.compile(pose, consumer, packedLight, overlay, red, green, blue, alpha);
        }

        for (BedrockBone child : this.children) {
            if (child.hasGeometryInTree(meshesPass)) {
                child.renderGeometryPass(poseStack, consumer, packedLight, overlay, red, green, blue, alpha, meshesPass);
            }
        }

        poseStack.popPose();
    }

    private boolean isScaleTooSmallToRender() {
        boolean xNearZero = -1E-5F < xScale && xScale < 1E-5F;
        boolean yNearZero = -1E-5F < yScale && yScale < 1E-5F;
        boolean zNearZero = -1E-5F < zScale && zScale < 1E-5F;
        return (xNearZero && yNearZero) || (xNearZero && zNearZero) || (yNearZero && zNearZero);
    }

    public void updateGeometryFlags() {
        this.hasCubesInTree = !this.cubes.isEmpty();
        this.hasMeshesInTree = !this.meshes.isEmpty();
        for (BedrockBone child : this.children) {
            child.updateGeometryFlags();
            this.hasCubesInTree |= child.hasCubesInTree();
            this.hasMeshesInTree |= child.hasMeshesInTree();
        }
    }

    private boolean hasGeometryInTree(boolean meshesPass) {
        return meshesPass ? hasMeshesInTree : hasCubesInTree;
    }

    public boolean hasCubesInTree() {
        return hasCubesInTree;
    }

    public boolean hasMeshesInTree() {
        return hasMeshesInTree;
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
