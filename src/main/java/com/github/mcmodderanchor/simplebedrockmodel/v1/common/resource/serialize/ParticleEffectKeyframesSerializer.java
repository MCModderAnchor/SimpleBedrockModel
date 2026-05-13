package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.serialize;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.ParticleEffectData;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.ParticleEffectKeyframes;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import it.unimi.dsi.fastutil.doubles.Double2ObjectRBTreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.lang.reflect.Type;
import java.util.Map;

public class ParticleEffectKeyframesSerializer implements JsonDeserializer<ParticleEffectKeyframes> {
    @Override
    public ParticleEffectKeyframes deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
        Double2ObjectRBTreeMap<ParticleEffectData> keyframes = new Double2ObjectRBTreeMap<>();
        if (json != null && json.isJsonObject()) {
            JsonObject jsonObject = json.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                double time = Double.parseDouble(entry.getKey());
                JsonElement value = entry.getValue();
                if (!value.isJsonObject()) {
                    continue;
                }
                JsonObject object = value.getAsJsonObject();
                ResourceLocation effect = ResourceLocation.parse(GsonHelper.getAsString(object, "effect"));
                String locator = GsonHelper.getAsString(object, "locator", "");
                String preEffectScript = GsonHelper.getAsString(object, "pre_effect_script", "");
                keyframes.put(time, new ParticleEffectData(effect, locator, preEffectScript));
            }
        }
        return new ParticleEffectKeyframes(keyframes);
    }
}
