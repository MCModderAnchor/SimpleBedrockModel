package example.client.worldmesh;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.world.GeometryCollector;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.world.MeshRenderableAdapter;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.world.WorldMeshStrategy;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.world.WorldMeshGroup;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.world.WorldMeshRenderer;
import example.block.blockentity.TestBlockEntity;
import example.init.ExampleModRegister;
import example.resource.KnownResources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** 只在 BER 发现实体并登记；有效性与状态通过同一个无状态适配器读取。 */
public final class ExampleBlockMeshGroup {
    private static final double RANGE_SQR = 128.0 * 128.0;
    private static final ResourceLocation TEST_TEXTURE = ExampleModRegister.modLoc("textures/block/test.png");
    private static final ResourceLocation POLY_TEXTURE = ExampleModRegister.modLoc("textures/block/vct.png");
    private static WorldMeshGroup<TestBlockEntity> group;

    private static final MeshRenderableAdapter<TestBlockEntity> ADAPTER = new MeshRenderableAdapter<>() {
        @Override
        public boolean isValid(TestBlockEntity entity) {
            Minecraft minecraft = Minecraft.getInstance();
            return !entity.isRemoved() && entity.getLevel() != null && entity.getLevel() == minecraft.level
                    && minecraft.player != null
                    && entity.getBlockPos().distToCenterSqr(minecraft.player.getEyePosition()) <= RANGE_SQR;
        }

        @Override public Vec3 origin(TestBlockEntity entity) { return Vec3.atLowerCornerOf(entity.getBlockPos()); }
        @Override public int packedLight(TestBlockEntity entity) {
            return LevelRenderer.getLightColor(entity.getLevel(), entity.getBlockPos());
        }
        @Override public Object geometryKey(TestBlockEntity entity) {
            return GeometryCollector.blockModelKey(modelId(entity), texture(entity), facing(entity));
        }
        @Override public boolean collectGeometry(TestBlockEntity entity, GeometryCollector collector) {
            return collector.blockModel(modelId(entity), texture(entity), facing(entity));
        }
    };

    private static ResourceLocation modelId(TestBlockEntity entity) {
        return entity.getBlockState().is(ExampleModRegister.POLY_MESH_TEST_BLOCK) ? KnownResources.POLY_MESH_TEST : KnownResources.TEST;
    }

    private static ResourceLocation texture(TestBlockEntity entity) {
        return entity.getBlockState().is(ExampleModRegister.POLY_MESH_TEST_BLOCK) ? POLY_TEXTURE : TEST_TEXTURE;
    }

    private static Direction facing(TestBlockEntity entity) {
        var state = entity.getBlockState();
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
    }

    private ExampleBlockMeshGroup() {}

    public static void register() {
        if (group == null) group = WorldMeshRenderer.createGroup("example:test_block", WorldMeshStrategy.INSTANCE);
    }

    /** 同一实体可每帧重复登记，无需维护第二份对象表；关闭网格绘制层时 BER 继续普通绘制。 */
    public static boolean tryEnqueue(TestBlockEntity entity) {
        if (group == null || entity.getLevel() == null || Minecraft.getInstance().level == null || entity.isRemoved()) return false;
        group.track(entity, ADAPTER);
        return WorldMeshRenderer.isEnabled();
    }
}
