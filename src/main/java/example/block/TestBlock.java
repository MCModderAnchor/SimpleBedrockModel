package example.block;

import com.mojang.serialization.MapCodec;
import example.block.blockentity.TestBlockEntity;
import example.init.ExampleModRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<TestBlock> CODEC = simpleCodec(TestBlock::new);
    public TestBlock(BlockBehaviour.Properties properties) {
        super(Properties.of()
                .mapColor(MapColor.PODZOL)
                .strength(2.0F)
                .sound(SoundType.WOOD)
                .lightLevel(s -> 15)
                .noOcclusion()
                .ignitedByLava());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public TestBlock() {
        super(Properties.of()
                .mapColor(MapColor.PODZOL)
                .strength(2.0F)
                .sound(SoundType.WOOD)
                .lightLevel(s -> 0)
                .noOcclusion()
                .ignitedByLava());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    private static final BlockEntityTicker<TestBlockEntity> ticker = (level, pos, state, blockEntity) -> {
        blockEntity.tick(level, pos, state);
    };

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new TestBlockEntity(blockPos, blockState);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, BlockHitResult pHit) {
        if (pLevel.isClientSide) {
            BlockEntity pBlockEntity = pLevel.getBlockEntity(pPos);
            if (pBlockEntity instanceof TestBlockEntity blockEntity) {
                blockEntity.setChanged();
            }
            return InteractionResult.SUCCESS;
        } else {
            BlockEntity pBlockEntity = pLevel.getBlockEntity(pPos);
            if (pBlockEntity instanceof TestBlockEntity blockEntity) {
                blockEntity.getAnimationInstance().triggerTransition();
                blockEntity.replicateAnimationInstance();
            }
            return InteractionResult.CONSUME;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level pLevel,
                                                                   @NotNull BlockState pState,
                                                                   @NotNull BlockEntityType<T> pBlockEntityType) {
        if (pBlockEntityType == ExampleModRegister.TEST_BLOCK_ENTITY_TYPE) {
            return (BlockEntityTicker<T>) ticker;
        } else {
            return null;
        }
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }
}
