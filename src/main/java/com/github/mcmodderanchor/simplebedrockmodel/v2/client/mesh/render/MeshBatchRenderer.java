package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.render;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.nio.FloatBuffer;

/** 管理每组材质、着色器及 VAO 的绘制状态；实例属性仍由对应几何的所有者管理。 */
public class MeshBatchRenderer implements AutoCloseable {
    /**
     * 世界空间光源方向，取自 {@code com.mojang.blaze3d.platform.Lighting}（那边是 private 常量）。
     * 静态网格的法线按世界朝向烘焙，所以必须用世界方向，不能复用 {@link RenderSystem#setupShaderLights}。
     */
    private static final Vector3f LIGHT_0_OVERWORLD = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
    private static final Vector3f LIGHT_1_OVERWORLD = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();
    private static final Vector3f LIGHT_0_NETHER = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
    private static final Vector3f LIGHT_1_NETHER = new Vector3f(-0.2F, -1.0F, 0.7F).normalize();

    private final Matrix3f inverseLinear = new Matrix3f();
    private final Matrix3f lastLightingLinear = new Matrix3f();
    private final Vector3f light0 = new Vector3f();
    private final Vector3f light1 = new Vector3f();
    private boolean lightingLinearCached;

    private final Matrix4f projection;
    private RenderType material;
    private ShaderInstance shader;
    private VertexBuffer bound;
    private int draws, setups, binds;

    public MeshBatchRenderer(Matrix4f projection) { this.projection = projection; }

    public boolean beginMaterial(RenderType next, Matrix4f modelView) {
        if (material == next) return shader != null;
        finishMaterial();
        material = next; // 状态设置本身失败时也必须清理。
        setups++;
        next.setupRenderState();
        shader = RenderSystem.getShader();
        lightingLinearCached = false;
        if (shader == null) return false;
        uploadSharedUniforms(shader, modelView, projection);
        return true;
    }

    public void bind(VertexBuffer buffer) {
        if (bound == buffer) return;
        buffer.bind();
        bound = buffer;
        binds++;
    }

    public void drawItem(MeshDrawTransform transform) {
        uploadModelView(transform.modelView);
        uploadItemLighting(shader, transform.inverseLinear);
        bound.draw();
        draws++;
    }

    public void drawWorld(Matrix4f modelView, Matrix4f localTransform) {
        uploadModelView(modelView);
        uploadInstanceLighting(shader, localTransform);
        bound.draw();
        draws++;
    }

    public void uploadModelView(Matrix4f matrix) {
        if (shader.MODEL_VIEW_MATRIX != null) {
            shader.MODEL_VIEW_MATRIX.set(matrix);
            shader.MODEL_VIEW_MATRIX.upload();
        }
    }

    public int draws() { return draws; }
    public int setups() { return setups; }
    public int binds() { return binds; }

    public void finishMaterial() {
        RenderType previous = material;
        ShaderInstance previousShader = shader;
        if (previous == null) return;
        if (previousShader != null) previousShader.clear();
        VertexBuffer.unbind();
        previous.clearRenderState();
        material = null;
        shader = null;
        bound = null;
    }

    /** 仅首次异常收尾；保留清理错误到原异常，交给边界日志和路径禁用处理。 */
    public void abort(RuntimeException failure) {
        RenderType previous = material;
        ShaderInstance previousShader = shader;
        material = null;
        shader = null;
        bound = null;
        if (previousShader != null) {
            try { previousShader.clear(); }
            catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
        }
        try { VertexBuffer.unbind(); }
        catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
        if (previous != null) {
            try { previous.clearRenderState(); }
            catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
        }
    }

    @Override public void close() { finishMaterial(); }

    public void uploadInstanceLighting(ShaderInstance shader, Matrix4f transform) {
        if (shader.LIGHT0_DIRECTION == null && shader.LIGHT1_DIRECTION == null) return;
        inverseLinear.set(transform);
        if (lightingLinearCached && inverseLinear.equals(lastLightingLinear)) return;
        lastLightingLinear.set(inverseLinear);
        lightingLinearCached = true;
        float determinant = inverseLinear.determinant();
        if (Float.isFinite(determinant) && Math.abs(determinant) > 1.0E-10F) inverseLinear.invert();
        else inverseLinear.identity();
        boolean constantAmbient = Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.effects().constantAmbientLight();
        if (shader.LIGHT0_DIRECTION != null) {
            light0.set(constantAmbient ? LIGHT_0_NETHER : LIGHT_0_OVERWORLD)
                    .mul(inverseLinear).normalize();
            shader.LIGHT0_DIRECTION.set(light0);
            shader.LIGHT0_DIRECTION.upload();
        }
        if (shader.LIGHT1_DIRECTION != null) {
            light1.set(constantAmbient ? LIGHT_1_NETHER : LIGHT_1_OVERWORLD)
                    .mul(inverseLinear).normalize();
            shader.LIGHT1_DIRECTION.set(light1);
            shader.LIGHT1_DIRECTION.upload();
        }
    }

    public static void uploadItemLighting(ShaderInstance shader, Matrix3f inverseLinear) {
        if (shader.LIGHT0_DIRECTION == null && shader.LIGHT1_DIRECTION == null) return;
        RenderSystem.setupShaderLights(shader);
        uploadLocalLight(shader.LIGHT0_DIRECTION, inverseLinear);
        uploadLocalLight(shader.LIGHT1_DIRECTION, inverseLinear);
    }

    public static void uploadLocalLight(Uniform uniform, Matrix3f inverseLinear) {
        if (uniform == null) return;
        FloatBuffer values = uniform.getFloatBuffer();
        Vector3f light = new Vector3f(values.get(0), values.get(1), values.get(2))
                .mul(inverseLinear);
        if (light.lengthSquared() > 1.0E-12F) light.normalize();
        uniform.set(light);
        uniform.upload();
    }

    public static void uploadSharedUniforms(ShaderInstance shader, Matrix4f baseView, Matrix4f projection) {
        // Both supported topologies use the same default uniforms; neither is a line mode.
        shader.setDefaultUniforms(VertexFormat.Mode.QUADS, baseView, projection, Minecraft.getInstance().getWindow());

        boolean constantAmbient = Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.effects().constantAmbientLight();
        if (shader.LIGHT0_DIRECTION != null) {
            shader.LIGHT0_DIRECTION.set(constantAmbient ? LIGHT_0_NETHER : LIGHT_0_OVERWORLD);
        }
        if (shader.LIGHT1_DIRECTION != null) {
            shader.LIGHT1_DIRECTION.set(constantAmbient ? LIGHT_1_NETHER : LIGHT_1_OVERWORLD);
        }
        shader.apply();
    }

}
