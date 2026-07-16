package com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.acceleratedrendering;

import com.github.argon4w.acceleratedrendering.core.CoreFeature;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.IAcceleratedVertexConsumer;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.IBufferGraph;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.VertexConsumerExtension;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.renderers.IAcceleratedRenderer;
import com.github.argon4w.acceleratedrendering.core.meshes.IMesh;
import com.github.argon4w.acceleratedrendering.core.meshes.collectors.CulledMeshCollector;
import com.github.argon4w.acceleratedrendering.features.entities.AcceleratedEntityRenderingFeature;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockBone;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockCube;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.FastColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Map;

@OnlyIn(Dist.CLIENT)
final class AcceleratedRenderer {
    private static final PoseStack.Pose IDENTITY_POSE = new PoseStack().last();
    private static final Vector3f[] FIXED_NORMALS = {
            new Vector3f(-0.0f, -1.0f, -0.0f),
            new Vector3f(+0.0f, +1.0f, +0.0f),
            new Vector3f(-0.0f, -0.0f, -1.0f),
            new Vector3f(+0.0f, +0.0f, +1.0f),
            new Vector3f(-1.0f, -0.0f, -0.0f),
            new Vector3f(+1.0f, +0.0f, +0.0f)
    };

    private final IAcceleratedRenderer<BedrockBone> cachedCubeRenderer = this::renderCachedCubes;
    private final IAcceleratedRenderer<BedrockBone> cachedMeshRenderer = this::renderCachedMeshes;

    boolean renderCubes(BedrockBone bone, PoseStack.Pose pose, VertexConsumer consumer,
                        int lightmap, int overlay, float red, float green, float blue, float alpha) {
        if (bone.cubes.isEmpty()) {
            return false;
        }
        return render(bone, cachedCubeRenderer, consumer, pose, lightmap, overlay, red, green, blue, alpha);
    }

    boolean renderMeshes(BedrockBone bone, PoseStack.Pose pose, VertexConsumer consumer,
                         int lightmap, int overlay, float red, float green, float blue, float alpha) {
        if (bone.meshes.isEmpty()) {
            return false;
        }
        return render(bone, cachedMeshRenderer, consumer, pose, lightmap, overlay, red, green, blue, alpha);
    }

    private boolean render(BedrockBone bone, IAcceleratedRenderer<BedrockBone> renderer,
                           VertexConsumer consumer, PoseStack.Pose pose,
                           int lightmap, int overlay, float red, float green, float blue, float alpha) {
        IAcceleratedVertexConsumer extension = getExtension(consumer);
        if (!canRender(extension)) {
            return false;
        }

        extension.doRender(renderer, bone, pose.pose(), pose.normal(), lightmap, overlay, packColor(red, green, blue, alpha));
        return true;
    }

    private void renderCachedCubes(VertexConsumer vertexConsumer, BedrockBone bone, Matrix4f transform, Matrix3f normal,
                                   int lightmap, int overlay, int color) {
        IAcceleratedVertexConsumer extension = VertexConsumerExtension.getAccelerated(vertexConsumer);
        Map<IBufferGraph, IMesh> meshCache = bone.getAcceleratedCache().cubeMeshes;
        IMesh mesh = meshCache.get(extension);

        extension.beginTransform(transform, normal);
        if (mesh == null) {
            CulledMeshCollector collector = new CulledMeshCollector(extension);
            VertexConsumer builder = extension.decorate(collector);
            for (BedrockCube cube : bone.cubes) {
                cube.compile(IDENTITY_POSE, FIXED_NORMALS, builder, 0, overlay, 1.0f, 1.0f, 1.0f, 1.0f);
            }
            collector.flush();
            mesh = AcceleratedEntityRenderingFeature.getMeshType().getBuilder().build(collector);
            meshCache.put(extension, mesh);
        }
        mesh.write(extension, color, lightmap, overlay);
        extension.endTransform();
    }

    private void renderCachedMeshes(VertexConsumer vertexConsumer, BedrockBone bone, Matrix4f transform, Matrix3f normal,
                                    int lightmap, int overlay, int color) {
        IAcceleratedVertexConsumer extension = VertexConsumerExtension.getAccelerated(vertexConsumer);
        Map<IBufferGraph, IMesh> meshCache = bone.getAcceleratedCache().polyMeshes;
        IMesh mesh = meshCache.get(extension);

        extension.beginTransform(transform, normal);
        if (mesh == null) {
            CulledMeshCollector collector = new CulledMeshCollector(extension);
            VertexConsumer builder = extension.decorate(collector);
            for (BedrockMesh meshSource : bone.meshes) {
                meshSource.compileTriangles(IDENTITY_POSE, builder, 0, overlay, 1.0f, 1.0f, 1.0f, 1.0f);
            }
            collector.flush();
            mesh = AcceleratedEntityRenderingFeature.getMeshType().getBuilder().build(collector);
            meshCache.put(extension, mesh);
        }
        mesh.write(extension, color, lightmap, overlay);
        extension.endTransform();
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
