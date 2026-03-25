package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import static com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleJsonUtils.getMolang;

public record ParticleInitialization(
        @Nullable MolangExpression perRenderExpression,
        @Nullable MolangExpression perUpdateExpression
) implements IParticleComponentDefinition, IParticleComponent {

    @Override
    public int order() {
        return 360;
    }

    @Override
    public boolean requireUpdate() {
        return perRenderExpression != null || perUpdateExpression != null;
    }

    @Override
    public void update(ParticleInstance p) {
        if (p.emitter == null) return;
        var ctx = p.emitter.getMolang().getContext();
        if (perRenderExpression != null) perRenderExpression.evaluate(ctx);
        if (perUpdateExpression != null) perUpdateExpression.evaluate(ctx);
    }

    public static ParticleInitialization fromJson(JsonObject obj, ParticleMolangEnvironment molang) {
        MolangExpression perRender = null;
        MolangExpression perUpdate = null;
        if (obj.has("per_render_expression")) {
            String expr = getMolang(obj, "per_render_expression", "");
            if (!expr.isEmpty()) perRender = molang.compile(expr);
        }
        if (obj.has("per_update_expression")) {
            String expr = getMolang(obj, "per_update_expression", "");
            if (!expr.isEmpty()) perUpdate = molang.compile(expr);
        }
        return new ParticleInitialization(perRender, perUpdate);
    }
}
