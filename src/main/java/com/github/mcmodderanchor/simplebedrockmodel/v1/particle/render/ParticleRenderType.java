package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;


/**
 * 继承 RenderType 以访问 protected 的 RenderStateShard 常量。
 */
@OnlyIn(Dist.CLIENT)
public abstract class ParticleRenderType extends RenderType {
    // 不会被实例化，仅用于访问 protected 字段
    private ParticleRenderType() {
        super("dummy", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 256, false, false, () -> {}, () -> {});
    }

    public static RenderType additiveParticle(ResourceLocation texture) {
        CompositeState state = CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(ADDITIVE_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(true);
        return create(
                "particle_additive",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256,
                true, true,
                state
        );
    }
}
