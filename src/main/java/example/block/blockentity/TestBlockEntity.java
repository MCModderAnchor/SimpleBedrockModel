package example.block.blockentity;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation.AnimationRateLimiter;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation.BedrockAnimation;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClocks;
import example.animation.ClientMolangAnimationState;
import example.animation.MolangTestAnimationContext;
import example.animation.TestBlockAnimationInstance;
import example.init.ExampleModRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestBlockEntity extends BlockEntity {
    private final TestBlockAnimationInstance animationInstance = new TestBlockAnimationInstance(this);

    @Nullable
    @OnlyIn(Dist.CLIENT)
    private ClientMolangAnimationState clientMolangAnimationState;

    public TestBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleModRegister.TEST_BLOCK_ENTITY_TYPE, pos, state);
    }

    public TestBlockAnimationInstance getAnimationInstance() {
        return animationInstance;
    }

    @Nullable
    @OnlyIn(Dist.CLIENT)
    public ClientMolangAnimationState getClientMolangAnimationState() {
        if (clientMolangAnimationState == null) {
            BedrockAnimation animation = MolangTestAnimationContext.getAnimation();
            if (animation != null) {
                clientMolangAnimationState = new ClientMolangAnimationState(
                        animation,
                        AnimationClocks.client(),
                        AnimationRateLimiter.FPS_60
                );
            }
        }
        return clientMolangAnimationState;
    }

    public void tick(Level pLevel, BlockPos pPos, BlockState pState) {
        animationInstance.tick();
    }

    public void replicateAnimationInstance() {
        this.setChanged();
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
    }
    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.put("AnimationInstance", animationInstance.getUpdateTag());
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        animationInstance.handleUpdateTag(tag.getCompound("AnimationInstance"));
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        super.onDataPacket(net, pkt);
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            animationInstance.handleUpdateTag(tag.getCompound("AnimationInstance"));
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
