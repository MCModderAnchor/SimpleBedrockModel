package com.github.mcmodderanchor.simplebedrockmodel.v2.client.compat.acceleratedrendering;

import com.github.argon4w.acceleratedrendering.core.CoreFeature;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.IBufferGraph;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.IAcceleratedVertexConsumer;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.VertexConsumerExtension;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.renderers.IAcceleratedRenderer;
import com.github.argon4w.acceleratedrendering.core.meshes.IMesh;
import com.github.argon4w.acceleratedrendering.core.meshes.collectors.CulledMeshCollector;
import com.github.argon4w.acceleratedrendering.features.entities.AcceleratedEntityRenderingFeature;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.bake.BakedGeometryChunk;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.bake.BakedQuadData;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.bake.BakedVertexData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.FastColor;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public final class AcceleratedRenderer {
    private final IAcceleratedRenderer<RenderContext> cachedMeshRenderer = this::renderCachedMesh;

    public boolean renderQuads(BakedGeometryChunk chunk, VertexConsumer consumer, PoseStack.Pose pose,
                               int lightmap, int overlay, float red, float green, float blue, float alpha) {
        if (chunk == null || !chunk.hasQuads()) {
            return false;
        }
        return render(chunk, consumer, pose, lightmap, overlay, red, green, blue, alpha, true);
    }

    public boolean renderVertices(BakedGeometryChunk chunk, VertexConsumer consumer, PoseStack.Pose pose,
                                  int lightmap, int overlay, float red, float green, float blue, float alpha) {
        if (chunk == null || !chunk.hasVertices()) {
            return false;
        }
        return render(chunk, consumer, pose, lightmap, overlay, red, green, blue, alpha, false);
    }

    private boolean render(BakedGeometryChunk chunk, VertexConsumer consumer, PoseStack.Pose pose,
                           int lightmap, int overlay, float red, float green, float blue, float alpha, boolean quads) {
        IAcceleratedVertexConsumer extension = getExtension(consumer);
        if (!canRender(extension)) {
            return false;
        }

        AcceleratedBedrockGeometryCache cache = chunk.getOrCreateCache();
        Map<IBufferGraph, IMesh> meshCache = quads ? cache.quadMeshes : cache.triangleMeshes;
        int color = packColor(red, green, blue, alpha);
        RenderContext context = new RenderContext(meshCache, builder -> {
            if (quads) {
                emitQuads(builder, chunk.quads());
            } else {
                emitVertices(builder, chunk.vertices());
            }
        });
        extension.doRender(cachedMeshRenderer, context, pose.pose(), pose.normal(), lightmap, overlay, color);
        return true;
    }

    private void renderCachedMesh(VertexConsumer vertexConsumer, RenderContext context, Matrix4f transform, Matrix3f normal,
                                  int lightmap, int overlay, int color) {
        IAcceleratedVertexConsumer extension = VertexConsumerExtension.getAccelerated(vertexConsumer);
        IMesh mesh = context.cache.get(extension);

        extension.beginTransform(transform, normal);
        if (mesh == null) {
            CulledMeshCollector collector = new CulledMeshCollector(extension);
            VertexConsumer builder = extension.decorate(collector);
            context.meshEmitter.accept(builder);
            collector.flush();
            mesh = AcceleratedEntityRenderingFeature.getMeshType().getBuilder().build(collector);
            context.cache.put(extension, mesh);
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

    private record RenderContext(Map<IBufferGraph, IMesh> cache, Consumer<VertexConsumer> meshEmitter) {
    }
}
