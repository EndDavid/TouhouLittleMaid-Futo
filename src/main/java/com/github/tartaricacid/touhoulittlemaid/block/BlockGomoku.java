package com.github.tartaricacid.touhoulittlemaid.block;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.api.block.IBoardGameBlock;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Point;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Statue;
import com.github.tartaricacid.touhoulittlemaid.block.properties.GomokuPart;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidGomokuAI;
import com.github.tartaricacid.touhoulittlemaid.entity.favorability.Type;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.github.tartaricacid.touhoulittlemaid.item.ItemHakureiGohei;
import com.github.tartaricacid.touhoulittlemaid.network.GomokuToClientPacket;
import com.github.tartaricacid.touhoulittlemaid.network.MaidParticlePacket;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGomoku;
import com.github.tartaricacid.touhoulittlemaid.util.GomokuUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 五子棋棋盘，移植自上游 1.20.1 的 {@code block/BlockGomoku}。
 *
 * <p>3×3 多方块，只有中心格带方块实体。玩家执黑先手，女仆执白，
 * 每落一子就把整个 15×15 棋盘发给玩家的客户端，让他在后台线程里跑
 * {@code ZhiZhangAIService} 算应手（见 {@link GomokuToClientPacket}）。</p>
 *
 * <h2>坐标怎么算</h2>
 * <p>上游给九宫格每一格各写了一套 {@code getData(x, y, 三个偏移…)}，彼此之间并不自洽，
 * 而且判定框只有半格宽、还从格线往一侧偏 —— 表现就是「棋子画在线上、能点的却是线右侧」。
 * 这里统一走 {@link GomokuUtil#getGridPoint}：把点击点换到盘面坐标后四舍五入到最近的交叉点，
 * <b>渲染器用的是同一份格线</b>，两边不可能再错开。细节与实测数据见 {@link GomokuUtil}。</p>
 *
 * <h2>与上游的差异</h2>
 * <ol>
 *     <li><b>没有残局道具</b>（{@code InitItems.GOMOKU_BOARD_STATE} / {@code GomokuCodec} /
 *         木棍导出）。这套东西三张棋盘共用，本移植版三块都还没做，记在 README 的待办里。</li>
 *     <li><b>御币（创造模式）铺满棋盘</b>保留了 —— 它只用得到 {@code clickWithDebug()}，
 *         不需要残局编解码，是调 AI 时最省事的一条路径。</li>
 *     <li><b>没有 {@code onBlockExploded}</b>，统一在 {@link #onRemove} 里清桌子。</li>
 * </ol>
 */
public class BlockGomoku extends BlockJoy implements IBoardGameBlock {
    public static final EnumProperty<GomokuPart> PART = EnumProperty.create("part", GomokuPart.class);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final VoxelShape LEFT_UP = Block.box(8, 0, 8, 16, 2, 16);
    public static final VoxelShape LEFT_UP_WITH_BOX = Shapes.or(LEFT_UP, Block.box(11, 0, 2, 16, 4, 7));
    public static final VoxelShape UP = Block.box(0, 0, 8, 16, 2, 16);
    public static final VoxelShape RIGHT_UP = Block.box(0, 0, 8, 8, 2, 16);
    public static final VoxelShape RIGHT_UP_WITH_BOX = Shapes.or(RIGHT_UP, Block.box(9, 0, 11, 14, 4, 16));
    public static final VoxelShape LEFT_CENTER = Block.box(8, 0, 0, 16, 2, 16);
    public static final VoxelShape CENTER = Block.box(0, 0, 0, 16, 2, 16);
    public static final VoxelShape RIGHT_CENTER = Block.box(0, 0, 0, 8, 2, 16);
    public static final VoxelShape LEFT_DOWN = Block.box(8, 0, 0, 16, 2, 8);
    public static final VoxelShape LEFT_DOWN_WITH_BOX = Shapes.or(LEFT_DOWN, Block.box(2, 0, 0, 7, 4, 5));
    public static final VoxelShape DOWN = Block.box(0, 0, 0, 16, 2, 8);
    public static final VoxelShape RIGHT_DOWN = Block.box(0, 0, 0, 8, 2, 8);
    public static final VoxelShape RIGHT_DOWN_WITH_BOX = Shapes.or(RIGHT_DOWN, Block.box(0, 0, 9, 5, 4, 14));

    public BlockGomoku() {
        super(Properties.of(Material.WOOD).sound(SoundType.WOOD).strength(2.0F, 3.0F).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(PART, GomokuPart.CENTER).setValue(FACING, Direction.NORTH));
    }

    private void handleGomokuRemove(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) {
            return;
        }
        GomokuPart part = state.getValue(PART);
        BlockPos centerPos = pos.subtract(new Vec3i(part.getPosX(), 0, part.getPosY()));
        if (level.getBlockEntity(centerPos) instanceof TileEntityGomoku gomoku && gomoku.claimPieceDrop()) {
            popResource(level, centerPos, new ItemStack(InitItems.GOMOKU));
        }
        for (int i = -1; i < 2; i++) {
            for (int j = -1; j < 2; j++) {
                BlockPos target = centerPos.offset(i, 0, j);
                if (level.getBlockState(target).is(this)) {
                    level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    /**
     * 点击的是不是棋盘两端那两堆棋子（棋盒）—— 点它就是重开。
     *
     * <p>棋盒在模型里位于对角线上，而棋子盘面是按世界坐标算的（这里不像中国象棋那样
     * 把点击向量按朝向旋转，见 {@link #use}），所以哪两个格子里有棋盒取决于棋盘的朝向：
     * 朝 Z 轴时是右下 / 左上，朝 X 轴时是右上 / 左下。</p>
     */
    private static boolean isClickChessBox(double x, double z, GomokuPart part, Direction direction) {
        if (direction.getAxis() == Direction.Axis.Z) {
            if (part == GomokuPart.RIGHT_UP) {
                return 0.5625 <= x && x <= 0.875 && 0.6875 <= z && z <= 1;
            }
            if (part == GomokuPart.LEFT_DOWN) {
                return 0.125 <= x && x <= 0.4375 && 0 <= z && z <= 0.3125;
            }
        }
        if (direction.getAxis() == Direction.Axis.X) {
            if (part == GomokuPart.LEFT_UP) {
                return 0.6875 <= x && x <= 1 && 0.125 <= z && z <= 0.4375;
            }
            if (part == GomokuPart.RIGHT_DOWN) {
                return 0 <= x && x <= 0.3125 && 0.5625 <= z && z <= 0.875;
            }
        }
        return false;
    }

    @Override
    protected Vec3 sitPosition() {
        return Vec3.ZERO;
    }

    @Override
    protected String getTypeName() {
        return Type.GOMOKU.getTypeName();
    }

    @Override
    protected int sitYRot() {
        return 0;
    }

    @Override
    public void playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        handleGomokuRemove(world, pos, state);
        super.playerWillDestroy(world, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving && !newState.is(state.getBlock())) {
            handleGomokuRemove(world, pos, state);
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos centerPos = context.getClickedPos();
        for (int i = -1; i < 2; i++) {
            for (int j = -1; j < 2; j++) {
                BlockPos searchPos = centerPos.offset(i, 0, j);
                if (!context.getLevel().getBlockState(searchPos).canBeReplaced(context)) {
                    return null;
                }
            }
        }
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level worldIn, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(worldIn, pos, state, placer, stack);
        if (worldIn.isClientSide) {
            return;
        }
        for (int i = -1; i < 2; i++) {
            for (int j = -1; j < 2; j++) {
                BlockPos searchPos = pos.offset(i, 0, j);
                GomokuPart part = GomokuPart.getPartByPos(i, j);
                if (part != null && !part.isCenter()) {
                    worldIn.setBlock(searchPos, state.setValue(PART, part), Block.UPDATE_ALL);
                }
            }
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level instanceof ServerLevel serverLevel) || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        GomokuPart part = state.getValue(PART);
        BlockPos centerPos = pos.subtract(new Vec3i(part.getPosX(), 0, part.getPosY()));
        if (!(level.getBlockEntity(centerPos) instanceof TileEntityGomoku gomoku)) {
            return InteractionResult.FAIL;
        }

        Vec3 location = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        Direction facing = state.getValue(FACING);

        // 创造模式下拿御币点一下 = 铺满棋盘只留三个空位（调试 AI 用）
        Item held = player.getMainHandItem().getItem();
        if (player.getAbilities().instabuild && held instanceof ItemHakureiGohei) {
            gomoku.clickWithDebug();
            gomoku.refresh();
            level.playSound(null, centerPos, InitSounds.GOMOKU_RESET, SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }

        // 下棋必须空手
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }

        // 点棋盒 = 重开
        if (isClickChessBox(location.x, location.z, part, facing)) {
            level.playSound(null, centerPos, InitSounds.GOMOKU_RESET, SoundSource.BLOCKS, 1.0f, 1.0f);
            gomoku.reset();
            gomoku.refresh();

            Entity sitEntity = serverLevel.getEntity(gomoku.getSitId());
            if (sitEntity != null && sitEntity.isAlive() && sitEntity.getFirstPassenger() instanceof EntityMaid maid) {
                maid.getGameRecordManager().resetStatue();
            }
            return InteractionResult.SUCCESS;
        }

        Entity sitEntity = serverLevel.getEntity(gomoku.getSitId());
        if (sitEntity == null || !sitEntity.isAlive() || !(sitEntity.getFirstPassenger() instanceof EntityMaid maid)) {
            player.displayClientMessage(new TranslatableComponent("message.touhou_little_maid.gomoku.no_maid"), false);
            return InteractionResult.FAIL;
        }
        // 和其他人的女仆下棋可以，但不给好感度（下面那处判断）
        if (MaidConfig.MAID_GOMOKU_OWNER_LIMIT.get() && !maid.isOwnedBy(player)) {
            player.displayClientMessage(new TranslatableComponent("message.touhou_little_maid.gomoku.not_owner"), false);
            return InteractionResult.FAIL;
        }
        if (!gomoku.isPlayerTurn()) {
            return InteractionResult.FAIL;
        }

        int[] grid = GomokuUtil.getGridPoint(part, location.x, location.z);
        if (grid == null) {
            return InteractionResult.FAIL;
        }
        int[][] chessData = gomoku.getChessData();
        Point playerPoint = new Point(grid[0], grid[1], Point.BLACK);
        if (gomoku.getStatue() != Statue.IN_PROGRESS || chessData[playerPoint.x][playerPoint.y] != Point.EMPTY) {
            return InteractionResult.FAIL;
        }

        gomoku.setChessData(playerPoint.x, playerPoint.y, playerPoint.type);
        Statue statue = MaidGomokuAI.getStatue(chessData, playerPoint);
        // 但是和其他人的女仆对弈不加好感哦
        if (statue == Statue.WIN && maid.isOwnedBy(player)) {
            maid.getFavorabilityManager().apply(Type.GOMOKU_WIN);
            maid.getGameRecordManager().markStatue(false);
            int rankBefore = MaidGomokuAI.getRank(maid);
            maid.getGameRecordManager().increaseGomokuWinCount();
            int rankAfter = MaidGomokuAI.getRank(maid);
            // 女仆升段啦
            if (rankBefore < rankAfter) {
                // 上游是「只发给下棋的玩家」，本移植版的粒子包发给 16 格内所有玩家
                MaidParticlePacket.sendNearby(maid, MaidParticlePacket.Type.RANK_UP);
            }
            if (player instanceof ServerPlayer serverPlayer) {
                InitTrigger.MAID_EVENT.trigger(serverPlayer, TriggerType.WIN_GOMOKU);
            }
        }
        gomoku.setStatue(statue);
        level.playSound(null, pos, InitSounds.GOMOKU, SoundSource.BLOCKS,
                1.0f, 0.8F + level.random.nextFloat() * 0.4F);
        if (gomoku.getStatue() == Statue.IN_PROGRESS) {
            gomoku.setPlayerTurn(false);
            if (player instanceof ServerPlayer serverPlayer) {
                GomokuToClientPacket.sendToPlayer(serverPlayer, centerPos, chessData, playerPoint,
                        maid.getGameRecordManager().getGomokuWinCount());
            }
        }
        gomoku.refresh();
        return InteractionResult.SUCCESS;
    }

    @Override
    public void startMaidSit(EntityMaid maid, BlockState state, Level worldIn, BlockPos pos) {
        // 五子棋的盘面是 2×2 格，女仆坐在侧边一格半处刚好面对棋盘。
        //
        // 方向不能用 getClockWise()（上游是那么写的）：两个棋盒在对角线上，
        // 「顺时针一格」和「白盒在哪一侧」没有关系，四个朝向全都会坐到黑盒那边，
        // 而女仆执白。推导与实测见 GomokuUtil#seatDirection 与 port/gomoku_seat_probe.py。
        this.sitMaidAt(maid, state, worldIn, pos, GomokuUtil.seatDirection(state.getValue(FACING)), 1.5D);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(PART).isCenter()) {
            return new TileEntityGomoku(pos, state);
        }
        return null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        // 棋盒那两格要加高一点，否则玩家会站进模型里
        switch (state.getValue(PART)) {
            case LEFT_UP:
                return state.getValue(FACING).getAxis() == Direction.Axis.X ? LEFT_UP_WITH_BOX : LEFT_UP;
            case UP:
                return UP;
            case RIGHT_UP:
                return state.getValue(FACING).getAxis() == Direction.Axis.Z ? RIGHT_UP_WITH_BOX : RIGHT_UP;
            case LEFT_CENTER:
                return LEFT_CENTER;
            case RIGHT_CENTER:
                return RIGHT_CENTER;
            case LEFT_DOWN:
                return state.getValue(FACING).getAxis() == Direction.Axis.Z ? LEFT_DOWN_WITH_BOX : LEFT_DOWN;
            case DOWN:
                return DOWN;
            case RIGHT_DOWN:
                return state.getValue(FACING).getAxis() == Direction.Axis.X ? RIGHT_DOWN_WITH_BOX : RIGHT_DOWN;
            default:
                return CENTER;
        }
    }
}

