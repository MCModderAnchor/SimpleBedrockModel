package example.client.render.entity;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.BedrockModelRenderTypes;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockAnimationFile;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BakedModelInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedBoneDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedGeometryChunk;
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

        SimpleBedrockModel.LOGGER.info("Loaded v2 ZTI model: bones={}, cubeChunks={}, meshChunks={}",
                model.bones().length, model.cubeChunks().length, model.meshChunks().length);

        // 调试日志：打印骨骼层级与几何体分布
        logBoneHierarchy(model);
        // 调试日志：打印 mesh chunk 的顶点包围盒
        logMeshChunkBounds(model);
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

    private static void logBoneHierarchy(BakedBedrockModel model) {
        SimpleBedrockModel.LOGGER.info("=== ZTI v2 bone hierarchy ({}) ===", model.bones().length);
        for (BakedBoneDefinition bone : model.bones()) {
            boolean hasQuads = false;
            boolean hasVerts = false;
            for (BakedGeometryChunk chunk : model.cubeChunks()) {
                if (chunk.attachBoneIndex() == bone.index()) { hasQuads = true; break; }
            }
            for (BakedGeometryChunk chunk : model.meshChunks()) {
                if (chunk.attachBoneIndex() == bone.index()) { hasVerts = true; break; }
            }
            SimpleBedrockModel.LOGGER.info(String.format(
                    "  [%d] %s  parent=%d  pivot=(%.3f, %.3f, %.3f)  bindRot=(%.1f, %.1f, %.1f)  bindLocal=%s  hasQuads=%s hasVerts=%s",
                    bone.index(), bone.name(), bone.parentIndex(),
                    bone.pivotX(), bone.pivotY(), bone.pivotZ(),
                    Math.toDegrees(bone.bindEulerRotation().x()),
                    Math.toDegrees(bone.bindEulerRotation().y()),
                    Math.toDegrees(bone.bindEulerRotation().z()),
                    bone.bindLocalTransform() != null ? "non-null" : "NULL",
                    hasQuads, hasVerts));
        }
    }

    private static void logMeshChunkBounds(BakedBedrockModel model) {
        for (BakedGeometryChunk chunk : model.meshChunks()) {
            if (!chunk.hasVertices()) continue;
            float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
            float[] pos = chunk.vertices().positions();
            for (int v = 0; v < chunk.vertices().vertexCount(); v++) {
                float vx = pos[v * 3];
                float vy = pos[v * 3 + 1];
                float vz = pos[v * 3 + 2];
                if (vx < minX) minX = vx;
                if (vy < minY) minY = vy;
                if (vz < minZ) minZ = vz;
                if (vx > maxX) maxX = vx;
                if (vy > maxY) maxY = vy;
                if (vz > maxZ) maxZ = vz;
            }
            BakedBoneDefinition bone = model.bones()[chunk.attachBoneIndex()];
            SimpleBedrockModel.LOGGER.info(String.format(
                    "  Mesh chunk on bone [%d] %s: %d vertices, bounds=(%.3f,%.3f,%.3f) -> (%.3f,%.3f,%.3f)",
                    bone.index(), bone.name(), chunk.vertices().vertexCount(),
                    minX, minY, minZ, maxX, maxY, maxZ));
        }
    }
}
