package example.client.render.entity;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.model.EntityModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.BedrockModelRenderTypes;
import com.github.mcmodderanchor.simplebedrockmodel.v1.resource.BedrockModelResourceSet;
import com.google.common.base.Suppliers;
import com.maydaymemory.mae.basic.ArrayPoseBuilder;
import com.maydaymemory.mae.basic.Pose;
import com.maydaymemory.mae.basic.ZYXBoneTransformFactory;
import com.maydaymemory.mae.blend.EulerAdditiveBlender;
import com.maydaymemory.mae.blend.SimpleEulerAdditiveBlender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import example.entity.Zti;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class ZtiRenderer extends EntityRenderer<Zti> {
    public static final ResourceLocation TEXTURE =  ResourceLocation.fromNamespaceAndPath("example", "textures/entity/zti.png");
    public static final ResourceLocation MODEL =  ResourceLocation.fromNamespaceAndPath("example", "zti.geo");
    public static final ResourceLocation ANIMATION =  ResourceLocation.fromNamespaceAndPath("example", "zti.animation");

    private static final EulerAdditiveBlender BLENDER = new SimpleEulerAdditiveBlender(new ZYXBoneTransformFactory(), ArrayPoseBuilder::new);

    private final Supplier<EntityModel> modelSupplier;

    public ZtiRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.0F;
        this.modelSupplier = Suppliers.memoize(() -> (EntityModel) BedrockModelResourceSet.getInstance().getModel(MODEL));
    }

    @Override
    public void render(@NotNull Zti entity, float entityYaw, float partialTick, @NotNull PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        EntityModel model = modelSupplier.get();
        if (model != null) {
            entity.getAnimationInstance().renderTick();
            Pose blendedPose = BLENDER.blend(model.getBindPose(), entity.getAnimationInstance().getStateMachine().getPose());
            model.applyPose(blendedPose);

            poseStack.pushPose();

            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot)));

            model.renderToBuffer(poseStack, bufferSource,
                    RenderType.entityCutout(TEXTURE),
                    BedrockModelRenderTypes.polyMeshCutout(TEXTURE),
                    packedLight,
                    OverlayTexture.pack(0f, entity.hurtTime > 0 || entity.deathTime > 0)
            );
            model.applyPose(model.getBindPose());
            poseStack.popPose();
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull Zti entity) {
        return TEXTURE;
    }
}
