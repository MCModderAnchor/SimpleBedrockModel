package example.client.render.blockentity;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.BedrockModelRenderTypes;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockAnimationFile;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.TreeModelInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree.TreeBedrockModel;
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
import example.animation.TestBlockAnimationContext;
import example.animation.TestBlockAnimationInstance;
import example.block.blockentity.TestBlockEntity;
import example.init.ExampleModRegister;
import example.resource.KnownResources;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.NotNull;

import java.util.WeakHashMap;
import java.util.function.Supplier;

public class TreeTestBlockEntityRenderer implements BlockEntityRenderer<TestBlockEntity> {
    private static final ResourceLocation TEST_TEXTURE = ExampleModRegister.modLoc("textures/block/test.png");
    private static final ResourceLocation POLY_MESH_TEST_TEXTURE = ExampleModRegister.modLoc("textures/block/vct.png");
    private static final EulerAdditiveBlender BLENDER = new SimpleEulerAdditiveBlender(new ZYXBoneTransformFactory(), ArrayPoseBuilder::new);

    private final Supplier<TreeBedrockModel> testModelSupplier;
    private final Supplier<TreeBedrockModel> polyMeshTestModelSupplier;
    private final WeakHashMap<TestBlockEntity, TreeModelInstance> testInstanceCache = new WeakHashMap<>();
    private final WeakHashMap<TestBlockEntity, TreeModelInstance> polyMeshInstanceCache = new WeakHashMap<>();

    public TreeTestBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.testModelSupplier = Suppliers.memoize(this::loadTestModel);
        this.polyMeshTestModelSupplier = Suppliers.memoize(this::loadPolyMeshTestModel);
    }

    private TreeBedrockModel loadTestModel() {
        TreeBedrockModel model = BedrockModelResources.getInstance().getTreeModel(KnownResources.TEST);
        BedrockAnimationFile animationFile = BedrockAnimationResources.getInstance().getAnimationFile(KnownResources.TEST);
        if (model != null && animationFile != null) {
            TestBlockAnimationContext.initialize(animationFile, model);
        }
        return model;
    }

    private TreeBedrockModel loadPolyMeshTestModel() {
        return BedrockModelResources.getInstance().getTreeModel(KnownResources.POLY_MESH_TEST);
    }

    @Override
    public void render(@NotNull TestBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        boolean polyMeshTest = blockEntity.getBlockState().is(ExampleModRegister.POLY_MESH_TEST_BLOCK);
        TreeBedrockModel model = polyMeshTest ? polyMeshTestModelSupplier.get() : testModelSupplier.get();
        if (model == null) {
            return;
        }

        WeakHashMap<TestBlockEntity, TreeModelInstance> instanceCache = polyMeshTest ? polyMeshInstanceCache : testInstanceCache;
        TreeModelInstance instance = instanceCache.computeIfAbsent(blockEntity, ignored -> model.createInstance());
//        instance.resetPose();

        if (!polyMeshTest) {
            TestBlockAnimationInstance animationInstance = blockEntity.getAnimationInstance();
            animationInstance.renderTick();
            Pose animationPose = animationInstance.getStateMachine().getPose();

            if (animationPose != null) {
                Pose blended = BLENDER.blend(instance.getBindPose(), animationPose);
                instance.applyPose(blended);
            }
        }

        ResourceLocation texture = polyMeshTest ? POLY_MESH_TEST_TEXTURE : TEST_TEXTURE;
        BlockState blockState = blockEntity.getBlockState();
        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        if (blockState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction facing = blockState.getValue(BlockStateProperties.HORIZONTAL_FACING);
            poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        }
        instance.renderToBuffer(poseStack, bufferSource, RenderType.entityCutout(texture),
                BedrockModelRenderTypes.polyMeshCutout(texture), packedLight, packedOverlay);
        poseStack.popPose();
    }
}
