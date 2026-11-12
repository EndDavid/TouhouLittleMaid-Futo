package com.github.tartaricacid.touhoulittlemaid.block;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 女仆床，逐字移植自上游 {@code block/BlockMaidBed}。
 *
 * <h2>它和原版床的区别</h2>
 * <ul>
 *     <li><b>两格长的双格方块</b>（FOOT + HEAD），和原版床一样靠 {@code PART} 维护；
 *         但占的碰撞箱很矮（0~9/16），女仆是「趴」在上面而不是被抬起来；</li>
 *     <li><b>可以弹跳</b>：落在床上会把下落速度反弹 66%（见 {@link #updateEntityAfterFallOn}），
 *         所以床能当蹦床用 —— 上游就是想要这个手感；</li>
 *     <li>{@link #isMaidBed} 对女仆床返回 true，这样女仆的 {@code startSleeping}
 *         与寻路系统才认这张床。女仆的「睡觉」行为见 {@code MaidBedTask}。</li>
 * </ul>
 */
public class BlockMaidBed extends HorizontalDirectionalBlock {
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
    public static final BooleanProperty OCCUPIED = BlockStateProperties.OCCUPIED;

    protected static final VoxelShape BASE = Block.box(0.0, 0.0, 0.0, 16.0, 9.0, 16.0);

    public BlockMaidBed() {
        super(BlockBehaviour.Properties.of(Material.WOOL).sound(SoundType.WOOD).strength(0.2F).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(PART, BedPart.FOOT).setValue(OCCUPIED, false));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        return BASE;
    }

    @Override
    public BlockState updateShape(BlockState stateIn, Direction facing, BlockState facingState,
                                  LevelAccessor worldIn, BlockPos currentPos, BlockPos facingPos) {
        // 另一半没了，整张床就消失
        if (facing == getNeighbourDirection(stateIn.getValue(PART), stateIn.getValue(FACING))) {
            if (facingState.is(this) && facingState.getValue(PART) != stateIn.getValue(PART)) {
                return stateIn.setValue(OCCUPIED, facingState.getValue(OCCUPIED));
            }
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(stateIn, facing, facingState, worldIn, currentPos, facingPos);
    }

    @Override
    public void playerWillDestroy(Level worldIn, BlockPos pos, BlockState state, Player player) {
        // 创造模式下手动清掉另一半，否则床头会留下来而不掉落
        if (!worldIn.isClientSide && player.isCreative()) {
            BedPart bedpart = state.getValue(PART);
            if (bedpart == BedPart.FOOT) {
                BlockPos blockpos = pos.relative(getNeighbourDirection(bedpart, state.getValue(FACING)));
                BlockState blockstate = worldIn.getBlockState(blockpos);
                if (blockstate.getBlock() == this && blockstate.getValue(PART) == BedPart.HEAD) {
                    worldIn.setBlock(blockpos, Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    worldIn.levelEvent(player, LevelEvent.PARTICLES_DESTROY_BLOCK, blockpos, Block.getId(blockstate));
                }
            }
        }
        super.playerWillDestroy(worldIn, pos, state, player);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, OCCUPIED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.getHorizontalDirection();
        BlockPos relativePos = context.getClickedPos().relative(direction);
        return context.getLevel().getBlockState(relativePos).canBeReplaced(context)
                ? this.defaultBlockState().setValue(FACING, direction) : null;
    }

    @Override
    public void setPlacedBy(Level worldIn, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(worldIn, pos, state, placer, stack);
        if (!worldIn.isClientSide) {
            BlockPos blockpos = pos.relative(state.getValue(FACING));
            worldIn.setBlock(blockpos, state.setValue(PART, BedPart.HEAD), Block.UPDATE_ALL);
            worldIn.blockUpdated(pos, Blocks.AIR);
            state.updateNeighbourShapes(worldIn, pos, 3);
        }
    }

    /**
     * 掉一半的摔落伤害 —— 床是软的。
     *
     * <p>注意上游是在<b>调用</b> {@code super} 时把参数乘以 0.5 再传进去，
     * 所以真正的减半发生在 {@code Block#fallOn} 内部，这里必须照写。</p>
     */
    @Override
    public void fallOn(Level worldIn, BlockState blockState, BlockPos pos, Entity entityIn, float fallDistance) {
        super.fallOn(worldIn, blockState, pos, entityIn, fallDistance * 0.5f);
    }

    /**
     * 弹跳：落在床上时把竖直速度反向取 66%（活体），非活体 80% 的系数。
     */
    @Override
    public void updateEntityAfterFallOn(BlockGetter worldIn, Entity entity) {
        if (entity.isSuppressingBounce()) {
            super.updateEntityAfterFallOn(worldIn, entity);
        } else {
            Vec3 movement = entity.getDeltaMovement();
            if (movement.y < 0) {
                double modulus = entity instanceof LivingEntity ? 1.0D : 0.8D;
                entity.setDeltaMovement(movement.x, -movement.y * (double) 0.66F * modulus, movement.z);
            }
        }
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.DESTROY;
    }

    /**
     * 种子按「整张床」而不是单格计算，这样床的两半纹理 / 随机事件保持一致。
     *
     * <p>上游带 {@code @OnlyIn(Dist.CLIENT)}，原版会无条件调用，
     * 这里去掉注解（方法体本身没有任何客户端类型）。</p>
     */
    @Override
    public long getSeed(BlockState state, BlockPos pos) {
        BlockPos blockpos = pos.relative(state.getValue(FACING), state.getValue(PART) == BedPart.HEAD ? 0 : 1);
        return Mth.getSeed(blockpos.getX(), pos.getY(), blockpos.getZ());
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter worldIn, BlockPos pos, PathComputationType type) {
        return false;
    }

    /**
     * 只有女仆能躺上去。
     *
     * <p><b>为什么这里没有覆盖 {@code Block#isBed}：</b>1.18.2 原版<b>没有</b>这个方法 ——
     * 它是 Forge 的补丁加上的（{@code Block#isBed(BlockState, BlockGetter, BlockPos, Entity)}），
     * 原版的 {@code LivingEntity#startSleeping} 直接判定
     * {@code getBlockState(pos).getBlock() instanceof BedBlock}。本移植版不是
     * {@code BedBlock} 的子类（床的形状 / 双格语义都自定义了），所以那一步改为在
     * {@code EntityMaid#startSleeping} 里显式处理 {@link #OCCUPIED}，
     * 语义与 Forge 补丁完全一致。</p>
     */
    public static boolean isMaidBed(BlockState state) {
        return state.getBlock() instanceof BlockMaidBed;
    }

    /**
     * 占用标记的读写入口，供 {@code EntityMaid#startSleeping} / {@code #stopSleeping} 调用。
     *
     * <p>Forge 的补丁在 {@code startSleeping} 里对 {@code isBed} 为真的方块做
     * {@code setValue(OCCUPIED, true)} 并 {@code setBlock(..., 3)}；这里照抄同样的两步：
     * 只改被点击的那一格，另一半由 {@link #updateShape} 自动同步（它会把邻居的
     * {@code OCCUPIED} 抄过来）。</p>
     */
    public static void setBedOccupied(Level level, BlockPos pos, boolean occupied) {
        BlockState state = level.getBlockState(pos);
        if (isMaidBed(state) && state.getValue(OCCUPIED) != occupied) {
            level.setBlock(pos, state.setValue(OCCUPIED, occupied), Block.UPDATE_ALL);
        }
    }

    /**
     * 睡觉时头朝哪边 —— 读女仆床的 {@code FACING}。
     *
     * <p><b>注意：{@code EntityMaid#getBedOrientation()} 已经不再调用它。</b>
     * 上游根本没有覆写 {@code getBedOrientation()}，而 Forge 那条默认实现只能认出
     * {@code BedBlock}，所以上游女仆床的朝向查询恒为 {@code null}、也就不会做
     * {@code LivingEntityRenderer} 里那一步「{@code -bedFacing * (eyeHeight - 0.1)}」的
     * 水平位移。本移植版之前自己加了这层覆写，结果女仆整体沿床长方向偏了约 1.2 格 ——
     * 现在已按上游改回。这个方法保留下来是给需要「女仆床朝向」的地方用的工具方法。</p>
     */
    public static Direction getMaidBedFacing(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return isMaidBed(state) ? state.getValue(FACING) : null;
    }

    private Direction getNeighbourDirection(BedPart part, Direction direction) {
        return part == BedPart.FOOT ? direction : direction.getOpposite();
    }
}
