package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.TreeMap;

/** 渲染线程上的光照修改暂存；范围以顶点编号表示，合并相邻／重叠区间。 */
public class LightRangeUpdates {
    private final ByteBuffer data;
    private final TreeMap<Integer, Integer> dirty = new TreeMap<>();

    public LightRangeUpdates(ByteBuffer data) {
        this.data = data;
    }

    public boolean set(int firstVertex, int vertexCount, int packedLight) {
        int size = this.data.capacity() / 4;
        if (firstVertex < 0 || vertexCount < 0 || firstVertex > size || vertexCount > size - firstVertex) {
            throw new IndexOutOfBoundsException("Light range outside mesh: " + firstVertex + " + " + vertexCount);
        }
        short low = (short) (packedLight & 0xFFFF);
        short high = (short) (packedLight >>> 16);
        int firstChanged = -1;
        int lastChanged = -1;
        for (int vertex = firstVertex, end = firstVertex + vertexCount; vertex < end; vertex++) {
            int offset = vertex * 4;
            if (this.data.getShort(offset) == low && this.data.getShort(offset + 2) == high) continue;
            this.data.putShort(offset, low);
            this.data.putShort(offset + 2, high);
            if (firstChanged < 0) firstChanged = vertex;
            lastChanged = vertex;
        }
        if (firstChanged < 0) return false;
        merge(firstChanged, lastChanged + 1);
        return true;
    }

    public void merge(int start, int end) {
        Map.Entry<Integer, Integer> before = this.dirty.floorEntry(start);
        if (before != null && before.getValue() >= start) {
            start = before.getKey();
            end = Math.max(end, before.getValue());
            this.dirty.remove(before.getKey());
        }
        Map.Entry<Integer, Integer> next;
        while ((next = this.dirty.ceilingEntry(start)) != null && next.getKey() <= end) {
            end = Math.max(end, next.getValue());
            this.dirty.remove(next.getKey());
        }
        this.dirty.put(start, end);
    }

    public Map<Integer, Integer> ranges() {
        return this.dirty;
    }

    public void clear() {
        this.dirty.clear();
    }
}
