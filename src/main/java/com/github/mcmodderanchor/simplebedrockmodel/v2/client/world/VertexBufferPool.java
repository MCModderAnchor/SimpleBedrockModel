package com.github.mcmodderanchor.simplebedrockmodel.v2.client.world;

import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * 静态缓冲池。按顶点格式分桶：{@code VertexBuffer.upload} 在格式变化时会先 clear 再 setup 当前 VAO 的
 * 属性指针，跨格式复用会互相破坏 VAO 状态，所以桶必须按格式隔离。
 *
 * <p>池只回收"整个 VertexBuffer"，不做子分配。所有方法都必须在渲染线程调用。</p>
 */
@OnlyIn(Dist.CLIENT)
final class VertexBufferPool {
    private final Map<VertexFormat, ArrayDeque<VertexBuffer>> idle = new HashMap<>();
    private int created;
    private int reused;

    VertexBuffer acquire(VertexFormat format) {
        ArrayDeque<VertexBuffer> bucket = this.idle.get(format);
        VertexBuffer buffer = bucket == null ? null : bucket.pollLast();
        if (buffer == null) {
            this.created++;
            return new VertexBuffer(VertexBuffer.Usage.STATIC);
        }
        this.reused++;
        return buffer;
    }

    void recycle(VertexFormat format, VertexBuffer buffer) {
        this.idle.computeIfAbsent(format, ignored -> new ArrayDeque<>()).addLast(buffer);
    }

    void clear() {
        for (ArrayDeque<VertexBuffer> bucket : this.idle.values()) {
            for (VertexBuffer buffer : bucket) {
                buffer.close();
            }
        }
        this.idle.clear();
    }

    int created() {
        return this.created;
    }

    int reused() {
        return this.reused;
    }

    int idleCount() {
        int count = 0;
        for (ArrayDeque<VertexBuffer> bucket : this.idle.values()) {
            count += bucket.size();
        }
        return count;
    }
}
