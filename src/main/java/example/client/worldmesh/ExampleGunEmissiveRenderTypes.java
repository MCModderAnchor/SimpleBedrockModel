package example.client.worldmesh;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** 开发环境中复刻 TaCZ DisplayGunRenderType.emissive，包含对应的物品／实体输出目标。 */
public final class ExampleGunEmissiveRenderTypes extends RenderStateShard {
    private static final Map<ResourceLocation, RenderType> CACHE = new HashMap<>();

    private ExampleGunEmissiveRenderTypes() {
        super("example_gun_emissive", () -> {}, () -> {});
    }

    static RenderType emissive(ResourceLocation texture) {
        return CACHE.computeIfAbsent(texture, key -> RenderType.create(
                "example_tacz_gun_emissive", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENERGY_SWIRL_SHADER)
                        .setTextureState(new TextureStateShard(texture, false, false))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setCullState(CULL)
                        .setTexturingState(new TexturingStateShard("example_gun_identity_uv",
                                RenderSystem::resetTextureMatrix, RenderSystem::resetTextureMatrix))
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .setOutputState(ITEM_ENTITY_TARGET)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .createCompositeState(true)));
    }
}
