package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.shape;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangContext;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleInstance;
import org.jetbrains.annotations.Nullable;
import java.util.Random;

public record EmitterShapePoint(MolangExpression[] offset, @Nullable MolangExpression[] direction,
                                 DirectionMode directionMode) implements EmitterShape {
    @Override
    public void applyPosition(ParticleInstance p, MolangContext<?> ctx, Random random) {
        p.x = (float) offset[0].evaluate(ctx);
        p.y = (float) offset[1].evaluate(ctx);
        p.z = (float) offset[2].evaluate(ctx);
    }
}
