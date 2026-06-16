package com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.BoneIndexProvider;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.ParticleEffectDataKeyframe;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.ResourceLocationKeyframe;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.TimelineEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.TimelineKeyframe;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.molang.MolangEngineHelper;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.*;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.MochaEngine;
import com.maydaymemory.mae.basic.*;
import it.unimi.dsi.fastutil.doubles.Double2ObjectMap;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BedrockAnimation extends BasicAnimation {
    private static final float DEGREE_TO_ANGLE = (float) (Math.PI / 180);
    public static final String SOUND_CHANNEL_NAME = "sound_effects";
    public static final String PARTICLE_CHANNEL_NAME = "particle_effects";
    public static final String TIMELINE_CHANNEL_NAME = "timeline";

    private float specifiedEndTimeS = -1;

    public BedrockAnimation(String name) {
        super(name, new ZYXBoneTransformFactory(), ArrayPoseBuilder::new);
    }

    public void setSpecifiedEndTimeS(float specifiedEndTimeS) {
        this.specifiedEndTimeS = specifiedEndTimeS;
    }

    public float getSpecifiedEndTimeS() {
        return specifiedEndTimeS;
    }

    public static BedrockAnimation createAnimation(String name, BedrockAnimationPOJO pojo, @Nullable BoneIndexProvider indexProvider) {
        return createAnimation(name, pojo, indexProvider, null);
    }

    public static BedrockAnimation createAnimation(String name, BedrockAnimationPOJO pojo,
                                                    @Nullable BoneIndexProvider indexProvider,
                                                    @Nullable MochaEngine<?> molangEngine) {
        BedrockAnimation animation = new BedrockAnimation(name);
        if (pojo.getBones() != null && indexProvider != null) {
            for (Map.Entry<String, AnimationBone> entry : pojo.getBones().entrySet()) {
                int boneIndex = indexProvider.getIndex(entry.getKey());
                AnimationBone bone = entry.getValue();
                if (boneIndex >= 0) {
                    ArrayInterpolatableChannel<Vector3fc> translationChannel = parseChannel(bone.getPosition(), -1, 1, 1, molangEngine);
                    ArrayInterpolatableChannel<Rotation> rotationChannel = parseRotationChannel(bone.getRotation(), -1, -1, 1, molangEngine);
                    ArrayInterpolatableChannel<Vector3fc> scaleChannel = parseChannel(bone.getScale(), 1, 1, 1, molangEngine);
                    animation.setTranslationChannel(boneIndex, translationChannel);
                    animation.setRotationChannel(boneIndex, rotationChannel);
                    animation.setScaleChannel(boneIndex, scaleChannel);
                }
            }
        }
        SoundEffectKeyframes soundEffects = pojo.getSoundEffects();
        if (soundEffects != null && soundEffects.getKeyframes() != null) {
            ArrayList<Keyframe<ResourceLocation>> keyframes = new ArrayList<>();
            for (Double2ObjectMap.Entry<ResourceLocation> entry : soundEffects.getKeyframes().double2ObjectEntrySet()) {
                keyframes.add(new ResourceLocationKeyframe((float) entry.getDoubleKey(), entry.getValue()));
            }
            animation.setClipChannel(SOUND_CHANNEL_NAME, new ArrayClipChannel<>(keyframes));
        }
        ParticleEffectKeyframes particleEffects = pojo.getParticleEffects();
        if (particleEffects != null && particleEffects.getKeyframes() != null) {
            ArrayList<Keyframe<ParticleEffectData>> keyframes = new ArrayList<>();
            for (Double2ObjectMap.Entry<ParticleEffectData> entry : particleEffects.getKeyframes().double2ObjectEntrySet()) {
                keyframes.add(new ParticleEffectDataKeyframe((float) entry.getDoubleKey(), entry.getValue()));
            }
            animation.setClipChannel(PARTICLE_CHANNEL_NAME, new ArrayClipChannel<>(keyframes));
        }
        TimelineKeyframes timeline = pojo.getTimeline();
        if (timeline != null && timeline.getKeyframes() != null && molangEngine != null) {
            ArrayList<Keyframe<TimelineEvent>> keyframes = new ArrayList<>();
            for (Double2ObjectMap.Entry<List<String>> entry : timeline.getKeyframes().double2ObjectEntrySet()) {
                List<String> raw = entry.getValue();
                List<MolangExpression> compiled = new ArrayList<>(raw.size());
                for (String command : raw) {
                    compiled.add(MolangEngineHelper.compileExpression(molangEngine, command));
                }
                keyframes.add(new TimelineKeyframe((float) entry.getDoubleKey(), new TimelineEvent(raw, compiled)));
            }
            if (!keyframes.isEmpty()) {
                animation.setClipChannel(TIMELINE_CHANNEL_NAME, new ArrayClipChannel<>(keyframes));
            }
        }
        float animationLength = (float) pojo.getAnimationLength();
        animation.setSpecifiedEndTimeS(animationLength);
        return animation;
    }

    public static List<BedrockAnimation> createAnimation(BedrockAnimationFile pojo, @Nullable BoneIndexProvider indexProvider) {
        return createAnimation(pojo, indexProvider, null);
    }

    public static List<BedrockAnimation> createAnimation(BedrockAnimationFile pojo,
                                                          @Nullable BoneIndexProvider indexProvider,
                                                          @Nullable MochaEngine<?> molangEngine) {
        List<BedrockAnimation> animations = new ArrayList<>();
        if (pojo.getAnimations() != null) {
            for (Map.Entry<String, BedrockAnimationPOJO> entry : pojo.getAnimations().entrySet()) {
                animations.add(createAnimation(entry.getKey(), entry.getValue(), indexProvider, molangEngine));
            }
        }
        return animations;
    }

    private static ArrayInterpolatableChannel<Rotation> parseRotationChannel(AnimationKeyframes keyframes,
                                                                             float x, float y, float z,
                                                                             @Nullable MochaEngine<?> molangEngine) {
        if (keyframes == null) {
            return null;
        }
        ArrayList<InterpolatableKeyframe<Rotation>> array = new ArrayList<>();
        keyframes.getKeyframes().forEach((timeS, keyframe) -> {
            array.add(parseRotationKeyframe((float) (double)timeS, keyframe, x, y, z, molangEngine));
        });
        return new ArrayInterpolatableChannel<>(array);
    }

    private static InterpolatableKeyframe<Rotation> parseRotationKeyframe(float timeS, AnimationKeyframes.Keyframe keyframe,
                                                                          float x, float y, float z,
                                                                          @Nullable MochaEngine<?> molangEngine) {
        if (keyframe.hasMolang() && molangEngine != null) {
            Interpolator<Vector3fc> vecInterpolator;
            if ("catmullrom".equals(keyframe.getLerpMode())) {
                vecInterpolator = Vector3fCubicSplineInterpolator.INSTANCE;
            } else {
                vecInterpolator = Vector3fLinearInterpolator.INSTANCE;
            }
            MolangExpression[] preFunctions;
            MolangExpression[] postFunctions;
            if (keyframe.getDataExpressions() != null) {
                preFunctions = postFunctions = compileMolangExpressions(molangEngine, keyframe.getDataExpressions());
            } else {
                String[] preExprs = keyframe.getPreExpressions() != null ? keyframe.getPreExpressions() : keyframe.getPostExpressions();
                String[] postExprs = keyframe.getPostExpressions() != null ? keyframe.getPostExpressions() : keyframe.getPreExpressions();
                preFunctions = compileMolangExpressions(molangEngine, preExprs);
                postFunctions = (preExprs == postExprs) ? preFunctions : compileMolangExpressions(molangEngine, postExprs);
            }
            return new MolangRotationKeyframe(timeS, preFunctions, postFunctions, x, y, z,
                    new EulerAnglesRotationInterpolator(vecInterpolator));
        }

        Interpolator<Vector3fc> interpolator;
        Vector3f pre, post;
        if (keyframe.getData() != null) {
            pre = post = keyframe.getData();
        } else {
            pre = keyframe.getPre() == null ? keyframe.getPost() : keyframe.getPre();
            post = keyframe.getPost() == null ? keyframe.getPre() : keyframe.getPost();
        }
        Vector3f preTransformed = new Vector3f(pre).mul(x, y, z).mul(DEGREE_TO_ANGLE);
        Vector3f postTransformed;
        if (pre == post) {
            postTransformed = preTransformed;
        } else {
            postTransformed = new Vector3f(post).mul(x, y, z).mul(DEGREE_TO_ANGLE);
        }
        if ("catmullrom".equals(keyframe.getLerpMode())) {
            interpolator = Vector3fCubicSplineInterpolator.INSTANCE;
        } else {
            interpolator = Vector3fLinearInterpolator.INSTANCE;
        }
        return new RotationKeyframe(
                timeS,
                new Rotation(preTransformed),
                new Rotation(postTransformed),
                new EulerAnglesRotationInterpolator(interpolator));
    }

    private static ArrayInterpolatableChannel<Vector3fc> parseChannel(AnimationKeyframes keyframes,
                                                                      float x, float y, float z,
                                                                      @Nullable MochaEngine<?> molangEngine) {
        if (keyframes == null) {
            return null;
        }
        ArrayList<InterpolatableKeyframe<Vector3fc>> array = new ArrayList<>();
        keyframes.getKeyframes().forEach((timeS, keyframe) -> {
            array.add(parseKeyframe((float)(double)timeS, keyframe, x, y, z, molangEngine));
        });
        return new ArrayInterpolatableChannel<>(array);
    }

    private static InterpolatableKeyframe<Vector3fc> parseKeyframe(float timeS, AnimationKeyframes.Keyframe keyframe,
                                                                    float x, float y, float z,
                                                                    @Nullable MochaEngine<?> molangEngine) {
        if (keyframe.hasMolang() && molangEngine != null) {
            Interpolator<Vector3fc> interpolator;
            if ("catmullrom".equals(keyframe.getLerpMode())) {
                interpolator = Vector3fCubicSplineInterpolator.INSTANCE;
            } else {
                interpolator = Vector3fLinearInterpolator.INSTANCE;
            }
            MolangExpression[] preFunctions;
            MolangExpression[] postFunctions;
            if (keyframe.getDataExpressions() != null) {
                MolangExpression[] dataFunctions = compileMolangExpressions(molangEngine, keyframe.getDataExpressions());
                preFunctions = postFunctions = wrapWithMultiplier(dataFunctions, x, y, z);
            } else {
                String[] preExprs = keyframe.getPreExpressions() != null ? keyframe.getPreExpressions() : keyframe.getPostExpressions();
                String[] postExprs = keyframe.getPostExpressions() != null ? keyframe.getPostExpressions() : keyframe.getPreExpressions();
                MolangExpression[] preRaw = compileMolangExpressions(molangEngine, preExprs);
                preFunctions = wrapWithMultiplier(preRaw, x, y, z);
                if (preExprs == postExprs) {
                    postFunctions = preFunctions;
                } else {
                    MolangExpression[] postRaw = compileMolangExpressions(molangEngine, postExprs);
                    postFunctions = wrapWithMultiplier(postRaw, x, y, z);
                }
            }
            return new MolangVector3fKeyframe(timeS, preFunctions, postFunctions, interpolator);
        }

        Interpolator<Vector3fc> interpolator;
        Vector3f pre, post;
        if (keyframe.getData() != null) {
            pre = post = keyframe.getData();
        } else {
            pre = keyframe.getPre() == null ? keyframe.getPost() : keyframe.getPre();
            post = keyframe.getPost() == null ? keyframe.getPre() : keyframe.getPost();
        }
        Vector3f preTransformed = new Vector3f(pre).mul(x, y, z);
        Vector3f postTransformed;
        if (pre == post) {
            postTransformed = preTransformed;
        } else {
            postTransformed = new Vector3f(post).mul(x, y, z);
        }
        if ("catmullrom".equals(keyframe.getLerpMode())) {
            interpolator = Vector3fCubicSplineInterpolator.INSTANCE;
        } else {
            interpolator = Vector3fLinearInterpolator.INSTANCE;
        }
        return new Vector3fKeyframe(timeS, preTransformed, postTransformed, interpolator);
    }

    /**
     * 将 Molang 表达式字符串数组编译为 MolangExpression 数组
     */
    private static MolangExpression[] compileMolangExpressions(MochaEngine<?> engine, String[] expressions) {
        MolangExpression[] result = new MolangExpression[expressions.length];
        for (int i = 0; i < expressions.length; i++) {
            result[i] = MolangEngineHelper.compileExpression(engine, expressions[i]);
        }
        return result;
    }

    /**
     * 包装 MolangExpression 数组，对求值结果应用坐标系乘数
     */
    private static MolangExpression[] wrapWithMultiplier(MolangExpression[] functions, float x, float y, float z) {
        if (x == 1 && y == 1 && z == 1) return functions;
        float[] multipliers = {x, y, z};
        MolangExpression[] wrapped = new MolangExpression[3];
        for (int i = 0; i < 3; i++) {
            final MolangExpression original = functions[i];
            final float mul = multipliers[i];
            if (mul == 1) {
                wrapped[i] = original;
            } else {
                wrapped[i] = ctx -> original.evaluate(ctx) * mul;
            }
        }
        return wrapped;
    }
}
