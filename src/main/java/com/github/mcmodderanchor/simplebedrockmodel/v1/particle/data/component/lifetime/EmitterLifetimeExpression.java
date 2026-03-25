package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.lifetime;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangContext;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.IEmitterComponent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.IEmitterComponentDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import static com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleJsonUtils.getMolang;

/**
 * 表达式生命周期。对应 "minecraft:emitter_lifetime_expression"。
 */
public record EmitterLifetimeExpression(
        MolangExpression activationExpression,
        MolangExpression expirationExpression
) implements LifetimeComponent {

    @Override
    public int order() {
        return 500;
    }

    @Override
    public boolean requireUpdate() {
        return true;
    }

    @Override
    public IEmitterComponent createRuntime() {
        return new Runtime(activationExpression, expirationExpression);
    }

    public static EmitterLifetimeExpression fromJson(String key, JsonElement value, ParticleMolangEnvironment molang) {
        JsonObject obj = value.getAsJsonObject();
        return new EmitterLifetimeExpression(
                molang.compile(getMolang(obj, "activation_expression", "1")),
                molang.compile(getMolang(obj, "expiration_expression", "0")));
    }

    // ===== Runtime =====

    public static final class Runtime implements IEmitterComponent {
        private final MolangExpression activationExpr;
        private final MolangExpression expirationExpr;

        public Runtime(MolangExpression activation, MolangExpression expiration) {
            this.activationExpr = activation;
            this.expirationExpr = expiration;
        }

        @Override
        public void apply(ParticleEmitterInstance emitter) {
            emitter.setRemoved(false);
            emitter.setActive(true);
            emitter.setEmitterAge(0);
            emitter.setEmitterLifetime(0);
        }

        @Override
        public void update(ParticleEmitterInstance emitter) {
            float newAge = emitter.getEmitterAge() + emitter.getDt();
            emitter.setEmitterAge(newAge);
            emitter.setEmitterLifetime(newAge);
            emitter.bindContextAndCurves();

            MolangContext<?> ctx = emitter.getMolang().getContext();

            if (expirationExpr.evaluate(ctx) != 0) {
                emitter.fireExpirationEvents();
                emitter.setRemoved(true);
                emitter.setActive(false);
                return;
            }

            emitter.setActive(activationExpr.evaluate(ctx) != 0);
        }
    }
}
