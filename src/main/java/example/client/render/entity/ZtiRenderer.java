package example.client.render.entity;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.BedrockModelRenderTypes;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockAnimationFile;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BakedModelInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.BedrockAnimationResources;
import com.github.mcmodderanchor.simplebedrockmodel.v2.resource.BedrockModelResources;
import com.google.common.base.Suppliers;
import com.maydaymemory.mae.basic.ArrayPoseBuilder;
import com.maydaymemory.mae.basic.Pose;
import com.maydaymemory.mae.basic.ZYXBoneTransformFactory;
import com.maydaymemory.mae.blend.EulerAdditiveBlender;
import com.maydaymemory.mae.blend.SimpleEulerAdditiveBlender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import example.animation.ZtiAnimationContext;
import example.entity.Zti;
import example.resource.KnownResources;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.WeakHashMap;
import java.util.function.Supplier;

public class ZtiRenderer extends EntityRenderer<Zti> {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("example", "textures/entity/zti.png");

    private static final EulerAdditiveBlender BLENDER = new SimpleEulerAdditiveBlender(new ZYXBoneTransformFactory(), ArrayPoseBuilder::new);

    private final Supplier<BakedBedrockModel> modelSupplier;
    private final WeakHashMap<Zti, BakedModelInstance> instanceCache = new WeakHashMap<>();

    public ZtiRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.0F;
        this.modelSupplier = Suppliers.memoize(this::loadModel);
    }

    private BakedBedrockModel loadModel() {
        BakedBedrockModel model = BedrockModelResources.getInstance().getBakedModel(KnownResources.ZTI_MODEL);
        BedrockAnimationFile animationFile = BedrockAnimationResources.getInstance().getAnimationFile(KnownResources.ZTI_ANIMATION);
        if (model == null || animationFile == null) {
            return null;
        }

        // 使用 v2 模型的骨骼索引初始化动画，替代 v1 的 RegisterBedrockAnimationReloadListenerEvent 流程
        ZtiAnimationContext.initialize(animationFile, model);

        SimpleBedrockModel.LOGGER.debug("Loaded v2 ZTI model: bones={}, cubeChunks={}, meshChunks={}",
                model.bones().length, model.cubeChunks().length, model.meshChunks().length);

        return model;
    }

    @Override
    public void render(@NotNull Zti entity, float entityYaw, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource bufferSource, int packedLight) {
        BakedBedrockModel model = modelSupplier.get();
        if (model == null) {
            return;
        }
        BakedModelInstance instance = instanceCache.computeIfAbsent(entity, ignored -> model.createInstance());
        instance.resetPose();

        entity.getAnimationInstance().renderTick();
        Pose blended = BLENDER.blend(instance.getBindPose(), entity.getAnimationInstance().getStateMachine().getPose());
        instance.applyPose(blended);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot)));

        instance.renderToBuffer(poseStack, bufferSource,
                RenderType.entityCutout(TEXTURE),
                BedrockModelRenderTypes.polyMeshCutout(TEXTURE),
                packedLight,
                OverlayTexture.pack(0f, entity.hurtTime > 0 || entity.deathTime > 0)
        );
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(@NotNull Zti entity) {
        return TEXTURE;
    }

}
