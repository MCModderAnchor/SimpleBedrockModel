package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.MeshGeometryProvider;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache.MeshCachePolicy;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.MeshIntegerAttributes;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.render.MeshBatchRenderer;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.render.MeshDrawTransform;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.debug.MeshRenderDebug;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.StaticMeshRenderer.BatchOrder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache.StaticMeshCache.*;

/**
 * 属于调用方当前渲染阶段的临时命令。捕获在 submit 中完成；调用结束后仅保留矩阵副本
 * 和持有引用的几何。正常结束使用 endBatch；close 只取消命令。
 * 阶段所有者必须在改变共享着色器状态或渲染目标前排空队列。
 */
public class StaticMeshBufferSource implements AutoCloseable {
    public record Command(Entry entry, List<Part> parts, MeshDrawTransform transform,
                           int light, int overlay, Duration retention) {}
    public record QueuedPart(Command command, Part part) {}

    private final ResourceLocation owner;
    private final BatchOrder order;
    private final BooleanSupplier isCurrent;
    private final ClientLevel ownerLevel = Minecraft.getInstance().level;
    private final long ownerFrame = currentFrame();
    private final GLCapabilities context = GL.getCapabilities();
    private final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
    private final List<Command> commands = new ArrayList<>();
    private final List<QueuedPart> parts = new ArrayList<>();
    private long queuedGeneration = -1;
    private boolean closed;
    private boolean flushing;

    public StaticMeshBufferSource(ResourceLocation owner, BatchOrder order, BooleanSupplier isCurrent) {
        this.owner = owner;
        this.order = order;
        this.isCurrent = isCurrent;
    }

    public MeshSubmitResult submit(Object key, MeshGeometryProvider provider, PoseStack pose,
                                    int packedLight, int packedOverlay) {
        return submit(key, null, provider, pose, packedLight, packedOverlay,
                MeshCachePolicy.DEFAULT.idleRetention());
    }

    /** 旧几何只查询、不重新捕获，并且必须与当前捕获上下文兼容。 */
    public MeshSubmitResult submit(Object key, @Nullable Object previousKey, MeshGeometryProvider provider,
                                    PoseStack pose, int packedLight, int packedOverlay, Duration retention) {
        if (!isOwnerEnabled(owner)) return MeshSubmitResult.UNSUPPORTED;
        checkOpen();
        if (!samePass()) throw new IllegalStateException("Mesh batch outlived its render pass");
        if (MeshRenderDebug.attributes != null) throw new IllegalStateException("Batch uses production attributes");
        if (!prepareRequest(key, provider, pose, packedLight, packedOverlay, retention)) {
            return MeshSubmitResult.UNSUPPORTED;
        }
        if (queuedGeneration != -1 && queuedGeneration != generation) return MeshSubmitResult.PENDING;
        Entry target = acquire(new CacheKey(owner, key));
        Entry previous = previousKey == null || previousKey.equals(key) ? null
                : CACHE.get(new CacheKey(owner, previousKey));
        if (previous != null) pin(previous);
        MeshSubmitResult result;
        try {
            Preparation prepared = prepareTarget(target, provider);
            Entry selected = prepared == Preparation.READY ? target : null;
            if (selected == null && previous != null && previous.ready() && layoutSupported(previous)
                    && INTEGER_ATTRIBUTES.ensureReady()) selected = previous;
            if (selected == null) {
                result = prepared == Preparation.UNSUPPORTED ? MeshSubmitResult.UNSUPPORTED : MeshSubmitResult.PENDING;
            } else {
                Command command = new Command(selected, selected.parts,
                    new MeshDrawTransform(RenderSystem.getModelViewMatrix(), pose.last().pose()),
                    packedLight, packedOverlay, retention);
                pin(selected);
                commands.add(command);
                for (Part part : command.parts) parts.add(new QueuedPart(command, part));
                queuedGeneration = generation;
                result = selected == target ? MeshSubmitResult.QUEUED : MeshSubmitResult.QUEUED_PREVIOUS;
            }
        } catch (RuntimeException failure) {
            releaseEntry(target, retention);
            if (previous != null) releaseEntry(previous, retention);
            discard();
            disableOwner(owner, "batch submit " + key, failure);
            if (failure.getSuppressed().length != 0) throw failure;
            return MeshSubmitResult.PENDING;
        }
        releaseEntry(target, retention);
        if (previous != null) releaseEntry(previous, retention);
        return result;
    }

