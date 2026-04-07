package example.client.render.blockentity;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v1.resource.BedrockModelResourceSet;
import com.google.common.base.Suppliers;
import com.maydaymemory.mae.basic.ArrayPoseBuilder;
import com.maydaymemory.mae.basic.Pose;
import com.maydaymemory.mae.basic.ZYXBoneTransformFactory;
import com.maydaymemory.mae.blend.EulerAdditiveBlender;
import com.maydaymemory.mae.blend.SimpleEulerAdditiveBlender;
import com.mojang.blaze3d.vertex.PoseStack;
import example.animation.TestBlockAnimationInstance;
import example.block.blockentity.TestBlockEntity;
import example.init.ExampleModRegister;
import example.resource.KnownResources;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class TestBlockEntityRenderer extends BedrockModelBlockEntityRenderer<TestBlockEntity> {
    private static final Material MATERIAL = new Material(InventoryMenu.BLOCK_ATLAS, ExampleModRegister.modLoc("block/test"));
    private static final EulerAdditiveBlender BLENDER = new SimpleEulerAdditiveBlender(new ZYXBoneTransformFactory(), ArrayPoseBuilder::new);

    private final Supplier<BedrockModel> modelSupplier;

    public TestBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        modelSupplier = Suppliers.memoize(() -> BedrockModelResourceSet.getInstance().getModel(KnownResources.TEST));
    }

    @Override
    protected BedrockModel getModel() {
        return modelSupplier.get();
    }

    @Override
    protected Material getMaterial() {
        return MATERIAL;
    }

    @Override
    protected RenderType getRenderType(ResourceLocation textureLocation) {
        return RenderType.entityCutout(textureLocation);
    }

    @Override
    public void render(@NotNull TestBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        TestBlockAnimationInstance animationInstance = blockEntity.getAnimationInstance();
        animationInstance.renderTick();
        Pose animationPose = animationInstance.getStateMachine().getPose();
        BedrockModel model = modelSupplier.get();
        Pose bindPose = model.getBindPose();
        Pose blended = BLENDER.blend(bindPose, animationPose);
        model.applyPose(blended);
        super.render(blockEntity, partialTick, poseStack, buffer, packedLight, packedOverlay);
    }
}
