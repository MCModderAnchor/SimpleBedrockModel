package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.MeshGeometryProvider;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache.StaticMeshCache;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache.MeshCachePolicy;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.MeshIntegerAttributes;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.render.MeshBatchRenderer;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.render.MeshDrawTransform;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.debug.MeshRenderDebug;
import static com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache.StaticMeshCache.*;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** 临时共享静态网格的即时绘制入口，以及显式渲染阶段批次入口。 */
@OnlyIn(Dist.CLIENT)
public class StaticMeshRenderer {
    public enum BatchOrder {
        SUBMISSION,
        MATERIAL
    }

    public StaticMeshRenderer() {}

    /** 阶段所有者负责协调原版缓冲，并在改变阶段状态前关闭批次。 */
    public static StaticMeshBufferSource openBatch(ResourceLocation owner, BatchOrder order,
                                                   java.util.function.BooleanSupplier isCurrent) {
        RenderSystem.assertOnRenderThread();
        if (MeshRenderDebug.attributes != null) throw new IllegalStateException("Batch uses production attributes");
        return new StaticMeshBufferSource(Objects.requireNonNull(owner), Objects.requireNonNull(order),
                Objects.requireNonNull(isCurrent));
    }

    public static StaticMeshBufferSource openBatch(ResourceLocation owner, BatchOrder order) {
        return openBatch(owner, order, () -> true);
    }

    public static boolean supportsInstanceAttributes(int packedLight, int packedOverlay) {
        return StaticMeshCache.supportsInstanceAttributes(packedLight, packedOverlay);
    }

    public static void invalidateOwner(ResourceLocation owner) { StaticMeshCache.invalidateOwner(owner); }
    public static boolean isOwnerEnabled(ResourceLocation owner) { return StaticMeshCache.isOwnerEnabled(owner); }

    public static StaticMeshCache.PreparationStats preparationStats() { return StaticMeshCache.preparationStats(); }

    public static MeshDrawResult tryDraw(ResourceLocation owner, Object key, MeshGeometryProvider provider,
                                         PoseStack pose, int light, int overlay) {
        return tryDraw(owner, key, null, provider, pose, light, overlay, MeshCachePolicy.DEFAULT.idleRetention());
    }

    /** 在当前调用中准备网格，旧几何只查询；PENDING 接管本次调用，也包括部分绘制失败。 */
    public static MeshDrawResult tryDraw(ResourceLocation owner, Object key, @Nullable Object previousKey,
                                         MeshGeometryProvider provider, PoseStack pose, int light, int overlay,
                                         Duration retention) {
        RenderSystem.assertOnRenderThread();
        Objects.requireNonNull(owner, "owner");
        if (!isOwnerEnabled(owner)) return MeshDrawResult.UNSUPPORTED;
        if (!prepareRequest(key, provider, pose, light, overlay, retention)) return MeshDrawResult.UNSUPPORTED;
        Entry target = acquire(new CacheKey(owner, key));
        Entry previous = previousKey == null || previousKey.equals(key) ? null : CACHE.get(new CacheKey(owner, previousKey));
        if (previous != null) pin(previous);
        MeshDrawResult result;
        try {
            Preparation prepared = prepareTarget(target, provider);
            if (prepared == Preparation.READY) {
                // 开始绘制后不能尝试其他网格，因为可能已经提交了部分绘制。
                result = draw(target, pose, light, overlay) ? MeshDrawResult.DRAWN : MeshDrawResult.PENDING;
            } else if (previous != null && previous.ready() && layoutSupported(previous)
                    && (MeshRenderDebug.attributes != null || INTEGER_ATTRIBUTES.ensureReady())) {
                result = draw(previous, pose, light, overlay) ? MeshDrawResult.DRAWN_PREVIOUS : MeshDrawResult.PENDING;
            } else {
                result = prepared == Preparation.UNSUPPORTED ? MeshDrawResult.UNSUPPORTED : MeshDrawResult.PENDING;
            }
        } catch (RuntimeException failure) {
            releaseEntry(target, retention);
            if (previous != null) releaseEntry(previous, retention);
            disableOwner(owner, "immediate request " + key, failure);
            if (MeshRenderDebug.attributes != null || failure.getSuppressed().length != 0) throw failure;
            return MeshDrawResult.PENDING;
        }
        releaseEntry(target, retention);
        if (previous != null) releaseEntry(previous, retention);
        return result;
    }

    public static int attributeIndex(VertexFormat format, com.mojang.blaze3d.vertex.VertexFormatElement element) {
        List<com.mojang.blaze3d.vertex.VertexFormatElement> elements = format.getElements();
        for (int i = 0; i < elements.size(); i++) if (elements.get(i) == element) return i;
        return -1;
    }

    public static boolean draw(Entry entry, PoseStack pose, int packedLight, int packedOverlay) {
        MeshRenderDebug.DrawAttributes attributes = MeshRenderDebug.attributes;
        int previous = -1;
        boolean debugStarted = false;
        try {
            if (attributes != null) {
                attributes.beginItem();
                debugStarted = true;
            } else {
                previous = INTEGER_ATTRIBUTES.beginItem();
            }
            boolean drawn = drawParts(entry, pose, packedLight, packedOverlay, attributes);
            if (debugStarted) attributes.endItem();
            else INTEGER_ATTRIBUTES.endItem(previous);
            return drawn;
        } catch (RuntimeException exception) {
            try {
                if (debugStarted) attributes.endItem();
                else if (previous >= 0) INTEGER_ATTRIBUTES.endItem(previous);
            } catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
            disableOwner(entry.key.owner(), "immediate draw " + entry.key.geometry(), exception);
            if (attributes != null || exception.getSuppressed().length != 0) throw exception;
            return false;
        }
    }

    public static boolean drawParts(Entry entry, PoseStack pose, int packedLight, int packedOverlay,
                                     @Nullable MeshRenderDebug.DrawAttributes attributes) {
        MeshDrawTransform transform = new MeshDrawTransform(RenderSystem.getModelViewMatrix(), pose.last().pose());
        long lightOffset = MeshIntegerAttributes.parameterOffset(packedLight);
        long overlayOffset = MeshIntegerAttributes.parameterOffset(packedOverlay);
        MeshBatchRenderer batch = new MeshBatchRenderer(RenderSystem.getProjectionMatrix());
        try {
            for (Part part : entry.parts) {
                if (!batch.beginMaterial(part.material, transform.modelView)) {
                    throw new IllegalStateException("No active shader for " + part.material);
                }
                batch.bind(part.buffer);
                if (attributes != null) {
                    part.attributes.invalidate();
                    VertexFormat format = part.buffer.getFormat();
                    attributes.setupPart(part.buffer, format, part.instanceLight,
                            attributeIndex(format, VertexFormatElement.UV2),
                            attributeIndex(format, VertexFormatElement.UV1), packedLight, packedOverlay);
                } else {
                    INTEGER_ATTRIBUTES.setupPart(part.attributes, part.instanceLight, lightOffset, overlayOffset);
                }
                batch.drawItem(transform);
                batch.finishMaterial();
            }
            batch.close();
        } catch (RuntimeException failure) {
            batch.abort(failure);
            throw failure;
        }
        return true;
    }

}
