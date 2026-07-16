package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.compat.acceleratedrendering.AcceleratedRenderingCompat;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.compat.sodium.SodiumCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.util.FastColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class BakedGeometryChunkRenderer {
    public static final BakedGeometryChunkRenderer INSTANCE = new BakedGeometryChunkRenderer();

    private final Vector3f renderPosition = new Vector3f();
    private final Vector3f renderNormal = new Vector3f();

    public void render(BakedGeometryChunk chunk, PoseStack poseStack, VertexConsumer quadConsumer, VertexConsumer triangleConsumer,
                       int lightmap, int overlay, float red, float green, float blue, float alpha) {
        render(chunk, poseStack, quadConsumer, triangleConsumer, lightmap, overlay, red, green, blue, alpha, false);
    }

    public void render(BakedGeometryChunk chunk, PoseStack poseStack, VertexConsumer quadConsumer, VertexConsumer triangleConsumer,
                       int lightmap, int overlay, float red, float green, float blue, float alpha, boolean skipNormalVisibilityCull) {
        if (chunk.hasQuads()) {
            renderQuadChunk(chunk, poseStack, quadConsumer, lightmap, overlay, red, green, blue, alpha, skipNormalVisibilityCull);
        }
        if (chunk.hasVertices()) {
            renderVertexChunk(chunk, poseStack, triangleConsumer, lightmap, overlay, red, green, blue, alpha);
        }
    }

    public void renderQuadChunk(BakedGeometryChunk chunk, PoseStack poseStack, VertexConsumer consumer, int lightmap, int overlay,
                                float red, float green, float blue, float alpha) {
        renderQuadChunk(chunk, poseStack, consumer, lightmap, overlay, red, green, blue, alpha, false);
    }

    public void renderQuadChunk(BakedGeometryChunk chunk, PoseStack poseStack, VertexConsumer consumer, int lightmap, int overlay,
                                float red, float green, float blue, float alpha, boolean skipNormalVisibilityCull) {
        PoseStack.Pose pose = poseStack.last();
        if (AcceleratedRenderingCompat.renderQuads(chunk, consumer, pose, lightmap, overlay, red, green, blue, alpha)) {
            return;
        }
        Matrix4f poseMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        if (SodiumCompat.writeQuads(chunk, consumer, lightmap, overlay, red, green, blue, alpha, poseMatrix, normalMatrix, skipNormalVisibilityCull)) {
            return;
        }
        BakedQuadData quads = chunk.quads();
        float[] positions = quads.positions();
        float[] normals = quads.normals();
        float[] uvs = quads.uvs();
        boolean cull = !skipNormalVisibilityCull && RenderSystem.getModelViewMatrix().m32() == 0;
        for (int i = 0; i < quads.quadCount(); i++) {
            int pb = i * BakedQuadData.POSITION_STRIDE;
            int nb = i * BakedQuadData.NORMAL_STRIDE;
            int ub = i * BakedQuadData.UV_STRIDE;
            float nx = normals[nb];
            float ny = normals[nb + 1];
            float nz = normals[nb + 2];
            float tnx = normalMatrix.m00() * nx + normalMatrix.m10() * ny + normalMatrix.m20() * nz;
            float tny = normalMatrix.m01() * nx + normalMatrix.m11() * ny + normalMatrix.m21() * nz;
            float tnz = normalMatrix.m02() * nx + normalMatrix.m12() * ny + normalMatrix.m22() * nz;
            if (cull && shouldCullQuad(positions, pb, poseMatrix, tnx, tny, tnz)) {
                continue;
            }
            emitVertex(consumer, poseMatrix, red, green, blue, alpha, positions[pb], positions[pb + 1], positions[pb + 2], uvs[ub], uvs[ub + 1], overlay, lightmap, tnx, tny, tnz);
            emitVertex(consumer, poseMatrix, red, green, blue, alpha, positions[pb + 3], positions[pb + 4], positions[pb + 5], uvs[ub + 2], uvs[ub + 3], overlay, lightmap, tnx, tny, tnz);
            emitVertex(consumer, poseMatrix, red, green, blue, alpha, positions[pb + 6], positions[pb + 7], positions[pb + 8], uvs[ub + 4], uvs[ub + 5], overlay, lightmap, tnx, tny, tnz);
            emitVertex(consumer, poseMatrix, red, green, blue, alpha, positions[pb + 9], positions[pb + 10], positions[pb + 11], uvs[ub + 6], uvs[ub + 7], overlay, lightmap, tnx, tny, tnz);
        }
    }

    public void renderVertexChunk(BakedGeometryChunk chunk, PoseStack poseStack, VertexConsumer consumer, int lightmap, int overlay,
                                  float red, float green, float blue, float alpha) {
        PoseStack.Pose pose = poseStack.last();
        if (AcceleratedRenderingCompat.renderVertices(chunk, consumer, pose, lightmap, overlay, red, green, blue, alpha)) {
            return;
        }
        Matrix4f poseMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        if (SodiumCompat.writeVertices(chunk, consumer, lightmap, overlay, red, green, blue, alpha, poseMatrix, normalMatrix)) {
            return;
        }
        BakedVertexData vertices = chunk.vertices();
        float[] positions = vertices.positions();
        float[] normals = vertices.normals();
        float[] uvs = vertices.uvs();
        int color = packColor(red, green, blue, alpha);
        for (int i = 0; i < vertices.vertexCount(); i++) {
            int pb = i * BakedVertexData.POSITION_STRIDE;
            int nb = i * BakedVertexData.NORMAL_STRIDE;
            int ub = i * BakedVertexData.UV_STRIDE;
            renderPosition.set(positions[pb], positions[pb + 1], positions[pb + 2]).mulPosition(poseMatrix);
            renderNormal.set(normals[nb], normals[nb + 1], normals[nb + 2]).mul(normalMatrix);
            if (renderNormal.lengthSquared() > 1.0E-12f) {
                renderNormal.normalize();
            }
            consumer.addVertex(renderPosition.x, renderPosition.y, renderPosition.z, color, uvs[ub], uvs[ub + 1], overlay, lightmap,
                    renderNormal.x, renderNormal.y, renderNormal.z);
        }
    }

    private void emitVertex(VertexConsumer consumer, Matrix4f poseMatrix, float red, float green, float blue, float alpha,
                            float x, float y, float z, float u, float v, int overlay, int lightmap, float nx, float ny, float nz) {
        renderPosition.set(x, y, z).mulPosition(poseMatrix);
        int color = packColor(red, green, blue, alpha);
        consumer.addVertex(renderPosition.x, renderPosition.y, renderPosition.z, color, u, v, overlay, lightmap, nx, ny, nz);
    }

    private int packColor(float red, float green, float blue, float alpha) {
        return FastColor.ARGB32.color(
                (int) (alpha * 255.0f),
                (int) (red * 255.0f),
                (int) (green * 255.0f),
                (int) (blue * 255.0f)
        );
    }

    private boolean shouldCullQuad(float[] positions, int positionBase, Matrix4f poseMatrix, float nx, float ny, float nz) {
        float cx = positions[positionBase] + positions[positionBase + 6];
        float cy = positions[positionBase + 1] + positions[positionBase + 7];
        float cz = positions[positionBase + 2] + positions[positionBase + 8];
        float tx = poseMatrix.m00() * cx + poseMatrix.m10() * cy + poseMatrix.m20() * cz + poseMatrix.m30() + poseMatrix.m30();
        float ty = poseMatrix.m01() * cx + poseMatrix.m11() * cy + poseMatrix.m21() * cz + poseMatrix.m31() + poseMatrix.m31();
        float tz = poseMatrix.m02() * cx + poseMatrix.m12() * cy + poseMatrix.m22() * cz + poseMatrix.m32() + poseMatrix.m32();
        return tx * nx + ty * ny + tz * nz > 0;
    }

}
