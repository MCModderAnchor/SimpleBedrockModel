package com.github.mcmodderanchor.simplebedrockmodel.v2.client.compat.acceleratedrendering;

import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.IBufferGraph;
import com.github.argon4w.acceleratedrendering.core.meshes.IMesh;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Map;

public final class AcceleratedBedrockGeometryCache {
    final Map<IBufferGraph, IMesh> quadMeshes = new Object2ObjectOpenHashMap<>();
    final Map<IBufferGraph, IMesh> triangleMeshes = new Object2ObjectOpenHashMap<>();
}
