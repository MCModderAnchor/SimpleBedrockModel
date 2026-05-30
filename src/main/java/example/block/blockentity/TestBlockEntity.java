package example.block.blockentity;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation.AnimationRateLimiter;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation.BedrockAnimation;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClocks;
import example.animation.ClientMolangAnimationState;
import example.animation.MolangTestAnimationContext;
import example.animation.TestBlockAnimationInstance;
import example.init.ExampleModRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
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
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.put("AnimationInstance", animationInstance.getUpdateTag());
        return tag;
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider lookupProvider) {
        super.handleUpdateTag(tag, lookupProvider);
        animationInstance.handleUpdateTag(tag.getCompound("AnimationInstance"));
    }

    @Override
    public @NotNull ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(@NotNull Connection net, @NotNull ClientboundBlockEntityDataPacket pkt, HolderLookup.@NotNull Provider lookupProvider) {
        super.onDataPacket(net, pkt, lookupProvider);
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            animationInstance.handleUpdateTag(tag.getCompound("AnimationInstance"));
        }
    }
}
