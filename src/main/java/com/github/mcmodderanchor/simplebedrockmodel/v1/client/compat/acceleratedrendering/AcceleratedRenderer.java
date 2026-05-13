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
import java.util.function.Consumer;

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
    private final IAcceleratedRenderer<RenderContext> cachedMeshRenderer = this::renderCachedMesh;

    boolean renderCubes(BedrockBone bone, AcceleratedBedrockBoneCache cache, PoseStack.Pose pose, VertexConsumer consumer,
                        int lightmap, int overlay, float red, float green, float blue, float alpha) {
        if (bone.cubes.isEmpty()) {
            return false;
        }

        IAcceleratedVertexConsumer extension = getExtension(consumer);
        if (!canRender(extension)) {
            return false;
        }

        int color = packColor(red, green, blue, alpha);
        RenderContext context = new RenderContext(cache.cubeMeshes, builder -> {
            for (BedrockCube cube : bone.cubes) {
                cube.compile(IDENTITY_POSE, FIXED_NORMALS, builder, 0, overlay, 1.0f, 1.0f, 1.0f, 1.0f);
            }
        });
        extension.doRender(cachedMeshRenderer, context, pose.pose(), pose.normal(), lightmap, overlay, color);
        return true;
    }

    boolean renderMeshes(BedrockBone bone, AcceleratedBedrockBoneCache cache, PoseStack.Pose pose, VertexConsumer consumer,
                         int lightmap, int overlay, float red, float green, float blue, float alpha) {
        if (bone.meshes.isEmpty()) {
            return false;
        }

        IAcceleratedVertexConsumer extension = getExtension(consumer);
        if (!canRender(extension)) {
            return false;
        }

        int color = packColor(red, green, blue, alpha);
        RenderContext context = new RenderContext(cache.polyMeshes, builder -> {
            for (BedrockMesh mesh : bone.meshes) {
                mesh.compileTriangles(IDENTITY_POSE, builder, 0, overlay, 1.0f, 1.0f, 1.0f, 1.0f);
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
