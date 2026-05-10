package com.github.mcmodderanchor.simplebedrockmodel.v1.client.compat.acceleratedrendering;

import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.IBufferGraph;
import com.github.argon4w.acceleratedrendering.core.meshes.IMesh;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Map;

public class AcceleratedBedrockBoneCache {
    final Map<IBufferGraph, IMesh> cubeMeshes = new Object2ObjectOpenHashMap<>();
    final Map<IBufferGraph, IMesh> polyMeshes = new Object2ObjectOpenHashMap<>();
}
