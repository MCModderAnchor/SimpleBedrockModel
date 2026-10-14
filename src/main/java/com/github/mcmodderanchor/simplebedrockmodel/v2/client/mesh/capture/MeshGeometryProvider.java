package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture;

/** 仅在缺少几何时，于渲染线程同步调用。 */
@FunctionalInterface
public interface MeshGeometryProvider {
    public boolean capture(GeometryCollector collector);
}
