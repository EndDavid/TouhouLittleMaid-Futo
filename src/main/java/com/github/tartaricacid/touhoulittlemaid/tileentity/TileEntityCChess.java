package com.github.tartaricacid.touhoulittlemaid.tileentity;

import com.github.tartaricacid.touhoulittlemaid.api.block.IBoardGameEntityBlock;
import com.github.tartaricacid.touhoulittlemaid.api.game.xqwlight.Position;
import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.github.tartaricacid.touhoulittlemaid.util.CChessUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中国象棋棋盘，移植自上游 {@code tileentity/TileEntityCChess}。
 *
 * <p>整个棋局就是一个 FEN 字符串，其余全是「界面状态」：当前选中的棋子、回合数、
 * 以及三种终局标记（将死 / 长打 / 自然限着）。把整盘棋压成 FEN 的好处是存档和网络包都只有一个字段，
 * 而且 {@code Position#fromFen} 是引擎自己的入口，来回转换不会有信息损失。</p>
 *
 * <h2>为什么棋局不在同步数据里</h2>
 * <p>棋盘每一格都要重画，用 {@code SynchedEntityData} 得把 90 个字节塞进一个 accessor，
 * 还要自己造序列化器。这里走的是方块实体自己的更新包（{@link TileEntityJoy#refresh()}），
 * 只在真的走了一步时发一次，代价更小。</p>
 */
public class TileEntityCChess extends TileEntityJoy implements IBoardGameEntityBlock {
    private static final String CHESS_DATA = "ChessData";
    private static final String CHESS_COUNTER = "ChessCounter";
    private static final String SELECT_CHESS_POINT = "SelectChessPoint";
    private static final String CHECKMATE = "Checkmate";
    private static final String REPEAT = "Repeat";
    private static final String MOVE_NUMBER_LIMIT = "MoveNumberLimit";

    private final Position chessData;

    // 回合计数器（用于「六十回合自然限着」的显示）
    private int chessCounter = 0;
    // 当前选中的棋子（引擎坐标，非中心点时是 -1 之前的 0，见 BlockCChess#use）
    private int selectChessPoint = 0;
    // 将死（依据下棋方判断谁输谁赢）
    private boolean checkmate = false;
    // 长打（判和）
    private boolean repeat = false;
    // 六十回合自然限着（判和）
    private boolean moveNumberLimit = false;
    /**
     * 「棋盘物品已经掉过了」的一次性闸门。
     *
     * <p>拆掉任意一格都会把整张桌子清空，而清空的 9 次 {@code setBlock} 会各自触发一次
     * {@code onRemove} —— 不加这个闸门，一张棋盘能掉出好几个物品。</p>
     */
    private boolean pieceDropped = false;

    public TileEntityCChess(BlockPos pos, BlockState blockState) {
        super(InitBlocks.CCHESS_TE, pos, blockState);
        this.chessData = new Position();
        this.chessData.fromFen(CChessUtil.INIT);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        tag.putString(CHESS_DATA, chessData.toFen());
        tag.putInt(CHESS_COUNTER, chessCounter);
        tag.putInt(SELECT_CHESS_POINT, selectChessPoint);
        tag.putBoolean(CHECKMATE, checkmate);
        tag.putBoolean(REPEAT, repeat);
        tag.putBoolean(MOVE_NUMBER_LIMIT, moveNumberLimit);
        super.saveAdditional(tag);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        chessCounter = nbt.getInt(CHESS_COUNTER);
        selectChessPoint = nbt.getInt(SELECT_CHESS_POINT);
        // 空字符串会让引擎抛异常（fromFen 会 split 失败），而这段代码跑在区块加载里，
        // 一张被 /setblock 造出来的空棋盘就足以让整个区块加载报错
        String fen = nbt.getString(CHESS_DATA);
        chessData.fromFen(fen.isEmpty() ? CChessUtil.INIT : fen);
        checkmate = nbt.getBoolean(CHECKMATE);
        repeat = nbt.getBoolean(REPEAT);
        moveNumberLimit = nbt.getBoolean(MOVE_NUMBER_LIMIT);
    }

    public void reset() {
        this.chessCounter = 0;
        this.selectChessPoint = 0;
        this.chessData.fromFen(CChessUtil.INIT);
        this.checkmate = false;
        this.repeat = false;
        this.moveNumberLimit = false;
    }

    /**
     * 取走「掉落棋盘物品」的许可；只有第一次调用返回 {@code true}。
     */
    public boolean claimPieceDrop() {
        if (this.pieceDropped) {
            return false;
        }
        this.pieceDropped = true;
        return true;
    }

    public Position getChessData() {
        return chessData;
    }

    public boolean isCheckmate() {
        return checkmate;
    }

    public void setCheckmate(boolean checkmate) {
        this.checkmate = checkmate;
    }

    public boolean isPlayerTurn() {
        return CChessUtil.isPlayer(this.chessData);
    }

    public int getChessCounter() {
        return chessCounter;
    }

    public void addChessCounter() {
        this.chessCounter += 1;
    }

    public int getSelectChessPoint() {
        return selectChessPoint;
    }

    public void setSelectChessPoint(int selectChessPoint) {
        this.selectChessPoint = selectChessPoint;
    }

    public boolean isRepeat() {
        return repeat;
    }

    public void setRepeat(boolean repeat) {
        this.repeat = repeat;
    }

    public boolean isMoveNumberLimit() {
        return moveNumberLimit;
    }

    public void setMoveNumberLimit(boolean moveNumberLimit) {
        this.moveNumberLimit = moveNumberLimit;
    }
}
