package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component;

import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;
import com.google.gson.JsonObject;

import static com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleJsonUtils.getBoolean;

public record FPEmitterLocalSpace(boolean position, boolean rotation, boolean velocity, boolean toWorld)
        implements IEmitterComponentDefinition, IEmitterComponent {

    @Override
    public int order() {
        return 410;
    }

    @Override
    public void apply(ParticleEmitterInstance emitter) {
        emitter.enableFPMode(position, position && rotation, velocity, toWorld);
    }

    public static FPEmitterLocalSpace fromJson(JsonObject obj) {
        return new FPEmitterLocalSpace(
                getBoolean(obj, "position", false),
                getBoolean(obj, "rotation", false),
                getBoolean(obj, "velocity", false),
                getBoolean(obj, "to_world", false));
    }
}
