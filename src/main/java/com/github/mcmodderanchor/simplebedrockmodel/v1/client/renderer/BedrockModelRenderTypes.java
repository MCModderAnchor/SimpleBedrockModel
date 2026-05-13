package com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.Function;

@OnlyIn(Dist.CLIENT)
public abstract class BedrockModelRenderTypes extends RenderType {
    private static final Function<ResourceLocation, RenderType> POLY_MESH_CUTOUT = Util.memoize(BedrockModelRenderTypes::createPolyMeshCutout);

    private BedrockModelRenderTypes() {
        super("dummy", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 256, false, false, () -> {}, () -> {});
    }

    public static RenderType polyMeshCutout(ResourceLocation texture) {
        return POLY_MESH_CUTOUT.apply(texture);
    }

    private static RenderType createPolyMeshCutout(ResourceLocation texture) {
        CompositeState state = CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_CUTOUT_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(NO_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(true);
        return create(
                "bedrock_poly_mesh_cutout",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.TRIANGLES,
                256,
                true,
                false,
                state
        );
    }
}
