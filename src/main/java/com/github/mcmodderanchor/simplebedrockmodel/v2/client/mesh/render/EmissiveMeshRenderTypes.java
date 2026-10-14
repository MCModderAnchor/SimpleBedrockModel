package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/** 带深度和 alpha 裁剪的发光材质；shader 不使用法线方向计算明暗。 */
public class EmissiveMeshRenderTypes extends RenderStateShard {
    private static final Function<ResourceLocation, RenderType> QUADS =
            Util.memoize(texture -> create(texture, VertexFormat.Mode.QUADS));
    private static final Function<ResourceLocation, RenderType> TRIANGLES =
            Util.memoize(texture -> create(texture, VertexFormat.Mode.TRIANGLES));

    public EmissiveMeshRenderTypes(String name, Runnable setup, Runnable clear) {
        super(name, setup, clear);
    }

    public static RenderType cutout(ResourceLocation texture, VertexFormat.Mode mode) {
        return switch (mode) {
            case QUADS -> QUADS.apply(texture);
            case TRIANGLES -> TRIANGLES.apply(texture);
            default -> throw new IllegalArgumentException("Unsupported emissive mode: " + mode);
        };
    }

    public static RenderType create(ResourceLocation texture, VertexFormat.Mode mode) {
        return RenderType.create("sbm_emissive_cutout", DefaultVertexFormat.NEW_ENTITY, mode, 256, true, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENERGY_SWIRL_SHADER)
                        .setTextureState(new TextureStateShard(texture, false, false))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setCullState(mode == VertexFormat.Mode.QUADS ? CULL : NO_CULL)
                        .setTexturingState(new TexturingStateShard("sbm_emissive_identity_uv",
                                RenderSystem::resetTextureMatrix, RenderSystem::resetTextureMatrix))
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .createCompositeState(true));
    }
}
