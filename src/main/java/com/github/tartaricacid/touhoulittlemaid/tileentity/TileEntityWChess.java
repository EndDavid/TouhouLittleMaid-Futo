package com.github.tartaricacid.touhoulittlemaid.tileentity;

import com.github.tartaricacid.touhoulittlemaid.api.block.IBoardGameEntityBlock;
import com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position;
import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.github.tartaricacid.touhoulittlemaid.util.WChessUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 国际象棋棋盘，移植自上游 {@code tileentity/TileEntityWChess}。
 *
 * <p>结构与中国象棋那块完全一致（见 {@link TileEntityCChess}）：整盘棋就是一个 FEN 字符串，
 * 其余字段全是界面状态。差别只有两点：引擎换成 {@code api/game/chess}
 * （走法编码、棋子编码都不同，见 {@link WChessUtil}），以及「五十回合自然限着」的计数器
 * 除了吃子还要在<b>动兵</b>时清零 —— 这是国际象棋的规则，中国象棋那边中国象棋的规则。</p>
 */
public class TileEntityWChess extends TileEntityJoy implements IBoardGameEntityBlock {
    private static final String CHESS_DATA = "ChessData";
    private static final String CHESS_COUNTER = "ChessCounter";
    private static final String SELECT_CHESS_POINT = "SelectChessPoint";
    private static final String CHECKMATE = "Checkmate";
    private static final String REPEAT = "Repeat";
    private static final String MOVE_NUMBER_LIMIT = "MoveNumberLimit";

    private final Position chessData;

    private int chessCounter = 0;
    private int selectChessPoint = 0;
    private boolean checkmate = false;
    private boolean repeat = false;
    private boolean moveNumberLimit = false;
    /**
     * 「棋盘物品已经掉过了」的一次性闸门，理由同 {@link TileEntityCChess#claimPieceDrop()}。
     */
    private boolean pieceDropped = false;

    public TileEntityWChess(BlockPos pos, BlockState blockState) {
        super(InitBlocks.WCHESS_TE, pos, blockState);
        this.chessData = new Position();
        this.chessData.fromFen(WChessUtil.INIT);
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
        // 空字符串会让引擎抛异常（fromFen 直接 split），而这段代码跑在区块加载里，
        // 一张被 /setblock 造出来的空棋盘就足以让整个区块加载报错。同 TileEntityCChess。
        String fen = nbt.getString(CHESS_DATA);
        chessData.fromFen(fen.isEmpty() ? WChessUtil.INIT : fen);
        checkmate = nbt.getBoolean(CHECKMATE);
        repeat = nbt.getBoolean(REPEAT);
        moveNumberLimit = nbt.getBoolean(MOVE_NUMBER_LIMIT);
    }

    /**
     * 载入一个残局（FEN），对应上游 {@code TileEntityWChess} 对应方法。
     * 终局标志一并清掉的理由同 {@code TileEntityCChess#setEndgame}。
     */
    public void setEndgame(String fen) {
        this.chessCounter = 0;
        this.selectChessPoint = 0;
        this.chessData.fromFen(fen);
        this.checkmate = false;
        this.repeat = false;
        this.moveNumberLimit = false;
        this.refresh();
    }

    public void reset() {
        this.chessCounter = 0;
        this.selectChessPoint = 0;
        this.chessData.fromFen(WChessUtil.INIT);
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
        return WChessUtil.isPlayer(this.chessData);
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

