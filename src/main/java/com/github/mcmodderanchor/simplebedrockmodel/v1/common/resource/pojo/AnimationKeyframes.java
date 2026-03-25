package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo;

import it.unimi.dsi.fastutil.doubles.Double2ObjectRBTreeMap;
import org.joml.Vector3f;

import org.jetbrains.annotations.Nullable;

@SuppressWarnings("MapOrSetKeyShouldOverrideHashCodeEquals")
public class AnimationKeyframes {
    private final Double2ObjectRBTreeMap<Keyframe> keyframes;

    public AnimationKeyframes(Double2ObjectRBTreeMap<Keyframe> keyframes) {
        this.keyframes = keyframes;
    }

    public Double2ObjectRBTreeMap<Keyframe> getKeyframes() {
        return keyframes;
    }

    public static class Keyframe {
        private final Vector3f pre;
        private final Vector3f post;
        private final Vector3f data;
        private final String lerpMode;
        // Molang 表达式字符串（当关键帧值为字符串时保留原始表达式）
        private final String[] preExpressions;
        private final String[] postExpressions;
        private final String[] dataExpressions;

        public Keyframe (@Nullable Vector3f pre, @Nullable Vector3f post,
                         @Nullable Vector3f data, @Nullable String lerpMode) {
            this(pre, post, data, lerpMode, null, null, null);
        }

        public Keyframe (@Nullable Vector3f pre, @Nullable Vector3f post,
                         @Nullable Vector3f data, @Nullable String lerpMode,
                         @Nullable String[] preExpressions, @Nullable String[] postExpressions,
                         @Nullable String[] dataExpressions) {
            this.pre = pre;
            this.post = post;
            this.data = data;
            this.lerpMode = lerpMode;
            this.preExpressions = preExpressions;
            this.postExpressions = postExpressions;
            this.dataExpressions = dataExpressions;
        }

        public Vector3f getPre() {
            return pre;
        }

        public Vector3f getPost() {
            return post;
        }

        public Vector3f getData() {
            return data;
        }

        public String getLerpMode() {
            return lerpMode;
        }

        /**
         * 是否包含 Molang 表达式
         */
        public boolean hasMolang() {
            return preExpressions != null || postExpressions != null || dataExpressions != null;
        }

        @Nullable
        public String[] getPreExpressions() {
            return preExpressions;
        }

        @Nullable
        public String[] getPostExpressions() {
            return postExpressions;
        }

        @Nullable
        public String[] getDataExpressions() {
            return dataExpressions;
        }
    }
}
