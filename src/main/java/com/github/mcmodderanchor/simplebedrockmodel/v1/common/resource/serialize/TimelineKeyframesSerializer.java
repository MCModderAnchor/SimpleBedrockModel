package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.serialize;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.TimelineKeyframes;
import com.google.gson.*;
import it.unimi.dsi.fastutil.doubles.Double2ObjectRBTreeMap;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 解析基岩版动画的 {@code timeline} 字段。
 * <p>
 * 每个时间点的值可能是单个字符串，或字符串数组；统一规整为字符串列表。
 */
@SuppressWarnings("ALL")
public class TimelineKeyframesSerializer implements JsonDeserializer<TimelineKeyframes> {
    @Override
    public TimelineKeyframes deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
        Double2ObjectRBTreeMap<List<String>> keyframes = new Double2ObjectRBTreeMap<>();
        if (json.isJsonObject()) {
            JsonObject jsonObject = json.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entrySet : jsonObject.entrySet()) {
                double time = Double.parseDouble(entrySet.getKey());
                JsonElement value = entrySet.getValue();
                List<String> commands = new ArrayList<>();
                if (value.isJsonArray()) {
                    for (JsonElement element : value.getAsJsonArray()) {
                        if (element.isJsonPrimitive()) {
                            commands.add(element.getAsString());
                        }
                    }
                } else if (value.isJsonPrimitive()) {
                    commands.add(value.getAsString());
                }
                if (!commands.isEmpty()) {
                    keyframes.put(time, commands);
                }
            }
        }
        return new TimelineKeyframes(keyframes);
    }
}
