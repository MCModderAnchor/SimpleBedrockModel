package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache;

import java.util.HashSet;
import java.util.Set;

/** 首次故障后关闭对应路径，只有明确的生命周期重置才重新允许。 */
public class MeshPathFailures<K> {
    private final Set<K> disabled = new HashSet<>();

    public boolean enabled(K key) { return !disabled.contains(key); }
    public boolean disable(K key) { return disabled.add(key); }
    public void reset() { disabled.clear(); }
}