    /** 排空当前命令并保持批次开启；统计只覆盖本次 flush。 */
    public MeshFlushStats flush() {
        if (closed && !isOwnerEnabled(owner)) return new MeshFlushStats(0, 0, 0, 0, 0);
        checkOpen();
        flushing = true;
        int queued = commands.size();
        MeshBatchRenderer batch = null;
        int previousBuffer = -1;
        try {
            if (queued == 0) return finishFlush(new MeshFlushStats(0, 0, 0, 0, 0));
            if (!samePass() || queuedGeneration != generation) {
                return finishFlush(new MeshFlushStats(queued, 0, 0, 0, queued));
            }
            int discarded = 0;
            for (Command command : commands) if (command.entry.parts != command.parts) discarded++;
            parts.removeIf(part -> part.command.entry.parts != part.command.parts);
            if (parts.isEmpty()) return finishFlush(new MeshFlushStats(queued, 0, 0, 0, discarded));
            batch = new MeshBatchRenderer(projection);
            previousBuffer = INTEGER_ATTRIBUTES.beginItem();
            if (order == BatchOrder.MATERIAL) {
                Map<RenderType, Map<Part, List<QueuedPart>>> groups = new LinkedHashMap<>();
                for (QueuedPart part : parts) {
                    groups.computeIfAbsent(part.part.material, ignored -> new LinkedHashMap<>())
                            .computeIfAbsent(part.part, ignored -> new ArrayList<>()).add(part);
                }
                for (var material : groups.values()) {
                    for (var mesh : material.values()) for (QueuedPart part : mesh) draw(part, batch);
                }
            } else {
                for (QueuedPart part : parts) draw(part, batch);
            }
            batch.close();
            INTEGER_ATTRIBUTES.endItem(previousBuffer);
            return finishFlush(new MeshFlushStats(queued, batch.draws(), batch.setups(), batch.binds(), discarded));
        } catch (RuntimeException exception) {
            closed = true;
            if (batch != null) batch.abort(exception);
            if (previousBuffer >= 0) {
                try { INTEGER_ATTRIBUTES.endItem(previousBuffer); }
                catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
            }
            try { releaseCommands(); }
            catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
            flushing = false;
            disableOwner(owner, "batch flush", exception);
            if (exception.getSuppressed().length != 0) throw exception;
            return new MeshFlushStats(queued, batch == null ? 0 : batch.draws(),
                    batch == null ? 0 : batch.setups(), batch == null ? 0 : batch.binds(), queued);
        }
    }

    private MeshFlushStats finishFlush(MeshFlushStats stats) {
        releaseCommands();
        flushing = false;
        return stats;
    }

    /** 排空一次后关闭批次；绘制失败时同样关闭。 */
    public MeshFlushStats endBatch() {
        MeshFlushStats stats = flush();
        closed = true;
        return stats;
    }

    public void draw(QueuedPart queued, MeshBatchRenderer batch) {
        Command command = queued.command;
        if (!samePass() || queuedGeneration != generation || command.entry.parts != command.parts) return;
        if (!batch.beginMaterial(queued.part.material, command.transform.modelView)) {
            throw new IllegalStateException("No active shader for " + queued.part.material);
        }
        batch.bind(queued.part.buffer);
        if (!samePass() || queuedGeneration != generation || command.entry.parts != command.parts) {
            return;
        }
        INTEGER_ATTRIBUTES.setupPart(queued.part.attributes, queued.part.instanceLight,
                MeshIntegerAttributes.parameterOffset(command.light),
                MeshIntegerAttributes.parameterOffset(command.overlay));
        batch.drawItem(command.transform);
    }

    public boolean samePass() {
        return ownerFrame == currentFrame() && ownerLevel == Minecraft.getInstance().level && context == GL.getCapabilities()
                && projection.equals(RenderSystem.getProjectionMatrix()) && isCurrent.getAsBoolean();
    }

    public void checkOpen() {
        RenderSystem.assertOnRenderThread();
        if (closed || flushing) throw new IllegalStateException("Mesh batch is closed or flushing");
    }

    public void releaseCommands() {
        for (Command command : commands) releaseEntry(command.entry, command.retention);
        commands.clear();
        parts.clear();
        queuedGeneration = -1;
    }

    /** 幂等取消未完成的命令；异常退出清理期间不执行绘制。 */
    public void discard() {
        RenderSystem.assertOnRenderThread();
        if (flushing) throw new IllegalStateException("Cannot discard a flushing mesh batch");
        if (closed) return;
        closed = true;
        releaseCommands();
    }

    @Override public void close() { discard(); }
}
