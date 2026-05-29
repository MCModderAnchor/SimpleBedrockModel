package example.client.render.blockentity;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.BedrockModelRenderTypes;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.GsonUtil;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockAnimationFile;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockModelPOJO;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.BedrockModelInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.bake.BakerOptions;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.BakedBedrockModel;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public class V2TestBlockEntityRenderer implements BlockEntityRenderer<TestBlockEntity> {
    private static final ResourceLocation TEST_TEXTURE = ExampleModRegister.modLoc("textures/block/test.png");
    private static final ResourceLocation POLY_MESH_TEST_TEXTURE = ExampleModRegister.modLoc("textures/block/vct.png");
    private static final ResourceLocation TEST_ANIM_PATH = ExampleModRegister.modLoc("animations/test.json");
    private static final EulerAdditiveBlender BLENDER = new SimpleEulerAdditiveBlender(new ZYXBoneTransformFactory(), ArrayPoseBuilder::new);

    private final Supplier<BakedBedrockModel> testModelSupplier;
    private final Supplier<BakedBedrockModel> polyMeshTestModelSupplier;
    private final WeakHashMap<TestBlockEntity, BedrockModelInstance> testInstanceCache = new WeakHashMap<>();
    private final WeakHashMap<TestBlockEntity, BedrockModelInstance> polyMeshInstanceCache = new WeakHashMap<>();

    public V2TestBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.testModelSupplier = Suppliers.memoize(this::loadTestModel);
        this.polyMeshTestModelSupplier = Suppliers.memoize(this::loadPolyMeshTestModel);
    }

    private BakedBedrockModel loadTestModel() {
        try {
            BedrockModelPOJO modelPOJO;
            try (InputStream stream = Minecraft.getInstance().getResourceManager().open(ExampleModRegister.modLoc("models/bedrock/test.json"));
                 InputStreamReader reader = new InputStreamReader(stream)) {
                modelPOJO = GsonUtil.CLIENT_GSON.fromJson(reader, BedrockModelPOJO.class);
            }

            BedrockAnimationFile animationFile;
            try (InputStream stream = Minecraft.getInstance().getResourceManager().open(TEST_ANIM_PATH);
                 InputStreamReader reader = new InputStreamReader(stream)) {
                animationFile = GsonUtil.CLIENT_GSON.fromJson(reader, BedrockAnimationFile.class);
            }

            BakerOptions options = BakerOptions.ofAnimationFile(animationFile);
            BakedBedrockModel model = BakedBedrockModel.bake(modelPOJO, options);

            // 使用 v2 模型的骨骼索引初始化动画
            TestBlockAnimationContext.initialize(animationFile, model);

            SimpleBedrockModel.LOGGER.info("Loaded v2 test model: bones={}, cubeChunks={}, meshChunks={}, animatedBones={}",
                    model.bones().length, model.cubeChunks().length, model.meshChunks().length,
                    options.animatedBones().size());
            return model;
        } catch (Exception e) {
            SimpleBedrockModel.LOGGER.error("Failed to load v2 test model", e);
            return null;
        }
    }

    private BakedBedrockModel loadPolyMeshTestModel() {
        return loadModel(ExampleModRegister.modLoc("models/bedrock/vct.geo.json"), BakerOptions.defaults(), "poly mesh test");
    }

    private BakedBedrockModel loadModel(ResourceLocation path, BakerOptions options, String name) {
        try (InputStream stream = Minecraft.getInstance().getResourceManager().open(path);
             InputStreamReader reader = new InputStreamReader(stream)) {
            BedrockModelPOJO pojo = GsonUtil.CLIENT_GSON.fromJson(reader, BedrockModelPOJO.class);
            BakedBedrockModel model = BakedBedrockModel.bake(pojo, options);
            SimpleBedrockModel.LOGGER.info("Loaded v2 {} model: bones={}, cubeChunks={}, meshChunks={}",
                    name, model.bones().length, model.cubeChunks().length, model.meshChunks().length);
            return model;
        } catch (Exception e) {
            SimpleBedrockModel.LOGGER.error("Failed to load v2 {} model: {}", name, path, e);
            return null;
        }
    }

    @Override
    public void render(@NotNull TestBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        boolean polyMeshTest = blockEntity.getBlockState().is(ExampleModRegister.POLY_MESH_TEST_BLOCK);
        BakedBedrockModel model = polyMeshTest ? polyMeshTestModelSupplier.get() : testModelSupplier.get();
        if (model == null) {
            return;
        }
        WeakHashMap<TestBlockEntity, BedrockModelInstance> instanceCache = polyMeshTest ? polyMeshInstanceCache : testInstanceCache;
        BedrockModelInstance instance = instanceCache.computeIfAbsent(blockEntity, ignored -> model.createInstance());
        instance.resetPose();

        if (!polyMeshTest) {
            TestBlockAnimationInstance animationInstance = blockEntity.getAnimationInstance();
            animationInstance.renderTick();
            Pose animationPose = animationInstance.getStateMachine().getPose();
            Pose blended = BLENDER.blend(instance.getBindPose(), animationPose);
            instance.applyPose(blended);
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
