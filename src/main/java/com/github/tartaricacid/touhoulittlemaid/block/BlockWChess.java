package com.github.tartaricacid.touhoulittlemaid.block;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.api.block.IBoardGameBlock;
import com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position;
import com.github.tartaricacid.touhoulittlemaid.block.properties.GomokuPart;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.favorability.Type;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.github.tartaricacid.touhoulittlemaid.network.WChessToClientPacket;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityJoy;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityWChess;
import com.github.tartaricacid.touhoulittlemaid.util.WChessUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 国际象棋棋盘，移植自上游 {@code block/BlockWChess}。
 *
 * <p>它和中国象棋那块（{@link BlockCChess}）是<b>同一套骨架、不同的引擎</b>：
 * 3×3 多方块、只有中心格带方块实体、空手右击选子再落子、侧面浅色带重开、
 * 走完一步把 FEN 交给玩家客户端去搜索女仆的应手。差别只在引擎的编码方式：
 * 棋盘坐标在国际象棋里是 {@code 0x88} 的 4..11 / 0..7（见 {@code Position}），
 * 棋子编码是 {@code 8 + 类型} 与 {@code 16 + 类型}。</p>
 *
 * <h2>与上游的差异</h2>
 * <ol>
 *     <li><b>没有 {@code onBlockExploded}。</b>那是 Forge 钩子。这里统一放进 {@link #onRemove}，
 *         配合 {@link TileEntityWChess#claimPieceDrop()} 的一次性闸门，
 *         拆任意一格也只掉一个棋盘（理由同 {@link BlockCChess}）。</li>
 *     <li><b>提示走 {@code displayClientMessage}。</b>1.18.2 的
 *         {@code Player#sendMessage(Component, UUID)} 已被移除。</li>
 * </ol>
 */
public class BlockWChess extends BlockJoy implements IBoardGameBlock {
    public static final EnumProperty<GomokuPart> PART = EnumProperty.create("part", GomokuPart.class);
    public static final VoxelShape AABB = Block.box(0, 0, 0, 16, 2, 16);

    public BlockWChess() {
        super(Properties.of(Material.WOOD).sound(SoundType.WOOD).strength(2.0F, 3.0F).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(PART, GomokuPart.CENTER).setValue(FACING, Direction.NORTH));
    }

    /**
     * 拆掉任意一格时把整张桌子清空，并（只）掉出一个棋盘物品。
     */
    private void handleWChessRemove(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) {
            return;
        }
        GomokuPart part = state.getValue(PART);
        BlockPos centerPos = pos.subtract(new Vec3i(part.getPosX(), 0, part.getPosY()));
        if (level.getBlockEntity(centerPos) instanceof TileEntityWChess chess && chess.claimPieceDrop()) {
            popResource(level, centerPos, new ItemStack(InitItems.WCHESS));
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
     * 服务端执行女仆（客户端算出来的）一步棋。
     *
     * @param move       引擎的走法编码（{@code Position.MOVE(from, to)}）
     * @param maidLost   客户端认为女仆已经被将死
     * @param playerLost 客户端认为玩家已经被将死
     */
    public static void maidMove(ServerPlayer player, Level level, BlockPos pos, int move, boolean maidLost, boolean playerLost) {
        if (level.getBlockEntity(pos) instanceof TileEntityWChess chess) {
            if (chess.isPlayerTurn()) {
                // 不是女仆的回合 —— 伪造 / 迟到 的包，丢掉
                return;
            }

            Position chessData = chess.getChessData();
            UUID sitId = chess.getSitId();
            // 女仆输：防止作弊，再检查一次
            if (maidLost && WChessUtil.isMaid(chessData) && chessData.isMate()) {
                chess.setCheckmate(true);
                chess.refresh();

                if (level instanceof ServerLevel serverLevel && serverLevel.getEntity(sitId) instanceof EntitySit sit
                        && sit.getFirstPassenger() instanceof EntityMaid maid && maid.isOwnedBy(player)) {
                    // TODO: 暂时不加段位系统（上游同款）
                    maid.getFavorabilityManager().apply(Type.WCHESS_WIN);
                    maid.getGameRecordManager().markStatue(false);
                    InitTrigger.MAID_EVENT.trigger(player, TriggerType.WIN_WCHESS);
                }
                return;
            }

            // 如果吃子 / 动兵了，那么重置计数器（该计数器用于判断五十回合自然限着和长打）
            if (chessData.makeMove(move)) {
                int src = Position.SRC(move);
                if (chessData.captured() || Position.PIECE_TYPE(chessData.squares[src]) == Position.PIECE_PAWN) {
                    chessData.setIrrev();
                }
            }
            chess.setSelectChessPoint(Position.DST(move));
            chess.setCheckmate(playerLost);

            // 如果玩家没输，那么检查其他和局情况
            if (!playerLost) {
                if (WChessUtil.reachMoveLimit(chessData)) {
                    chess.setMoveNumberLimit(true);
                } else if (WChessUtil.isRepeat(chessData)) {
                    chess.setRepeat(true);
                }
            }

            if (level instanceof ServerLevel serverLevel && serverLevel.getEntity(sitId) instanceof EntitySit sit
                    && sit.getFirstPassenger() instanceof EntityMaid maid) {
                maid.swing(InteractionHand.MAIN_HAND);
                if (playerLost) {
                    maid.getGameRecordManager().markStatue(true);
                }
            }
            level.playSound(null, pos, InitSounds.GOMOKU, SoundSource.BLOCKS,
                    1.0f, 0.8F + level.random.nextFloat() * 0.4F);
            chess.refresh();
        }
    }

    @Override
    public void startMaidSit(EntityMaid maid, BlockState state, Level worldIn, BlockPos pos) {
        // 女仆坐在棋盘对面两格处，这样她才能真正「面朝」棋盘
        this.sitMaidAt(maid, state, worldIn, pos, state.getValue(FACING).getOpposite(), 2.0D);
    }

    @Override
    public void playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        handleWChessRemove(world, pos, state);
        super.playerWillDestroy(world, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        // 只在「这个方块真的消失了」时处理（setPlacedBy 铺周围八格时 newState 仍是同一个方块）
        if (!isMoving && !newState.is(state.getBlock())) {
            handleWChessRemove(world, pos, state);
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
        if (!(level instanceof ServerLevel serverLevel) || hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        GomokuPart part = state.getValue(PART);
        BlockPos centerPos = pos.subtract(new Vec3i(part.getPosX(), 0, part.getPosY()));

        if (!(level.getBlockEntity(centerPos) instanceof TileEntityWChess chess)) {
            return InteractionResult.FAIL;
        }
        if (!chess.isPlayerTurn() && !chess.isCheckmate()) {
            return InteractionResult.FAIL;
        }

        // 检查女仆
        Entity sitEntity = serverLevel.getEntity(chess.getSitId());
        if (sitEntity == null || !sitEntity.isAlive() || !(sitEntity.getFirstPassenger() instanceof EntityMaid maid)) {
            player.displayClientMessage(new TranslatableComponent("message.touhou_little_maid.gomoku.no_maid"), false);
            return InteractionResult.FAIL;
        }
        // 检查是不是自己的女仆
        if (MaidConfig.MAID_GOMOKU_OWNER_LIMIT.get() && !maid.isOwnedBy(player)) {
            player.displayClientMessage(new TranslatableComponent("message.touhou_little_maid.gomoku.not_owner"), false);
            return InteractionResult.FAIL;
        }

        // 点击坐标的转换：把 3×3 的哪一格 + 格内偏移合成为以中心为原点的坐标
        Direction facing = state.getValue(FACING);
        Vec3 clickPos = hit.getLocation()
                .subtract(pos.getX(), pos.getY(), pos.getZ())
                .add(part.getPosX() - 0.5, 0, part.getPosY() - 0.5)
                .yRot(facing.toYRot() * Mth.DEG_TO_RAD);

        // 重置棋盘（棋盘两侧的浅色块）
        if (WChessUtil.isClickResetArea(clickPos)) {
            chess.reset();
            chess.refresh();
            level.playSound(null, centerPos, InitSounds.GOMOKU_RESET, SoundSource.BLOCKS, 1.0f, 1.0f);
            maid.getGameRecordManager().resetStatue();
        }

        // 没有点击到棋盘上，返回
        int nowClick = WChessUtil.getClickPosition(clickPos);
        if (nowClick < 0 || !Position.IN_BOARD(nowClick)) {
            return InteractionResult.PASS;
        }

        // 玩家已经输了，不能下棋
        if (chess.isCheckmate() && chess.isPlayerTurn()) {
            return InteractionResult.FAIL;
        }

        // 五十回合自然限着、长打不能下棋
        if (chess.isMoveNumberLimit() || chess.isRepeat()) {
            return InteractionResult.FAIL;
        }

        // 处理点击棋子的逻辑
        Position chessData = chess.getChessData();
        byte[] squares = chessData.squares;
        int preClick = chess.getSelectChessPoint();
        if (preClick < 0 || squares.length <= preClick) {
            preClick = 0;
        }
        byte prePiece = squares[preClick];
        byte nowPiece = squares[nowClick];

        // 如果前一个选择为空，或者选中的是黑方，说明没有选中棋子
        if (prePiece <= 0 || WChessUtil.isBlack(prePiece)) {
            // 当前点击的是白方棋子
            if (WChessUtil.isWhite(nowPiece)) {
                chess.setSelectChessPoint(nowClick);
                chess.refresh();
                level.playSound(null, pos, InitSounds.GOMOKU, SoundSource.BLOCKS,
                        1.0f, 0.8F + level.random.nextFloat() * 0.4F);
            }
            return InteractionResult.SUCCESS;
        }

        // 如果选的都是白方棋子，重选
        if (WChessUtil.isWhite(prePiece) && WChessUtil.isWhite(nowPiece)) {
            chess.setSelectChessPoint(nowClick);
            chess.refresh();
            level.playSound(null, pos, InitSounds.GOMOKU, SoundSource.BLOCKS,
                    1.0f, 0.8F + level.random.nextFloat() * 0.4F);
            return InteractionResult.SUCCESS;
        }

        // 判断移动是否合法
        int move = Position.MOVE(preClick, nowClick);
        if (!chessData.legalMove(move)) {
            return InteractionResult.FAIL;
        }

        // 没有将军，正常移动
        boolean notChecked = chessData.makeMove(move);
        if (notChecked) {
            int src = Position.SRC(move);
            // 如果吃子、动兵了，那么重置计数器
            if (chessData.captured() || Position.PIECE_TYPE(chessData.squares[src]) == Position.PIECE_PAWN) {
                chessData.setIrrev();
            }
            chess.addChessCounter();
            chess.setSelectChessPoint(nowClick);
            chess.refresh();
            level.playSound(null, pos, InitSounds.GOMOKU, SoundSource.BLOCKS,
                    1.0f, 0.8F + level.random.nextFloat() * 0.4F);
            // 一步棋下完就该女仆思考了：把局面交给客户端
            if (player instanceof ServerPlayer serverPlayer) {
                WChessToClientPacket.sendToPlayer(serverPlayer, centerPos, chessData.toFen());
            }
            return InteractionResult.SUCCESS;
        }

        // 如果将军，那么给予提示
        player.displayClientMessage(new TranslatableComponent("message.touhou_little_maid.cchess.check"), false);
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL, SoundSource.BLOCKS,
                1.0f, 0.8F + level.random.nextFloat() * 0.4F);
        return InteractionResult.FAIL;
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(PART).isCenter()) {
            return new TileEntityWChess(pos, state);
        }
        return null;
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return AABB;
    }
}

