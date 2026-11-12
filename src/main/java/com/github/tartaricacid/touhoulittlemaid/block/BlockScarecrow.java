package com.github.tartaricacid.touhoulittlemaid.block;

import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MiscConfig;
import com.github.tartaricacid.touhoulittlemaid.util.VoxelShapeUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 稻草人，逐字移植自上游 {@code block/BlockScarecrow}。
 *
 * <p>两格高的装饰方块，作用是<b>抑制女仆妖精的生成</b>：{@code EntityFairy} 刷怪前会查
 * {@code InitPoi.SCARECROW} 附近有没有稻草人，范围内有就不刷
 * （半径就是 {@code MiscConfig#SCARECROW_RANGE}，默认 48 格）。</p>
 *
 * <p>它只在<b>下半格</b>注册 POI —— 上半格是纯形状，见
 * {@code entity/poi/MaidPoiManager}。</p>
 *
 * <h2>为什么上半部分的碰撞箱要旋转</h2>
 * <p>上半格是十字架形状的「手臂」，只写了朝北的那一份，东南西三个朝向由
 * {@link VoxelShapeUtils#rotateHorizontal} 转 90° / 180° / 270° 得到。
 * 手写四份坐标容易错位，上游也是这么做的。</p>
 */
public class BlockScarecrow extends HorizontalDirectionalBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    protected static final VoxelShape LOWER_AABB = Shapes.or(
            Block.box(1, 0, 1, 15, 4, 15),
            Block.box(2, 4, 2, 14, 8, 14),
            Block.box(4.5, 8, 4.5, 11.5, 16, 11.5)
    );

    protected static final VoxelShape UPPER_AABB_NORTH = Block.box(4, 0, 7.5, 12, 7.5, 13.5);
    protected static final VoxelShape UPPER_AABB_SOUTH = VoxelShapeUtils.rotateHorizontal(UPPER_AABB_NORTH, Direction.SOUTH);
    protected static final VoxelShape UPPER_AABB_EAST = VoxelShapeUtils.rotateHorizontal(UPPER_AABB_NORTH, Direction.EAST);
    protected static final VoxelShape UPPER_AABB_WEST = VoxelShapeUtils.rotateHorizontal(UPPER_AABB_NORTH, Direction.WEST);

    public BlockScarecrow() {
        super(BlockBehaviour.Properties.of(Material.STONE).sound(SoundType.GRASS).strength(0.2F).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(HALF, DoubleBlockHalf.LOWER).setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        // 上下两格必须成对存在，缺一个就整体消失（与上游一致，不做流体处理）
        DoubleBlockHalf currentHalf = state.getValue(HALF);
        boolean isLower = currentHalf == DoubleBlockHalf.LOWER && direction == Direction.UP;
        boolean isUpper = currentHalf == DoubleBlockHalf.UPPER && direction == Direction.DOWN;
        if (isLower || isUpper) {
            if (neighborState.is(this) && neighborState.getValue(HALF) != currentHalf) {
                return state.setValue(FACING, neighborState.getValue(FACING));
            }
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // 阻止创造模式破坏掉落双份物品
        if (!level.isClientSide && player.isCreative()) {
            DoubleBlockHalf half = state.getValue(HALF);
            if (half == DoubleBlockHalf.UPPER) {
                BlockPos belowPos = pos.below();
                BlockState belowState = level.getBlockState(belowPos);
                if (belowState.is(state.getBlock()) && belowState.getValue(HALF) == DoubleBlockHalf.LOWER) {
                    BlockState empty = belowState.getFluidState().is(Fluids.WATER)
                            ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    level.setBlock(belowPos, empty, Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    level.levelEvent(player, LevelEvent.PARTICLES_DESTROY_BLOCK, belowPos, Block.getId(belowState));
                }
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos clickedPos = context.getClickedPos();
        Level level = context.getLevel();
        BlockPos abovePos = clickedPos.above();
        // 头顶要有空位，否则放不下来
        if (clickedPos.getY() < level.getMaxBuildHeight() - 1
                && level.getBlockState(abovePos).canBeReplaced(context)) {
            Direction horizontalDirection = context.getHorizontalDirection();
            return this.defaultBlockState()
                    .setValue(FACING, horizontalDirection).setValue(HALF, DoubleBlockHalf.LOWER);
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return LOWER_AABB;
        }
        return switch (state.getValue(FACING)) {
            case SOUTH -> UPPER_AABB_SOUTH;
            case EAST -> UPPER_AABB_EAST;
            case WEST -> UPPER_AABB_WEST;
            default -> UPPER_AABB_NORTH;
        };
    }

    /**
     * 提示里直接写出当前的抑制半径（配置项可改，所以必须动态读）。
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level,
                                List<Component> tooltip, TooltipFlag flag) {
        int range = MiscConfig.SCARECROW_RANGE.get();
        tooltip.add(new TranslatableComponent("tooltips.touhou_little_maid.scarecrow.desc", range, range)
                .withStyle(ChatFormatting.GRAY));
    }
}

