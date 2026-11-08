package com.github.tartaricacid.touhoulittlemaid.block;

import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.item.ItemMaidBeacon;
import com.github.tartaricacid.touhoulittlemaid.network.MaidBeaconPacket;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityMaidBeacon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * 神社庭灯，移植自上游 {@code block/BlockMaidBeacon}。
 *
 * <p>本体是<b>两格高</b>的方块：下半格（{@code position=down}）只是灯座，是个纯装饰性的
 * 支撑块；上半格（{@code position=up_n_s} / {@code up_w_e}）才是真正的庭灯，方块实体、
 * 光源（亮度 15）和 GUI 都挂在它上面。两格朝向由玩家放置时的朝向决定，
 * 东西向与南北向是两份不同的模型（{@code up_w_e} 只是 {@code up_n_s} 转 90°）。</p>
 *
 * <p><b>与上游的 1.18.2 差异（两处，均为 Forge 专有 API）：</b></p>
 * <ul>
 *     <li>{@code Block#getPistonPushReaction} 是 Forge 补丁，原版 1.18.2 上活塞反应由
 *         {@code Material#getPushReaction()} 决定。因此这里自定义了一个
 *         {@link #BEACON_MATERIAL}（与 {@code Material.WOOD} 等价，但
 *         {@link PushReaction#BLOCK}），否则活塞会把上半格推走、留下孤零零的灯座。
 *         1.20 之后原版把推力反应搬到了 {@code BlockBehaviour.Properties}，这个兼容层就不需要了；</li>
 *     <li>拾取方块（中键）的方法签名：Forge 是
 *         {@code getCloneItemStack(BlockState, HitResult, BlockGetter, BlockPos, Player)}，
 *         原版 1.18.2 是 {@link Block#getCloneItemStack(BlockGetter, BlockPos, BlockState)}。
 *         语义相同：永远给一个「空壳」庭灯，不要把方块实体里的 P 点复制出来。</li>
 * </ul>
 */
public class BlockMaidBeacon extends BaseEntityBlock {
    public static final EnumProperty<Position> POSITION = EnumProperty.create("position", Position.class);

    /**
     * 与 {@code Material.WOOD} 同参数，只把活塞反应换成 {@link PushReaction#BLOCK}。
     *
     * <p>构造器参数顺序见 {@code Material(MaterialColor, liquid, solid, blocksMotion,
     * solidBlocking, flammable, replaceable, pushReaction)}。</p>
     */
    private static final Material BEACON_MATERIAL = new Material(MaterialColor.WOOD,
            false, true, true, true, true, false, PushReaction.BLOCK);

    private static final VoxelShape UP_AABB = Block.box(3, 1, 3, 13, 16, 13);
    private static final VoxelShape DOWN_AABB = Block.box(6.5, 0, 6.5, 9.5, 26, 9.5);

    public BlockMaidBeacon() {
        super(BlockBehaviour.Properties.of(BEACON_MATERIAL).strength(2, 2).noOcclusion()
                .lightLevel(state -> state.getValue(POSITION) == Position.DOWN ? 0 : 15));
        this.registerDefaultState(this.stateDefinition.any().setValue(POSITION, Position.DOWN));
    }

    /**
     * {@code BaseEntityBlock} 默认是 {@code RenderShape.INVISIBLE}（只给方块实体渲染器用），
     * 本方块走普通 JSON 模型，必须显式改回 {@code MODEL}。
     */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * 只有上半格有方块实体；下半格返回 {@code null}（原版允许）。
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(POSITION) != Position.DOWN) {
            return new TileEntityMaidBeacon(pos, state);
        }
        return null;
    }

    /**
     * 只有服务端需要 tick（吸取 P 点 / 施加效果）。
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, com.github.tartaricacid.touhoulittlemaid.init.InitBlocks.MAID_BEACON_TE,
                TileEntityMaidBeacon::serverTick);
    }

    /**
     * 右击上半格：服务端下发「打开 GUI」包。
     *
     * <p>上游是 {@code NetworkHandler.sendToClientPlayer(new OpenBeaconGuiMessage(pos), player)}，
     * 本移植版走 Fabric 的单通道动作码包，见 {@link MaidBeaconPacket#sendOpenGui}。</p>
     */
    @Override
    public InteractionResult use(BlockState state, Level worldIn, BlockPos pos, Player player,
                                 InteractionHand handIn, BlockHitResult hit) {
        if (worldIn.getBlockEntity(pos) instanceof TileEntityMaidBeacon) {
            if (!worldIn.isClientSide) {
                MaidBeaconPacket.sendOpenGui(player, pos);
            }
            return InteractionResult.sidedSuccess(worldIn.isClientSide);
        }
        return super.use(state, worldIn, pos, player, handIn, hit);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        Position position = state.getValue(POSITION);
        return position == Position.DOWN ? DOWN_AABB : UP_AABB;
    }

    /**
     * 两格互相依存：任一半被破坏，另一半在 {@code updateShape} 里一起变成空气。
     *
     * <p>注意上半格的「变成空气」会触发 {@code onRemove}，庭灯物品（带存储的 P 点）
     * 就是在那时弹出的 —— 所以拆掉任意一半都只掉一个物品，不会丢数据也不会复制。</p>
     */
    @Override
    public BlockState updateShape(BlockState stateIn, Direction facing, BlockState facingState,
                                  LevelAccessor worldIn, BlockPos currentPos, BlockPos facingPos) {
        if (facing.getAxis() == Direction.Axis.Y) {
            Position position = stateIn.getValue(POSITION);
            if (position == Position.DOWN && facing == Direction.UP) {
                if (!facingState.is(this) || facingState.getValue(POSITION) == Position.DOWN) {
                    return Blocks.AIR.defaultBlockState();
                }
            }
            if (position != Position.DOWN && facing == Direction.DOWN) {
                if (!facingState.is(this)
                        || facingState.getValue(POSITION) == Position.UP_W_E
                        || facingState.getValue(POSITION) == Position.UP_N_S) {
                    return Blocks.AIR.defaultBlockState();
                }
            }
        }
        return super.updateShape(stateIn, facing, facingState, worldIn, currentPos, facingPos);
    }

    /**
     * 创造模式拆上半格时，顺手把下半格也清掉（否则会留下没有实体的灯座）。
     * {@code UPDATE_SUPPRESS_DROPS} 保证不会额外掉一个灯座物品。
     */
    @Override
    public void playerWillDestroy(Level worldIn, BlockPos pos, BlockState state, Player player) {
        if (!worldIn.isClientSide && player.isCreative()) {
            Position position = state.getValue(POSITION);
            if (position != Position.DOWN) {
                BlockPos belowPos = pos.below();
                BlockState belowState = worldIn.getBlockState(belowPos);
                if (belowState.is(this) && belowState.getValue(POSITION) == Position.DOWN) {
                    worldIn.setBlock(belowPos, Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    worldIn.levelEvent(player, LevelEvent.PARTICLES_DESTROY_BLOCK, belowPos, Block.getId(belowState));
                }
            }
        }
        super.playerWillDestroy(worldIn, pos, state, player);
    }

    /**
     * 方块实体在这里「变成物品」，把存储的 P 点带回去。
     *
     * <p>1.18.2 上游没有 {@code !state.is(newState.getBlock()) && !isMoving} 这个守卫，
     * 结果是<b>换朝向 / 被活塞处理等状态变更</b>也会走 {@code onRemove}，凭空多掉一个庭灯。
     * 这里对齐 1.20.1 上游的写法加上守卫。</p>
     *
     * <p>上游也没有为 {@code maid_beacon} 提供战利品表（{@code port/upstream-data} 里
     * 只有 bookshelf / computer / keyboard / maid_bed / model_switcher / scarecrow / shrine），
     * 因为掉落完全由这里负责 —— 再挂战利品表就会掉成两份。</p>
     */
    @Override
    public void onRemove(BlockState state, Level worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !isMoving) {
            BlockEntity te = worldIn.getBlockEntity(pos);
            if (te instanceof TileEntityMaidBeacon beacon) {
                popResource(worldIn, pos, ItemMaidBeacon.tileEntityToItemStack(beacon));
            }
        }
        super.onRemove(state, worldIn, pos, newState, isMoving);
    }

    /**
     * 中键获取方块 → 一把空庭灯（不带 P 点）。
     */
    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(InitItems.MAID_BEACON);
    }

    /**
     * 不能在最高一层放置（上半格需要空间）。
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos blockpos = context.getClickedPos();
        Level world = context.getLevel();
        int maxHeight = world.getMaxBuildHeight() - 1;
        if (blockpos.getY() < maxHeight && world.getBlockState(blockpos.above()).canBeReplaced(context)) {
            return super.getStateForPlacement(context);
        }
        return null;
    }

    /**
     * 放下上半格，并把物品里存的 P 点 / 效果还原到方块实体里。
     */
    @Override
    public void setPlacedBy(Level worldIn, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        Direction facing = getHorizontalDirection(placer);
        BlockState stateUp;
        if (facing == Direction.SOUTH || facing == Direction.NORTH) {
            stateUp = this.defaultBlockState().setValue(POSITION, Position.UP_N_S);
        } else {
            stateUp = this.defaultBlockState().setValue(POSITION, Position.UP_W_E);
        }
        worldIn.setBlock(pos.above(), stateUp, Block.UPDATE_ALL);
        BlockEntity te = worldIn.getBlockEntity(pos.above());
        if (te instanceof TileEntityMaidBeacon beacon) {
            ItemMaidBeacon.itemStackToTileEntity(stack, beacon);
            beacon.refresh();
        }
    }

    /**
     * 结构方块旋转时同步翻转上半格的朝向。
     *
     * <p>1.18.2 原版的签名只有 {@code (BlockState, Rotation)}；带
     * {@code LevelAccessor / BlockPos} 的重载是 Forge 补丁，这里不需要。</p>
     */
    @Override
    public BlockState rotate(BlockState state, Rotation direction) {
        switch (direction) {
            default:
                return state;
            case CLOCKWISE_90:
            case COUNTERCLOCKWISE_90:
                if (state.getValue(POSITION) == Position.UP_N_S) {
                    return state.setValue(POSITION, Position.UP_W_E);
                }
                if (state.getValue(POSITION) == Position.UP_W_E) {
                    return state.setValue(POSITION, Position.UP_N_S);
                }
                return state;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POSITION);
    }

    private Direction getHorizontalDirection(@Nullable LivingEntity placer) {
        return placer == null ? Direction.NORTH : placer.getDirection();
    }

    /**
     * 方块状态。名字必须与 {@code blockstates/maid_beacon.json} 的变体一一对应
     * （{@code position=down} / {@code position=up_n_s} / {@code position=up_w_e}）。
     */
    public enum Position implements StringRepresentable {
        UP_N_S, UP_W_E, DOWN;

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.US);
        }

        @Override
        public String toString() {
            return getSerializedName();
        }
    }
}

