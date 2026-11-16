package com.github.tartaricacid.touhoulittlemaid.block;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityJoy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 娱乐方块基类（棋类 / 键盘 / 书架 / 电脑），移植自上游 {@code block/BlockJoy}。
 *
 * <p>共同行为只有一条：<b>空手右击 = 坐下</b>。坐下的不是玩家本身，而是一个
 * {@code EntitySit} 实体 —— 原版 {@code Entity#startRiding} 需要目标实体有合理体积与同步，
 * 而「椅子」既不该被推动也不该被攻击，所以用专门的哑实体最省事。</p>
 *
 * <p>坐下以后方向盘交给 {@code EntitySit#tickMaid}：它每 20 tick 给一次好感度，
 * 并且判断当前作息 / 工作模式是否允许继续坐着（工作模式下只有 {@code canSitInJoy} 返回 true 的任务能坐）。</p>
 */
public abstract class BlockJoy extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    protected BlockJoy(Properties properties) {
        super(properties);
    }

    public BlockJoy() {
        this(Properties.of(Material.WOOD).sound(SoundType.WOOD).strength(2.0F, 3.0F).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /**
     * 座位相对方块原点的偏移。
     */
    protected abstract Vec3 sitPosition();

    /**
     * 显示在好感度类型里的名字（{@code entity/favorability/Type}）。
     */
    protected abstract String getTypeName();

    /**
     * 座位朝向的额外偏移（度）。
     */
    protected abstract int sitYRot();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public InteractionResult use(BlockState state, Level worldIn, BlockPos pos, Player playerIn, InteractionHand hand, BlockHitResult hit) {
        if (worldIn instanceof ServerLevel serverLevel && playerIn.getItemInHand(hand).isEmpty()
                && worldIn.getBlockEntity(pos) instanceof TileEntityJoy joy) {
            Entity oldSitEntity = serverLevel.getEntity(joy.getSitId());
            if (oldSitEntity != null && oldSitEntity.isAlive()) {
                return super.use(state, worldIn, pos, playerIn, hand, hit);
            }
            Vec3 sitPos = new Vec3(pos.getX() + this.sitPosition().x, pos.getY() + this.sitPosition().y,
                    pos.getZ() + this.sitPosition().z);
            EntitySit newSitEntity = new EntitySit(worldIn, sitPos, this.getTypeName(), pos);
            newSitEntity.setYRot(state.getValue(FACING).getOpposite().toYRot() + this.sitYRot());
            worldIn.addFreshEntity(newSitEntity);
            joy.setSitId(newSitEntity.getUUID());
            joy.setChanged();
            playerIn.startRiding(newSitEntity);
            return InteractionResult.SUCCESS;
        }
        return super.use(state, worldIn, pos, playerIn, hand, hit);
    }

    /**
     * 女仆坐下（由 {@code IBoardGameBlock} 的子类或 AI 调用）。
     *
     * <p>与玩家坐下共用同一套 {@code EntitySit}；如果座位上已经有人，直接放弃 ——
     * 否则两个乘客会挂在同一个实体上，位置完全重合。</p>
     */
    public void startMaidSit(EntityMaid maid, BlockState state, Level worldIn, BlockPos pos) {
        if (worldIn instanceof ServerLevel serverLevel && worldIn.getBlockEntity(pos) instanceof TileEntityJoy joy) {
            Entity oldSitEntity = serverLevel.getEntity(joy.getSitId());
            if (oldSitEntity != null && oldSitEntity.isAlive()) {
                return;
            }
            Vec3 sitPos = new Vec3(pos.getX() + this.sitPosition().x, pos.getY() + this.sitPosition().y,
                    pos.getZ() + this.sitPosition().z);
            EntitySit newSitEntity = new EntitySit(worldIn, sitPos, this.getTypeName(), pos);
            newSitEntity.setYRot(state.getValue(FACING).getOpposite().toYRot() + this.sitYRot());
            worldIn.addFreshEntity(newSitEntity);
            joy.setSitId(newSitEntity.getUUID());
            joy.setChanged();
            maid.startRiding(newSitEntity);
        }
    }

    /**
     * 女仆坐到方块<b>旁边</b>（而不是正中央），三张棋盘共用。
     *
     * <p>棋盘类方块不能用 {@link #sitPosition()}：那个偏移是相对方块原点的常量，
     * 而棋盘要让女仆坐在「棋盘对面」并面朝棋盘，位置得随 {@code FACING} 变。
     * 上游是把这段代码在三块棋盘里各抄了一遍（{@code BlockCChess} / {@code BlockWChess}
     * 用 {@code FACING.getOpposite()} 配 2 格，{@code BlockGomoku} 用 {@code getClockWise()} 配 1.5 格），
     * 这里收成一个方法，省得三处里的某一处悄悄改错。</p>
     *
     * @param face     从棋盘中心指向女仆的方向
     * @param distance 女仆离方块中心几个格
     */
    protected final void sitMaidAt(EntityMaid maid, BlockState state, Level level, BlockPos pos,
                                   Direction face, double distance) {
        if (!(level instanceof ServerLevel serverLevel) || !(level.getBlockEntity(pos) instanceof TileEntityJoy joy)) {
            return;
        }
        Entity oldSitEntity = serverLevel.getEntity(joy.getSitId());
        if (oldSitEntity != null && oldSitEntity.isAlive()) {
            return;
        }
        Vec3 sitPos = new Vec3(pos.getX() + 0.5 + face.getStepX() * distance,
                pos.getY() + 0.1,
                pos.getZ() + 0.5 + face.getStepZ() * distance);
        EntitySit newSitEntity = new EntitySit(level, sitPos, this.getTypeName(), pos);
        newSitEntity.setYRot(face.getOpposite().toYRot() + this.sitYRot());
        level.addFreshEntity(newSitEntity);
        joy.setSitId(newSitEntity.getUUID());
        joy.setChanged();
        maid.startRiding(newSitEntity);
    }

    /**
     * 方块被移除时顺手丢掉座位实体。
     *
     * <p>不丢的话座位会留在原地十 tick 之后由 {@code EntitySit} 自己销毁 —— 结果是玩家在空中坐着，
     * 而且这张棋盘再也没法被坐（{@code SitId} 指向一个已死实体，{}@code startMaidSit} 会拒绝）。</p>
     */
    @Override
    public void onRemove(BlockState state, Level worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
        BlockEntity blockEntity = worldIn.getBlockEntity(pos);
        if (blockEntity instanceof TileEntityJoy joy && worldIn instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(joy.getSitId());
            if (entity instanceof EntitySit) {
                entity.discard();
            }
        }
        super.onRemove(state, worldIn, pos, newState, isMoving);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    // 1.20.1 - true
    // 1.18.2 - false
    @Override
    public boolean isPathfindable(BlockState state, BlockGetter worldIn, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }
}

