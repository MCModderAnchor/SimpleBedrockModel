package com.github.mcmodderanchor.simplebedrockmodel.v2.client.compat.acceleratedrendering;

import com.github.argon4w.acceleratedrendering.core.CoreFeature;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.IAcceleratedVertexConsumer;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.IBufferGraph;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.VertexConsumerExtension;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.renderers.IAcceleratedRenderer;
import com.github.argon4w.acceleratedrendering.core.meshes.IMesh;
import com.github.argon4w.acceleratedrendering.core.meshes.collectors.CulledMeshCollector;
import com.github.argon4w.acceleratedrendering.features.entities.AcceleratedEntityRenderingFeature;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedGeometryChunk;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedQuadData;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedVertexData;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.TreeBoneDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.TreeGeometryWriter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.FastColor;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class AcceleratedRenderer {
    private static final Matrix4f IDENTITY_TRANSFORM = new Matrix4f();
    private static final Matrix3f IDENTITY_NORMAL = new Matrix3f();

    private final IAcceleratedRenderer<BakedGeometryChunk> cachedQuadRenderer = this::renderCachedQuads;
    private final IAcceleratedRenderer<BakedGeometryChunk> cachedVertexRenderer = this::renderCachedVertices;
    private final IAcceleratedRenderer<TreeBoneDefinition> cachedCubeRenderer = this::renderCachedCubes;
    private final IAcceleratedRenderer<TreeBoneDefinition> cachedPolyMeshRenderer = this::renderCachedPolyMeshes;

    public boolean renderQuads(BakedGeometryChunk chunk, VertexConsumer consumer, PoseStack.Pose pose,
                               int lightmap, int overlay, float red, float green, float blue, float alpha) {
        if (chunk == null || !chunk.hasQuads()) {
            return false;
        }
        return render(chunk, cachedQuadRenderer, consumer, pose, lightmap, overlay, red, green, blue, alpha);
    }

    public boolean renderVertices(BakedGeometryChunk chunk, VertexConsumer consumer, PoseStack.Pose pose,
                                  int lightmap, int overlay, float red, float green, float blue, float alpha) {
        if (chunk == null || !chunk.hasVertices()) {
            return false;
        }
        return render(chunk, cachedVertexRenderer, consumer, pose, lightmap, overlay, red, green, blue, alpha);
    }

    public boolean renderCubes(TreeBoneDefinition bone, VertexConsumer consumer, PoseStack.Pose pose,
                               int lightmap, int overlay, float red, float green, float blue, float alpha) {
        if (!bone.hasQuads()) {
            return false;
        }
        return render(bone, cachedCubeRenderer, consumer, pose, lightmap, overlay, red, green, blue, alpha);
    }

    public boolean renderPolyMeshes(TreeBoneDefinition bone, VertexConsumer consumer, PoseStack.Pose pose,
                                    int lightmap, int overlay, float red, float green, float blue, float alpha) {
        if (!bone.hasVertices()) {
            return false;
        }
        return render(bone, cachedPolyMeshRenderer, consumer, pose, lightmap, overlay, red, green, blue, alpha);
    }

    private <T> boolean render(T source, IAcceleratedRenderer<T> renderer,
                               VertexConsumer consumer, PoseStack.Pose pose,
                               int lightmap, int overlay, float red, float green, float blue, float alpha) {
        IAcceleratedVertexConsumer extension = getExtension(consumer);
        if (!canRender(extension)) {
            return false;
        }

        extension.doRender(renderer, source, pose.pose(), pose.normal(), lightmap, overlay, packColor(red, green, blue, alpha));
        return true;
    }

    private void renderCachedQuads(VertexConsumer vertexConsumer, BakedGeometryChunk chunk, Matrix4f transform, Matrix3f normal,
                                   int lightmap, int overlay, int color) {
        IAcceleratedVertexConsumer extension = VertexConsumerExtension.getAccelerated(vertexConsumer);
        Map<IBufferGraph, IMesh> meshCache = chunk.getOrCreateCache().quadMeshes;
        IMesh mesh = meshCache.get(extension);

        extension.beginTransform(transform, normal);
        if (mesh == null) {
            CulledMeshCollector collector = new CulledMeshCollector(extension);
            VertexConsumer builder = extension.decorate(collector);
            emitQuads(builder, chunk.quads());
            collector.flush();
            mesh = AcceleratedEntityRenderingFeature.getMeshType().getBuilder().build(collector);
            meshCache.put(extension, mesh);
        }
        mesh.write(extension, color, lightmap, overlay);
        extension.endTransform();
    }

    private void renderCachedVertices(VertexConsumer vertexConsumer, BakedGeometryChunk chunk, Matrix4f transform, Matrix3f normal,
                                      int lightmap, int overlay, int color) {
        IAcceleratedVertexConsumer extension = VertexConsumerExtension.getAccelerated(vertexConsumer);
        Map<IBufferGraph, IMesh> meshCache = chunk.getOrCreateCache().triangleMeshes;
        IMesh mesh = meshCache.get(extension);

        extension.beginTransform(transform, normal);
        if (mesh == null) {
            CulledMeshCollector collector = new CulledMeshCollector(extension);
            VertexConsumer builder = extension.decorate(collector);
            emitVertices(builder, chunk.vertices());
            collector.flush();
            mesh = AcceleratedEntityRenderingFeature.getMeshType().getBuilder().build(collector);
            meshCache.put(extension, mesh);
        }
        mesh.write(extension, color, lightmap, overlay);
        extension.endTransform();
    }

    private void renderCachedCubes(VertexConsumer vertexConsumer, TreeBoneDefinition bone, Matrix4f transform, Matrix3f normal,
                                   int lightmap, int overlay, int color) {
        IAcceleratedVertexConsumer extension = VertexConsumerExtension.getAccelerated(vertexConsumer);
        Map<IBufferGraph, IMesh> meshCache = bone.getOrCreateCache().quadMeshes;
        IMesh mesh = meshCache.get(extension);

        extension.beginTransform(transform, normal);
        if (mesh == null) {
            CulledMeshCollector collector = new CulledMeshCollector(extension);
            VertexConsumer builder = extension.decorate(collector);
            TreeGeometryWriter.writeCubesFallback(bone.cubes(), builder, IDENTITY_TRANSFORM, IDENTITY_NORMAL, 0, 0, 1, 1, 1, 1);
            collector.flush();
            mesh = AcceleratedEntityRenderingFeature.getMeshType().getBuilder().build(collector);
            meshCache.put(extension, mesh);
        }
        mesh.write(extension, color, lightmap, overlay);
        extension.endTransform();
    }

    private void renderCachedPolyMeshes(VertexConsumer vertexConsumer, TreeBoneDefinition bone, Matrix4f transform, Matrix3f normal,
                                        int lightmap, int overlay, int color) {
        IAcceleratedVertexConsumer extension = VertexConsumerExtension.getAccelerated(vertexConsumer);
        Map<IBufferGraph, IMesh> meshCache = bone.getOrCreateCache().triangleMeshes;
        IMesh mesh = meshCache.get(extension);

        extension.beginTransform(transform, normal);
        if (mesh == null) {
            CulledMeshCollector collector = new CulledMeshCollector(extension);
            VertexConsumer builder = extension.decorate(collector);
            TreeGeometryWriter.writePolyMeshesFallback(bone.polyMeshes(), builder, IDENTITY_TRANSFORM, IDENTITY_NORMAL, 0, 0, 1, 1, 1, 1);
            collector.flush();
            mesh = AcceleratedEntityRenderingFeature.getMeshType().getBuilder().build(collector);
            meshCache.put(extension, mesh);
        }
        mesh.write(extension, color, lightmap, overlay);
        extension.endTransform();
    }

    private void emitQuads(VertexConsumer builder, BakedQuadData quads) {
        float[] positions = quads.positions();
        float[] normals = quads.normals();
        float[] uvs = quads.uvs();
        int color = FastColor.ARGB32.color(255, 255, 255, 255);
        for (int i = 0; i < quads.quadCount(); i++) {
            int pb = i * BakedQuadData.POSITION_STRIDE;
            int nb = i * BakedQuadData.NORMAL_STRIDE;
            int ub = i * BakedQuadData.UV_STRIDE;
            float nx = normals[nb];
            float ny = normals[nb + 1];
            float nz = normals[nb + 2];
            builder.addVertex(positions[pb], positions[pb + 1], positions[pb + 2], color, uvs[ub], uvs[ub + 1], 0, 0, nx, ny, nz);
            builder.addVertex(positions[pb + 3], positions[pb + 4], positions[pb + 5], color, uvs[ub + 2], uvs[ub + 3], 0, 0, nx, ny, nz);
            builder.addVertex(positions[pb + 6], positions[pb + 7], positions[pb + 8], color, uvs[ub + 4], uvs[ub + 5], 0, 0, nx, ny, nz);
            builder.addVertex(positions[pb + 9], positions[pb + 10], positions[pb + 11], color, uvs[ub + 6], uvs[ub + 7], 0, 0, nx, ny, nz);
        }
    }

    private void emitVertices(VertexConsumer builder, BakedVertexData vertices) {
        float[] positions = vertices.positions();
        float[] normals = vertices.normals();
        float[] uvs = vertices.uvs();
        int color = FastColor.ARGB32.color(255, 255, 255, 255);
        for (int i = 0; i < vertices.vertexCount(); i++) {
            int pb = i * BakedVertexData.POSITION_STRIDE;
            int nb = i * BakedVertexData.NORMAL_STRIDE;
            int ub = i * BakedVertexData.UV_STRIDE;
            builder.addVertex(positions[pb], positions[pb + 1], positions[pb + 2], color, uvs[ub], uvs[ub + 1], 0, 0, normals[nb], normals[nb + 1], normals[nb + 2]);
        }
    }

    private IAcceleratedVertexConsumer getExtension(VertexConsumer consumer) {
        try {
            return VertexConsumerExtension.getAccelerated(consumer);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean canRender(IAcceleratedVertexConsumer extension) {
        if (extension == null) {
            return false;
        }
        try {
            return AcceleratedEntityRenderingFeature.isEnabled()
                    && AcceleratedEntityRenderingFeature.shouldUseAcceleratedPipeline()
                    && (CoreFeature.isRenderingLevel()
                    || (CoreFeature.isRenderingGui() && AcceleratedEntityRenderingFeature.shouldAccelerateInGui())
                    || CoreFeature.isRenderingHand())
                    && extension.isAccelerated();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int packColor(float red, float green, float blue, float alpha) {
        return FastColor.ARGB32.color(
                (int) (alpha * 255.0f),
                (int) (red * 255.0f),
                (int) (green * 255.0f),
                (int) (blue * 255.0f)
        );
    }
}
