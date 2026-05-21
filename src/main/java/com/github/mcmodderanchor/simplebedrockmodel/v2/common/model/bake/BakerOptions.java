package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.bake;

import java.util.Set;

public record BakerOptions(
        Set<String> animatedBones,
        Set<String> preservedBones,
        boolean bakeStaticGeometry,
        boolean debugFoldedTree
) {
    public BakerOptions {
        animatedBones = Set.copyOf(animatedBones);
        preservedBones = Set.copyOf(preservedBones);
    }

    public static BakerOptions defaults() {
        return new BakerOptions(Set.of(), Set.of(), true, false);
    }

    public static BakerOptions ofAnimatedBones(Set<String> animatedBones) {
        return new BakerOptions(animatedBones, Set.of(), true, false);
    }

    public BakerOptions withDebugFoldedTree(boolean debugFoldedTree) {
        return new BakerOptions(animatedBones, preservedBones, bakeStaticGeometry, debugFoldedTree);
    }
}
