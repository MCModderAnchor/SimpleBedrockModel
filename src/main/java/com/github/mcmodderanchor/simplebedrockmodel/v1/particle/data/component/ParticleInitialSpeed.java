package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangContext;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.shape.EmitterShape;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonElement;

import java.util.Random;

import static com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleJsonUtils.molangFromElement;

public record ParticleInitialSpeed(MolangExpression speed)
        implements IParticleComponentDefinition, IParticleComponent {

    @Override
    public int order() {
        return -500;
    }

    @Override
    public void apply(ParticleInstance p) {
        if (p.emitter == null) return;
        MolangContext<?> ctx = p.emitter.getMolang().getContext();
        float s = (float) speed.evaluate(ctx);
        if (s == 0) return;
        EmitterShape shape = p.emitter.getDefinition().emitterPreset().find(EmitterShape.class);
        if (shape != null) {
            shape.applyDirection(p, ctx, new Random(), s);
        }
    }

    public static ParticleInitialSpeed fromJson(JsonElement value, ParticleMolangEnvironment molang) {
        return new ParticleInitialSpeed(molang.compile(molangFromElement(value, "0")));
    }
}
