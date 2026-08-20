package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.render;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.BedrockModelRenderTypes;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.Function;

/**
 * 继承 RenderType 以访问 protected 的 RenderStateShard 常量。
 */
@OnlyIn(Dist.CLIENT)
public abstract class ParticleRenderType extends RenderType {
    private static final Function<ResourceLocation, RenderType> ADDITIVE_PARTICLE= Util.memoize(ParticleRenderType::createAdditiveParticle);
    private static final Function<ResourceLocation, RenderType> EMISSIVE_PARTICLE = Util.memoize(ParticleRenderType::createEmissiveParticle);

    // 不会被实例化，仅用于访问 protected 字段
    private ParticleRenderType() {
        super("dummy", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 256, false, false, () -> {}, () -> {});
    }

    public static RenderType additiveParticle(ResourceLocation texture) {
        return ADDITIVE_PARTICLE.apply(texture);
    }

    public static RenderType emissiveParticle(ResourceLocation texture) {
        return EMISSIVE_PARTICLE.apply(texture);
    }

    public static RenderType createAdditiveParticle(ResourceLocation texture) {
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

    /**
     * 类似 TACZ 镭射的自发光 RenderType：NEW_ENTITY format + emissive shader + 叠加混合。
     * 自发光不依赖光照传递，且与枪体同 format，AR 加速路径下可与枪体同一加速层被模板剔除。
     */
    public static RenderType createEmissiveParticle(ResourceLocation texture) {
        CompositeState state = CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(ADDITIVE_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setWriteMaskState(COLOR_DEPTH_WRITE)
                .createCompositeState(true);
        return create(
                "particle_emissive",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256,
                true, true,
                state
        );
    }

}
