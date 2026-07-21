package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.AnimationBone;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockAnimationFile;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockAnimationPOJO;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public record BakerOptions(
        Set<String> animatedBones,
        Set<String> preservedBones,
        Set<Pattern> preservedBonePatterns,
        boolean bakeStaticGeometry,
        boolean debugFoldedTree,
        boolean retainCubeGeometry
) {
    public static final Set<String> ARMOR_BONES = Set.of(
            "armorHead",
            "armorBody",
            "armorRightArm",
            "armorLeftArm",
            "armorRightLeg",
            "armorLeftLeg",
            "armorRightBoot",
            "armorLeftBoot"
    );
    public static final BakerOptions ARMOR = new BakerOptions(Set.of(), ARMOR_BONES, true, false);

    public BakerOptions {
        animatedBones = Set.copyOf(animatedBones);
        preservedBones = Set.copyOf(preservedBones);
        preservedBonePatterns = Set.copyOf(preservedBonePatterns);
    }

    public BakerOptions(Set<String> animatedBones, Set<String> preservedBones, Set<Pattern> preservedBonePatterns,
                        boolean bakeStaticGeometry, boolean debugFoldedTree) {
        this(animatedBones, preservedBones, preservedBonePatterns, bakeStaticGeometry, debugFoldedTree, false);
    }

    public BakerOptions(Set<String> animatedBones, Set<String> preservedBones, boolean bakeStaticGeometry, boolean debugFoldedTree) {
        this(animatedBones, preservedBones, Set.of(), bakeStaticGeometry, debugFoldedTree, false);
    }

    public static BakerOptions defaults() {
        return new BakerOptions(Set.of(), Set.of(), Set.of(), true, false, false);
    }

    public static BakerOptions ofAnimatedBones(Set<String> animatedBones) {
        return new BakerOptions(animatedBones, Set.of(), Set.of(), true, false, false);
    }

    public static BakerOptions ofAnimationFile(BedrockAnimationFile animationFile) {
        return ofAnimatedBones(collectAnimatedBones(animationFile));
    }

    public static Set<String> collectAnimatedBones(BedrockAnimationFile animationFile) {
        if (animationFile == null || animationFile.getAnimations() == null || animationFile.getAnimations().isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (BedrockAnimationPOJO animation : animationFile.getAnimations().values()) {
            if (animation == null) {
                continue;
            }
            Map<String, AnimationBone> bones = animation.getBones();
            if (bones != null) {
                result.addAll(bones.keySet());
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public BakerOptions withPreservedBonePatterns(Set<Pattern> preservedBonePatterns) {
        return new BakerOptions(animatedBones, preservedBones, preservedBonePatterns, bakeStaticGeometry, debugFoldedTree, retainCubeGeometry);
    }

    public BakerOptions withPreservedBoneRegexes(Set<String> preservedBoneRegexes) {
        LinkedHashSet<Pattern> patterns = new LinkedHashSet<>();
        for (String regex : preservedBoneRegexes) {
            patterns.add(Pattern.compile(regex));
        }
        return withPreservedBonePatterns(patterns);
    }

    public BakerOptions withDebugFoldedTree(boolean debugFoldedTree) {
        return new BakerOptions(animatedBones, preservedBones, preservedBonePatterns, bakeStaticGeometry, debugFoldedTree, retainCubeGeometry);
    }

    public BakerOptions withRetainedCubeGeometry(boolean retainCubeGeometry) {
        return new BakerOptions(animatedBones, preservedBones, preservedBonePatterns, bakeStaticGeometry, debugFoldedTree, retainCubeGeometry);
    }
}
