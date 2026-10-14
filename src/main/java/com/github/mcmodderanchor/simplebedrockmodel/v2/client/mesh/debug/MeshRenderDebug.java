package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.debug;


import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;


/** 开发环境对照入口；不持有 GPU 资源，也不提供可切换的生产后端配置。 */
@ApiStatus.Internal
public class MeshRenderDebug {
    public interface DrawAttributes {
        public void beginItem();
        public void setupPart(VertexBuffer buffer, VertexFormat format, boolean instanceLight,
                       int lightIndex, int overlayIndex, int packedLight, int packedOverlay);
        public void endItem();
    }


    public static DrawAttributes attributes;

    public MeshRenderDebug() {}

    public static void withDrawAttributes(@Nullable DrawAttributes override, Runnable action) {
        RenderSystem.assertOnRenderThread();
        if (FMLEnvironment.production) throw new IllegalStateException("Development instrumentation only");
        DrawAttributes previous = attributes;
        attributes = override;
        try { action.run(); }
        finally { attributes = previous; }
    }

}
