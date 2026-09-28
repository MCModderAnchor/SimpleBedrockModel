package com.github.mcmodderanchor.simplebedrockmodel.v2.client.world;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 打包顶点捕获：把 {@link VertexConsumer} 调用写进扁平数组，不触碰 GL 与
 * BufferBuilder。同一个实例不可并发访问；后台烘焙须使用独立的模型状态。
 * 回放线程要求由目标 {@link VertexConsumer} 决定。
 *
 * <p>顶点格式与 draw mode 由世界网格绘制层钉死：{@link DefaultVertexFormat#NEW_ENTITY}（36 B/顶点），
 * 只允许 {@link VertexFormat.Mode#QUADS} 与 {@link VertexFormat.Mode#TRIANGLES}。上层缓冲池按格式分桶，
 * 所以格式不能由调用方自带。</p>
 *
 * <p>法线按共享网格的局部空间捕获，不含相机视图旋转。INSTANCE 绘制会把世界光源方向
 * 变换到局部空间；SECTION 拼接时把法线变换到世界空间。</p>
 */
@OnlyIn(Dist.CLIENT)
public class MeshSink implements VertexConsumer {
    public static final VertexFormat FORMAT = DefaultVertexFormat.NEW_ENTITY;
    public static final int VERTEX_STRIDE_BYTES = FORMAT.getVertexSize();

    private static final int MIN_VERTICES = 64;
    private static final Matrix4f IDENTITY = new Matrix4f();

    private VertexFormat.Mode mode = VertexFormat.Mode.QUADS;
    private int vertexCount;

    private float[] positions = new float[MIN_VERTICES * 3];
    private int[] colors = new int[MIN_VERTICES];
    private float[] uvs = new float[MIN_VERTICES * 2];
    private int[] overlays = new int[MIN_VERTICES];
    private int[] lights = new int[MIN_VERTICES];
    private float[] normals = new float[MIN_VERTICES * 3];

    private float x;
    private float y;
    private float z;
    private float u;
    private float v;
    private float nx;
    private float ny;
    private float nz;
    private int color = -1;
    private int overlay;
    private int light;
    private boolean pending;

    /** 光照运行段（RLE）：同一光照值 + 顶点连续时合并，通常整块 mesh 只有一段。 */
    private int[] lightRunStarts = new int[4];
    private int[] lightRunLengths = new int[4];
    private int[] lightRunValues = new int[4];
    private int lightRunCount;

    /** 清空并指定绘制模式。只接受 QUADS / TRIANGLES。 */
    public MeshSink begin(VertexFormat.Mode mode) {
        if (mode != VertexFormat.Mode.QUADS && mode != VertexFormat.Mode.TRIANGLES) {
            throw new IllegalArgumentException("Unsupported static mesh mode: " + mode);
        }
        this.mode = mode;
        this.vertexCount = 0;
        this.pending = false;
        this.lightRunCount = 0;
        return this;
    }

    public VertexFormat.Mode mode() {
        return this.mode;
    }

    public VertexFormat format() {
        return FORMAT;
    }

    public int vertexCount() {
        return this.vertexCount + (this.pending ? 1 : 0);
    }

    public boolean isEmpty() {
        return this.vertexCount() == 0;
    }

    /** 上传预算按字节估算用（顶点数 × 36）。 */
    public int estimatedBytes() {
        return this.vertexCount() * VERTEX_STRIDE_BYTES;
    }

    /** 已捕获顶点在烘焙坐标系下的包围盒。 */
    public AABB bounds() {
        this.flush();
        if (this.vertexCount == 0) {
            return new AABB(0, 0, 0, 0, 0, 0);
        }
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < this.vertexCount; i++) {
            float px = this.positions[i * 3];
            float py = this.positions[i * 3 + 1];
            float pz = this.positions[i * 3 + 2];
            minX = Math.min(minX, px);
            minY = Math.min(minY, py);
            minZ = Math.min(minZ, pz);
            maxX = Math.max(maxX, px);
            maxY = Math.max(maxY, py);
            maxZ = Math.max(maxZ, pz);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * 光照运行段数量。实例光照模式用它构造独立光照流，不改写几何缓冲。
     */
    public int lightRunCount() {
        this.flush();
        return this.lightRunCount;
    }

    public int lightRunStart(int index) {
        this.flush();
        return this.lightRunStarts[index];
    }

    public int lightRunLength(int index) {
        this.flush();
        return this.lightRunLengths[index];
    }

    public int lightRunValue(int index) {
        this.flush();
        return this.lightRunValues[index];
    }

    /** 回放到目标 consumer；线程约束由目标决定。 */
    public void emitTo(VertexConsumer out) {
        emitTo(out, 0, 0, 0);
    }

    /** 以局部平移追加捕获结果；用于 section 拼接，不改变法线和光照模板。 */
    public void emitTo(VertexConsumer out, double x, double y, double z) {
        this.flush();
        for (int i = 0; i < this.vertexCount; i++) {
            int p = i * 3;
            int t = i * 2;
            out.vertex(this.positions[p] + x, this.positions[p + 1] + y, this.positions[p + 2] + z)
                    .color(this.colors[i])
                    .uv(this.uvs[t], this.uvs[t + 1])
                    .overlayCoords(this.overlays[i])
                    .uv2(this.lights[i])
                    .normal(this.normals[p], this.normals[p + 1], this.normals[p + 2])
                    .endVertex();
        }
    }

    /** SECTION 拼接时应用实例局部变换，再加上相对 section 的平移。 */
    public void emitTo(VertexConsumer out, Matrix4f transform, double x, double y, double z) {
        if (transform.equals(IDENTITY)) {
            emitTo(out, x, y, z);
            return;
        }
        this.flush();
        Matrix3f normalMatrix = new Matrix3f(transform);
        float determinant = normalMatrix.determinant();
        if (Float.isFinite(determinant) && Math.abs(determinant) > 1.0E-10F) normalMatrix.invert().transpose();
        else normalMatrix.identity();
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        for (int i = 0; i < this.vertexCount; i++) {
            int p = i * 3;
            int t = i * 2;
            position.set(this.positions[p], this.positions[p + 1], this.positions[p + 2]).mulPosition(transform);
            normal.set(this.normals[p], this.normals[p + 1], this.normals[p + 2]).mul(normalMatrix);
            if (normal.lengthSquared() > 1.0E-12F) normal.normalize();
            out.vertex(position.x + x, position.y + y, position.z + z)
                    .color(this.colors[i]).uv(this.uvs[t], this.uvs[t + 1])
                    .overlayCoords(this.overlays[i]).uv2(this.lights[i])
                    .normal(normal.x, normal.y, normal.z).endVertex();
        }
    }

    @Override
    public VertexConsumer vertex(double px, double py, double pz) {
        this.flush();
        this.x = (float) px;
        this.y = (float) py;
        this.z = (float) pz;
        this.pending = true;
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        this.color = (alpha << 24) | (red << 16) | (green << 8) | blue;
        return this;
    }

    @Override
    public VertexConsumer uv(float pu, float pv) {
        this.u = pu;
        this.v = pv;
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int pu, int pv) {
        this.overlay = (pv << 16) | pu;
        return this;
    }

    @Override
    public VertexConsumer uv2(int pu, int pv) {
        this.light = (pv << 16) | pu;
        return this;
    }

    @Override
    public VertexConsumer normal(float pnx, float pny, float pnz) {
        this.nx = pnx;
        this.ny = pny;
        this.nz = pnz;
        return this;
    }

    @Override
    public void endVertex() {
        this.flush();
    }

    @Override
    public void defaultColor(int red, int green, int blue, int alpha) {
        this.color(red, green, blue, alpha);
    }

    @Override
    public void unsetDefaultColor() {
        this.color = -1;
    }

    private void flush() {
        if (!this.pending) {
            return;
        }
        this.ensure(this.vertexCount + 1);
        int p = this.vertexCount * 3;
        int t = this.vertexCount * 2;
        this.positions[p] = this.x;
        this.positions[p + 1] = this.y;
        this.positions[p + 2] = this.z;
        this.colors[this.vertexCount] = this.color;
        this.uvs[t] = this.u;
        this.uvs[t + 1] = this.v;
        this.overlays[this.vertexCount] = this.overlay;
        this.lights[this.vertexCount] = this.light;
        this.normals[p] = this.nx;
        this.normals[p + 1] = this.ny;
        this.normals[p + 2] = this.nz;
        this.vertexCount++;
        recordLightRun(this.vertexCount - 1, this.light);
        this.pending = false;
    }

    private void recordLightRun(int vertex, int value) {
        if (this.lightRunCount > 0) {
            int last = this.lightRunCount - 1;
            if (this.lightRunValues[last] == value
                    && this.lightRunStarts[last] + this.lightRunLengths[last] == vertex) {
                this.lightRunLengths[last]++;
                return;
            }
        }
        if (this.lightRunCount == this.lightRunStarts.length) {
            int capacity = this.lightRunStarts.length * 2;
            this.lightRunStarts = grow(this.lightRunStarts, capacity, this.lightRunCount);
            this.lightRunLengths = grow(this.lightRunLengths, capacity, this.lightRunCount);
            this.lightRunValues = grow(this.lightRunValues, capacity, this.lightRunCount);
        }
        this.lightRunStarts[this.lightRunCount] = vertex;
        this.lightRunLengths[this.lightRunCount] = 1;
        this.lightRunValues[this.lightRunCount] = value;
        this.lightRunCount++;
    }

    private void ensure(int count) {
        if (count <= this.colors.length) {
            return;
        }
        int capacity = this.colors.length;
        while (capacity < count) {
            capacity *= 2;
        }
        this.positions = grow(this.positions, capacity * 3, this.vertexCount * 3);
        this.colors = grow(this.colors, capacity, this.vertexCount);
        this.uvs = grow(this.uvs, capacity * 2, this.vertexCount * 2);
        this.overlays = grow(this.overlays, capacity, this.vertexCount);
        this.lights = grow(this.lights, capacity, this.vertexCount);
        this.normals = grow(this.normals, capacity * 3, this.vertexCount * 3);
    }

    private static float[] grow(float[] source, int length, int used) {
        float[] result = new float[length];
        System.arraycopy(source, 0, result, 0, used);
        return result;
    }

    private static int[] grow(int[] source, int length, int used) {
        int[] result = new int[length];
        System.arraycopy(source, 0, result, 0, used);
        return result;
    }
}
