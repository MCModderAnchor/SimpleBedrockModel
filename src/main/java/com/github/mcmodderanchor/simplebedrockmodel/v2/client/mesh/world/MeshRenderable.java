package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.GeometryCollector;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** 可直接 track 的对象；库读取属性并按需调用几何收集，不要求调用方构造状态包装。 */
public interface MeshRenderable {
    default boolean isValid() {
        return true;
    }

    /** 每客户端 tick 至多检查一次；markDirty 与资源重载会绕过此判断。 */
    default boolean needsUpdate() {
        return true;
    }

    default boolean isVisible() {
        return true;
    }

    public Vec3 origin();

    /** 相对 origin 的实例局部变换；默认恒等。 */
    default Matrix4f localTransform() { return new Matrix4f(); }

    public int packedLight();

    /** 不可变共享键，包含模型、材质、拓扑、局部姿势／显隐等影响捕获结果的状态。 */
    public Object geometryKey();

    /** 仅首次捕获、key 变化或资源失效时调用；资源暂不可用返回 false。不要保留 collector。 */
    public boolean collectGeometry(GeometryCollector collector);
}
