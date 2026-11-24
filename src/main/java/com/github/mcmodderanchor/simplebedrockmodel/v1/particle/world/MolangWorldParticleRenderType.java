package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world;

import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleDescription;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

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
    private WorldParticleCompute computeBatch;

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

    public static void invalidateCompute() {
        WorldParticleCompute.invalidate();
    }

    @Override
    public void begin(BufferBuilder builder, TextureManager textureManager) {
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
            case ENERGY_SWIRL, PARTICLES_EMISSIVE -> {
                RenderSystem.depthMask(false);
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            }
        }

        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        computeBatch = WorldParticleCompute.begin(builder);
    }

    boolean collect(SnowStormParticle particle, VertexConsumer consumer, Camera camera, float partialTick) {
        return computeBatch != null && computeBatch.collect(particle, consumer, camera, partialTick);
    }

    @Override
    public void end(Tesselator tesselator) {
        var batch = computeBatch;
        computeBatch = null;
        if (batch == null) {
            tesselator.end();
        } else {
            batch.end(tesselator);
        }
    }

    @Override
    public String toString() {
        return "MolangWorldParticleRenderType{" + material + ", " + texture + "}";
    }
}
