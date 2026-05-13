package example.client.render.blockentity;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.BedrockModelRenderTypes;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.model.BedrockModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.NotNull;

public abstract class BedrockModelBlockEntityRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    protected abstract BedrockModel getModel();

    protected abstract ResourceLocation getTexture();

    protected abstract RenderType getRenderType(ResourceLocation textureLocation);

    protected RenderType getPolyMeshRenderType(ResourceLocation textureLocation) {
        return BedrockModelRenderTypes.polyMeshCutout(textureLocation);
    }

    @Override
    public void render(@NotNull T blockEntity, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ResourceLocation texture = getTexture();
        BlockState blockState = blockEntity.getBlockState();

        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        if (blockState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction facing = blockState.getValue(BlockStateProperties.HORIZONTAL_FACING);
            poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        }
        getModel().renderToBuffer(poseStack, bufferSource, getRenderType(texture), getPolyMeshRenderType(texture), packedLight, packedOverlay);
        poseStack.popPose();
    }
}
