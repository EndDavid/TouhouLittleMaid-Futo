package com.github.tartaricacid.touhoulittlemaid.block;

import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntitySnackCabinet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 零食柜，移植自上游 1.20.1 的 {@code block/BlockSnackCabinet}。
 *
 * <p><b>这是 1.20.1 新增的内容</b>：1.18.2 的 jar 里既没有这个方块，也没有
 * {@code recipes/altar/snack_cabinet.json}。本仓库 baseline 早就拷了它的模型与贴图
 * （{@code models/bedrock/block/snack_cabinet.json}、{@code textures/bedrock/block/snack_cabinet.png}、
 * 物品模型），但方块与配方一直没接上 —— 于是那条祭坛配方也就无从谈起。</p>
 *
 * <h2>方块本体是「不可见」的</h2>
 * <p>{@code models/block/snack_cabinet.json} 里<b>只有 particle 贴图、没有 elements</b>，
 * 所以世界里的柜子完全由方块实体渲染器用基岩模型画（与祭坛 / 神社 / 三张棋盘同一条管线）。
 * 这也解释了为什么 {@code blockstates} 里只有一个空 variants —— 它不按 state 换模型。</p>
 *
 * <h2>TYPE 属性做什么</h2>
 * <p>上游用它表示「柜子里装了多少」：{@code 0} 空、{@code 1} 满、{@code 2} 半。
 * 相邻的柜子会把状态传染过来（{@code getStateForPlacement} 与 {@code updateShape} 里
 * 检查上方邻居的标签）。本移植版保留了这套 state 与标签判断，
 * 因为女仆「取零食」的行为要按它决定去哪个柜子拿。</p>
 *
 * <h2>与上游的差异</h2>
 * <p>上游 extends {@code BaseEntityBlock} 并自己实现 {@code use} → {@code openMenu}。
 * 这里同样如此，但<b>没有</b>实现「蹲下 + 方块物品点顶面 = 叠一个半高柜」那一段
 * （上游用 {@code TagBlock.SNACK_CABINET_HALF} 传染 TYPE）。那段只影响外观分层，
 * 不影响「能装 27 格食物、能被女仆取用」这一核心功能；等柜子的视觉分层需求明确了再补。</p>
 */
public class BlockSnackCabinet extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty TYPE = IntegerProperty.create("type", 0, 2);
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
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
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

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        // 手持方块物品点顶面时不打开界面：那一下是留给「往上叠一层」的
        if (hit.getDirection() == Direction.UP && player.getItemInHand(hand).getItem() instanceof BlockItem) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof TileEntitySnackCabinet cabinet) {
            player.openMenu(cabinet);
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

