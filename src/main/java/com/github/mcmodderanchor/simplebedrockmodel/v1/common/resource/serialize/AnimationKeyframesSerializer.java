package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.serialize;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.AnimationKeyframes;
import com.google.gson.*;
import it.unimi.dsi.fastutil.doubles.Double2ObjectRBTreeMap;
import net.minecraft.util.GsonHelper;
import org.joml.Vector3f;

import org.jetbrains.annotations.Nullable;
import java.lang.reflect.Type;
import java.util.Map;

@SuppressWarnings("ALL")
public class AnimationKeyframesSerializer implements JsonDeserializer<AnimationKeyframes> {
    @Override
    public AnimationKeyframes deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
        Double2ObjectRBTreeMap<AnimationKeyframes.Keyframe> keyframes = new Double2ObjectRBTreeMap<>();
        // 如果是数字
        if (json.isJsonPrimitive()) {
            if (json.getAsJsonPrimitive().isString()) {
                // 单个 Molang 表达式，作为 data 的三个分量
                String expr = json.getAsString();
                String[] dataExpr = {expr, expr, expr};
                AnimationKeyframes.Keyframe keyframe = new AnimationKeyframes.Keyframe(null, null, null, null, null, null, dataExpr);
                keyframes.put(0, keyframe);
                return new AnimationKeyframes(keyframes);
            } else {
                float value = json.getAsJsonPrimitive().getAsFloat();
                Vector3f data = new Vector3f(value, value, value);
                AnimationKeyframes.Keyframe keyframe = new AnimationKeyframes.Keyframe(null, null, data, null);
                keyframes.put(0, keyframe);
                return new AnimationKeyframes(keyframes);
            }
        }
        // 如果是数组
        if (json.isJsonArray()) {
            JsonArray array = json.getAsJsonArray();
            String[] molangExprs = extractMolangFromArray(array);
            if (molangExprs != null) {
                AnimationKeyframes.Keyframe keyframe = new AnimationKeyframes.Keyframe(null, null, null, null, null, null, molangExprs);
                keyframes.put(0, keyframe);
            } else {
                Vector3f data = this.readVector3f(array);
                AnimationKeyframes.Keyframe keyframe = new AnimationKeyframes.Keyframe(null, null, data, null);
                keyframes.put(0, keyframe);
            }
            return new AnimationKeyframes(keyframes);
        }
        // 如果是对象
        if (json.isJsonObject()) {
            JsonObject jsonObject = json.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entrySet : jsonObject.entrySet()) {
                double time = Double.parseDouble(entrySet.getKey());
                AnimationKeyframes.Keyframe keyframe = readKeyFrames(entrySet.getValue());
                keyframes.put(time, keyframe);
            }
            return new AnimationKeyframes(keyframes);
        }
        return new AnimationKeyframes(keyframes);
    }

    private AnimationKeyframes.Keyframe readKeyFrames(JsonElement element) {
        // 如果是数组
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            String[] molangExprs = extractMolangFromArray(array);
            if (molangExprs != null) {
                return new AnimationKeyframes.Keyframe(null, null, null, null, null, null, molangExprs);
            }
            Vector3f data = this.readVector3f(array);
            return new AnimationKeyframes.Keyframe(null, null, data, null);
        }
        // 如果是对象
        if (element.isJsonObject()) {
            JsonObject jsonObject = element.getAsJsonObject();
            String lerpMode = null;
            Vector3f pre = null;
            Vector3f post = null;
            String[] preExpressions = null;
            String[] postExpressions = null;
            if (jsonObject.has("lerp_mode")) {
                lerpMode = jsonObject.get("lerp_mode").getAsString();
            }
            if (jsonObject.has("pre")) {
                JsonElement preElement = jsonObject.get("pre");
                if (preElement.isJsonArray()) {
                    JsonArray array = preElement.getAsJsonArray();
                    preExpressions = extractMolangFromArray(array);
                    if (preExpressions == null) {
                        pre = this.readVector3f(array);
                    }
                }
            }
            if (jsonObject.has("post")) {
                JsonElement postElement = jsonObject.get("post");
                if (postElement.isJsonArray()) {
                    JsonArray array = postElement.getAsJsonArray();
                    postExpressions = extractMolangFromArray(array);
                    if (postExpressions == null) {
                        post = this.readVector3f(array);
                    }
                }
            }
            return new AnimationKeyframes.Keyframe(pre, post, null, lerpMode, preExpressions, postExpressions, null);
        }
        return new AnimationKeyframes.Keyframe(null, null, null, null);
    }

    /**
     * 检查 JsonArray 中是否包含 Molang 字符串表达式。
     * 如果任意一个元素是字符串，则返回一个长度为 3 的表达式数组；
     * 数字元素会被转换为字符串形式的常量。
     * 如果所有元素都是数字，返回 null。
     */
    @Nullable
    private String[] extractMolangFromArray(JsonArray array) {
        boolean hasMolang = false;
        for (int i = 0; i < 3; i++) {
            if (array.get(i).isJsonPrimitive() && array.get(i).getAsJsonPrimitive().isString()) {
                hasMolang = true;
                break;
            }
        }
        if (!hasMolang) return null;
        String[] expressions = new String[3];
        for (int i = 0; i < 3; i++) {
            JsonElement elem = array.get(i);
            if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isString()) {
                expressions[i] = elem.getAsString();
            } else {
                // 数字元素转为常量字符串
                expressions[i] = String.valueOf(GsonHelper.convertToFloat(elem, "(array i=" + i + ")"));
            }
        }
        return expressions;
    }

    private Vector3f readVector3f(JsonArray array) {
        JsonElement xElement = array.get(0);
        JsonElement yElement = array.get(1);
        JsonElement zElement = array.get(2);
        float x = readVector3fElement(xElement, "(array i=0)");
        float y = readVector3fElement(yElement, "(array i=1)");
        float z = readVector3fElement(zElement, "(array i=2)");
        return new Vector3f(x, y, z);
    }

    private float readVector3fElement(JsonElement element, String memberName) {
        if (element.getAsJsonPrimitive().isString()) {
            return 0;
        } else {
            return GsonHelper.convertToFloat(element, memberName);
        }
    }
}
