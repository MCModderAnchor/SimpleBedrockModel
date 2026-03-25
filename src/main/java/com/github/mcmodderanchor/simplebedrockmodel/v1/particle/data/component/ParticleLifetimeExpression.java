package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import static com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleJsonUtils.getMolang;

public record ParticleLifetimeExpression(
        MolangExpression maxLifetime,
        @Nullable MolangExpression expirationExpression
) implements IParticleComponentDefinition, IParticleComponent {

    @Override
    public int order() {
        return 100;
    }

    @Override
    public boolean requireUpdate() {
        return expirationExpression != null;
    }

    @Override
    public void apply(ParticleInstance p) {
        if (p.emitter == null) return;
        p.maxLifetime = (float) maxLifetime.evaluate(p.emitter.getMolang().getContext());
    }

    @Override
    public void update(ParticleInstance p) {
        if (expirationExpression == null || p.emitter == null) return;
        if (expirationExpression.evaluate(p.emitter.getMolang().getContext()) != 0) {
            p.alive = false;
        }
    }

    public static ParticleLifetimeExpression fromJson(JsonObject obj, ParticleMolangEnvironment molang) {
        MolangExpression maxLifetime = molang.compile(getMolang(obj, "max_lifetime", "1"));
        MolangExpression expiration = obj.has("expiration_expression")
                ? molang.compile(getMolang(obj, "expiration_expression", "0")) : null;
        return new ParticleLifetimeExpression(maxLifetime, expiration);
    }
}
