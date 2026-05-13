package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world;

import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleDescription;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 世界粒子的自定义 {@link ParticleRenderType}。
 * <p>
 * 每个纹理+材质组合对应一个实例，通过 {@link #get(ParticleDescription.Material, ResourceLocation)} 获取。<br/>
 * todo 目前没用atlas，一个贴图一个，考虑优化 ？
 */
@OnlyIn(Dist.CLIENT)
public final class MolangWorldParticleRenderType implements ParticleRenderType {

    private static final Map<String, MolangWorldParticleRenderType> CACHE = new ConcurrentHashMap<>();

    private final ParticleDescription.Material material;
    private final ResourceLocation texture;

    private MolangWorldParticleRenderType(ParticleDescription.Material material, ResourceLocation texture) {
        this.material = material;
        this.texture = texture;
    }

    /**
     * 获取或创建指定材质+纹理的 RenderType 实例。
     */
    public static MolangWorldParticleRenderType get(ParticleDescription.Material material, ResourceLocation texture) {
        String key = material.name() + ":" + texture;
        return CACHE.computeIfAbsent(key, k -> new MolangWorldParticleRenderType(material, texture));
    }

    /**
     * 清除缓存（资源重载时调用）。
     */
    public static void clearCache() {
        CACHE.clear();
    }

    @Override
    public @Nullable BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
        RenderSystem.enableDepthTest();
        Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
        RenderSystem.setShaderTexture(0, texture);

        switch (material) {
            case PARTICLES_OPAQUE -> {
                RenderSystem.depthMask(true);
                RenderSystem.disableBlend();
            }
            case PARTICLES_ALPHA -> {
                RenderSystem.depthMask(true);
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            }
            case PARTICLES_BLEND -> {
                RenderSystem.depthMask(true);
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            }
            case PARTICLES_ADD -> {
                RenderSystem.depthMask(false);
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            }
        }

        RenderSystem.disableCull();
        return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
    }

    @Override
    public String toString() {
        return "MolangWorldParticleRenderType{" + material + ", " + texture + "}";
    }
}
