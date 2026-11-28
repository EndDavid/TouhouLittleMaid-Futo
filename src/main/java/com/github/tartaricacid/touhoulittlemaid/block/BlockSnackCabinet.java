package com.github.tartaricacid.touhoulittlemaid.block;

import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntitySnackCabinet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 零食柜，移植自上游 1.20.1 的 {@code block/BlockSnackCabinet}（1.18.2 没有这个方块）。
 *
 * <p>{@link #TYPE} 表示<b>柜顶摆了什么</b>，只影响外观分层：{@code 0} 空、
 * {@code 1} 满（高玻璃罩 {@code full}）、{@code 2} 半（矮玻璃罩 {@code half}），
 * 判定依据是上方方块的标签 {@link #SNACK_CABINET_FULL} / {@link #SNACK_CABINET_HALF}。</p>
 *
 * <p>方块本身不画（{@code ENTITYBLOCK_ANIMATED}），几何全在方块实体渲染器里。</p>
 */
public class BlockSnackCabinet extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty TYPE = IntegerProperty.create("type", 0, 2);
    /** 顶上是「满」的方块类食物（蛋糕 / 烤鸡 …）→ 画高玻璃罩。 */
    public static final TagKey<Block> SNACK_CABINET_FULL = TagKey.create(Registry.BLOCK_REGISTRY,
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "snack_cabinet_full"));
    /** 顶上是「半」的方块类食物（派 / 芝士蛋糕 …）→ 画矮玻璃罩。 */
    public static final TagKey<Block> SNACK_CABINET_HALF = TagKey.create(Registry.BLOCK_REGISTRY,
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "snack_cabinet_half"));

    public static final int TYPE_NONE = 0;
    public static final int TYPE_FULL = 1;
    public static final int TYPE_HALF = 2;

    public BlockSnackCabinet() {
        super(BlockBehaviour.Properties.of(Material.WOOD)
                .sound(SoundType.WOOD)
                .strength(2.0F, 3.0F)
                .noOcclusion());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(TYPE, TYPE_NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TYPE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 放下时就把顶上的情况算进去 —— 否则「先把蛋糕摆好、再把柜子塞到下面」
        // 要等一次方块更新才会出玻璃罩。
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(TYPE, typeFor(level.getBlockState(pos.above())));
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /**
     * 上方邻居变化时重算 {@link #TYPE}（上游同名覆写）。
     *
     * <p>原版调这个方法时 {@code direction} 是「从本方块指向邻居」的方向，
     * 所以我们只关心 {@link Direction#UP}。</p>
     */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.UP) {
            return state.setValue(TYPE, typeFor(neighborState));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    /**
     * 上方方块对应哪个外观档位。
     *
     * <p>顺序要紧：同一个方块可能同时被两个标签收录（整合包作者手滑），
     * 上游是「先判 half、再判 full」，这里保持一致。</p>
     */
    private static int typeFor(BlockState above) {
        if (above.is(SNACK_CABINET_HALF)) {
            return TYPE_HALF;
        }
        if (above.is(SNACK_CABINET_FULL)) {
            return TYPE_FULL;
        }
        return TYPE_NONE;
    }

    /** 方块本身不画（几何全在方块实体渲染器里），但仍有 `particle` 贴图供破坏粒子用。 */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /** 有 27 格物品：比较器按容器填充度输出信号（与上游一致）。 */
    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntitySnackCabinet(pos, state);
    }

    /**
     * 右键打开 27 格存储。界面就是原版单箱（{@code ChestMenu.threeRows} + {@code ChestScreen}）。
     *
     * <p>不处理「手持方块点顶面」：那个分支只服务于上游「蹲下往柜顶叠一层」的动作，
     * 而原版客户端在蹲下且手上有物品时根本不会调用方块的 {@code use}。</p>
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof TileEntitySnackCabinet cabinet) {
            player.openMenu(cabinet);
        } else {
            TouhouLittleMaid.LOGGER.warn("[Futo] snack cabinet at {} has no block entity ({})",
                    pos, blockEntity);
        }
        return InteractionResult.CONSUME;
    }

    /** 拆掉时把 27 格里的东西全部掉出来，否则玩家会丢物品。 */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof Container container) {
                Containers.dropContents(level, pos, container);
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    /** 放下来时把自定义名字写进方块实体（命名牌改名后放置的柜子会记住名字）。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        if (stack.hasCustomHoverName()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof TileEntitySnackCabinet cabinet) {
                cabinet.setCustomName(stack.getHoverName());
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(new TranslatableComponent("block.touhou_little_maid.snack_cabinet.tip")
                .withStyle(ChatFormatting.GRAY));
    }
}

