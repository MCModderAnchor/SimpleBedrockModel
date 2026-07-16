package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked;

import org.joml.Vector3fc;

import java.util.Arrays;
import java.util.LinkedHashSet;

public class BakedGeometryChunkBuilder {
    private final FloatBuffer quadPositions = new FloatBuffer();
    private final FloatBuffer quadNormals = new FloatBuffer();
    private final FloatBuffer quadUvs = new FloatBuffer();
    private final FloatBuffer vertexPositions = new FloatBuffer();
    private final FloatBuffer vertexNormals = new FloatBuffer();
    private final FloatBuffer vertexUvs = new FloatBuffer();
    private final LinkedHashSet<String> sourceBones = new LinkedHashSet<>();
    private int quadCount;
    private int vertexCount;

    void addSourceBone(String sourceBone) {
        sourceBones.add(sourceBone);
    }

    void addQuad(Vector3fc p0, Vector3fc p1, Vector3fc p2, Vector3fc p3, Vector3fc normal,
                 float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3) {
        quadPositions.add(p0.x(), p0.y(), p0.z());
        quadPositions.add(p1.x(), p1.y(), p1.z());
        quadPositions.add(p2.x(), p2.y(), p2.z());
        quadPositions.add(p3.x(), p3.y(), p3.z());
        quadNormals.add(normal.x(), normal.y(), normal.z());
        quadUvs.add(u0, v0);
        quadUvs.add(u1, v1);
        quadUvs.add(u2, v2);
        quadUvs.add(u3, v3);
        quadCount++;
    }

    void addVertex(Vector3fc position, Vector3fc normal, float u, float v) {
        vertexPositions.add(position.x(), position.y(), position.z());
        vertexNormals.add(normal.x(), normal.y(), normal.z());
        vertexUvs.add(u, v);
        vertexCount++;
    }

    boolean isEmpty() {
        return quadCount == 0 && vertexCount == 0;
    }

    BakedGeometryChunk toChunk(int attachBoneIndex) {
        return new BakedGeometryChunk(attachBoneIndex,
                new BakedQuadData(quadPositions.toArray(), quadNormals.toArray(), quadUvs.toArray(), quadCount),
                new BakedVertexData(vertexPositions.toArray(), vertexNormals.toArray(), vertexUvs.toArray(), vertexCount),
                sourceBones.toArray(String[]::new));
    }

    void add(BakedGeometryChunkBuilder other) {
        this.quadPositions.addAll(other.quadPositions);
        this.quadNormals.addAll(other.quadNormals);
        this.quadUvs.addAll(other.quadUvs);
        this.vertexPositions.addAll(other.vertexPositions);
        this.vertexNormals.addAll(other.vertexNormals);
        this.vertexUvs.addAll(other.vertexUvs);
        this.sourceBones.addAll(other.sourceBones);
        this.quadCount += other.quadCount;
        this.vertexCount += other.vertexCount;
    }

    private static final class FloatBuffer {
        private float[] values = new float[32];
        private int size;

        private void add(float first, float second) {
            ensureCapacity(size + 2);
            values[size++] = first;
            values[size++] = second;
        }

        private void add(float first, float second, float third) {
            ensureCapacity(size + 3);
            values[size++] = first;
            values[size++] = second;
            values[size++] = third;
        }

        private void addAll(FloatBuffer other) {
            ensureCapacity(size + other.size);
            System.arraycopy(other.values, 0, values, size, other.size);
            size += other.size;
        }

        private void ensureCapacity(int capacity) {
            if (capacity <= values.length) {
                return;
            }
            int newCapacity = values.length;
            while (newCapacity < capacity) {
                newCapacity *= 2;
            }
            values = Arrays.copyOf(values, newCapacity);
        }

        private float[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }
}
