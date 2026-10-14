package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

/** 根据捕获时的 UV2 将完整图元送往普通或固定发光 pass。 */
public class LightPassVertexRouter implements VertexConsumer {
    private final VertexConsumer ordinary;
    private final VertexConsumer emissive;
    private final int primitiveSize;
    private int primitiveVertices;
    private boolean primitiveEmissive;
    private float x, y, z;
    private boolean pending;
    private int red = 255, green = 255, blue = 255, alpha = 255;
    private float u, v;
    private int overlayU, overlayV, lightU, lightV;
    private float normalX, normalY, normalZ;

    public LightPassVertexRouter(VertexConsumer ordinary, VertexConsumer emissive, VertexFormat.Mode mode) {
        this.ordinary = ordinary;
        this.emissive = emissive;
        this.primitiveSize = switch (mode) {
            case QUADS -> 4;
            case TRIANGLES -> 3;
            default -> throw new IllegalArgumentException("Unsupported geometry mode: " + mode);
        };
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        flush();
        this.x = x; this.y = y; this.z = z;
        this.pending = true;
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        this.red = red; this.green = green; this.blue = blue; this.alpha = alpha;
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        this.u = u; this.v = v;
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        this.overlayU = u; this.overlayV = v;
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        this.lightU = u; this.lightV = v;
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        this.normalX = x; this.normalY = y; this.normalZ = z;
        return this;
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v,
                          int packedOverlay, int packedLight, float normalX, float normalY, float normalZ) {
        VertexConsumer.super.addVertex(x, y, z, color, u, v, packedOverlay, packedLight, normalX, normalY, normalZ);
        flush();
    }

    /** Submit the last chained vertex before the collector exposes its captured meshes. */
    public void finish() {
        flush();
    }

    private void flush() {
        if (!this.pending) return;
        boolean fixed = this.lightU != 0 || this.lightV != 0;
        if (this.primitiveVertices == 0) {
            this.primitiveEmissive = fixed;
        } else if (this.primitiveEmissive != fixed) {
            throw new IllegalArgumentException("Mixed light values within one mesh primitive");
        }

        VertexConsumer target = fixed ? this.emissive : this.ordinary;
        int color = (this.alpha << 24) | (this.red << 16) | (this.green << 8) | this.blue;
        target.addVertex(this.x, this.y, this.z, color, this.u, this.v,
                (this.overlayV << 16) | this.overlayU, (this.lightV << 16) | this.lightU,
                this.normalX, this.normalY, this.normalZ);

        this.pending = false;
        this.primitiveVertices = (this.primitiveVertices + 1) % this.primitiveSize;
        this.red = this.green = this.blue = this.alpha = 255;
        this.u = this.v = 0;
        this.overlayU = this.overlayV = this.lightU = this.lightV = 0;
        this.normalX = this.normalY = this.normalZ = 0;
    }
}
