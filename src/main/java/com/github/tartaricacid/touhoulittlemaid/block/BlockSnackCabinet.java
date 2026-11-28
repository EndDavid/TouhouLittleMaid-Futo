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
import net.minecraft.world.item.BlockItem;
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
 * <p>它表示<b>柜子顶上摆了什么</b>，只影响外观分层（反编译上游 1.20.1 的
 * {@code TileEntitySnackCabinetRenderer} 可见）：</p>
 * <ul>
 *     <li>{@code 0} —— 顶上是空的：只画柜体本身（1×1 的木柜）；</li>
 *     <li>{@code 1} —— 顶上是一个「满」的食物（蛋糕 / 烤鸡），画高玻璃罩 {@code full} 骨骼；</li>
 *     <li>{@code 2} —— 顶上是一个「半」的食物（派 / 芝士蛋糕），画矮玻璃罩 {@code half} 骨骼。</li>
 * </ul>
 * <p>判定依据是<b>上方方块的方块标签</b>：{@link #SNACK_CABINET_FULL} 与
 * {@link #SNACK_CABINET_HALF}。这两个标签上游由 datagen 生成，本移植版直接随包给 JSON
 * （{@code data/touhou_little_maid/tags/blocks/snack_cabinet_{full,half}.json}，内容照抄上游）。</p>
 *
 * <p><b>与上游的一处差异：</b>上游只在 {@code updateShape} 里「命中标签才改 TYPE」，
 * 邻居变成空气时直接走 {@code super}，于是<b>把食物拿走后玻璃罩不会消失</b>（要等下一次
 * 方块更新才可能被改掉）。这里改成三分支都赋值：上方既不在 full 也不在 half 标签里 → {@code 0}。
 * 这样「拿走食物 → 罩子消失」是即时且确定的。</p>
 *
 * <h2>与上游的其他差异</h2>
 * <p>上游 extends {@code BaseEntityBlock} 并自己实现 {@code use} → {@code openMenu}，这里同样。</p>
 *
 * <p>上游还有一段「蹲下 + 方块物品点顶面 = 往上叠一层」，本移植版没做：
 * 它依赖上游 {@code MaidConfig} 里的开关与其他方块的摸索行为，
 * 而「能装 27 格、能被女仆取用、顶上的食物有玻璃罩」这三件已经完整。</p>
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

