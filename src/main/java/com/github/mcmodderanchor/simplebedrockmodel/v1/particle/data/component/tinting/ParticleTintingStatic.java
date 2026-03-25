package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.tinting;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangContext;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.IParticleComponent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.IParticleComponentDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleInstance;
import org.jetbrains.annotations.Nullable;

public record ParticleTintingStatic(MolangExpression r, MolangExpression g, MolangExpression b,
                                     @Nullable MolangExpression a)
        implements IParticleComponentDefinition, IParticleComponent {

    @Override public int order() { return 210; }
    @Override public boolean requireUpdate() { return false; }

    @Override
    public void apply(ParticleInstance p) {
        if (p.emitter == null) return;
        MolangContext<?> ctx = p.emitter.getMolang().getContext();
        p.r = (float) r.evaluate(ctx);
        p.g = (float) g.evaluate(ctx);
        p.b = (float) b.evaluate(ctx);
        p.a = a != null ? (float) a.evaluate(ctx) : 1f;
    }
}
