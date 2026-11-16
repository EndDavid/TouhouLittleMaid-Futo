package com.github.tartaricacid.touhoulittlemaid.tileentity;

import com.github.tartaricacid.touhoulittlemaid.api.block.IBoardGameEntityBlock;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.GomokuCodec;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Point;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Statue;
import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.github.tartaricacid.touhoulittlemaid.util.GomokuUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 五子棋棋盘，移植自上游 1.20.1 的 {@code tileentity/TileEntityGomoku}。
 *
 * <h2>为什么这一块存的不是 FEN</h2>
 * <p>中国象棋 / 国际象棋把整盘棋压成一个 FEN 字符串，因为它们有现成的
 * {@code Position#toFen} / {@code fromFen}。五子棋的引擎（{@code ZhiZhangAIService}）
 * 只吃一个 {@code int[15][15]}（0 空 / 1 黑 / 2 白），没有序列化入口，
 * 所以直接存成 15 条 {@code IntArrayTag} —— 一个字段读回来就是棋盘本身。</p>
 *
 * <h2>与上游的两处加固</h2>
 * <ol>
 *     <li><b>读档时校验行列数。</b>上游直接 {@code chessData[i] = intArray}：
 *         存档里若因为任何原因多出一行（或某行长度不是 15），之后第一次落子就会
 *         {@code ArrayIndexOutOfBounds}，而这段代码跑在区块加载里 —— 一个坏存档能被放大成
 *         「整个世界加载不出来」。这里多出来的行直接丢掉、短行补零。</li>
 *     <li><b>没有 {@code getPersistentData()}。</b>那是 Forge 提供的懒创建子标签，
 *         1.18.2 没有；{@code saveAdditional} 收到的 {@code tag} 本来就是方块实体自己的标签。</li>
 * </ol>
 */
public class TileEntityGomoku extends TileEntityJoy implements IBoardGameEntityBlock {
    private static final String CHESS_DATA = "ChessData";
    private static final String STATUE = "Statue";
    private static final String PLAYER_TURN = "PlayerTurn";
    private static final String CHESS_COUNTER = "ChessCounter";
    private static final String LATEST_CHESS_POINT = "LatestChessPoint";

    private int[][] chessData = new int[GomokuUtil.GRID_SIZE][GomokuUtil.GRID_SIZE];
    private int statue = Statue.IN_PROGRESS.ordinal();
    private boolean playerTurn = true;
    private int chessCounter = 0;
    private Point latestChessPoint = Point.NULL;

    /**
     * 「棋盘物品已经掉过了」的一次性闸门。
     *
     * <p>拆掉任意一格都会把整张桌子清空，而清空的 9 次 {@code setBlock} 会各自触发一次
     * {@code onRemove} —— 不加这个闸门，一张棋盘能掉出好几个物品。同 {@link TileEntityCChess}。</p>
     */
    private boolean pieceDropped = false;

    public TileEntityGomoku(BlockPos pos, BlockState blockState) {
        super(InitBlocks.GOMOKU_TE, pos, blockState);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        ListTag listTag = new ListTag();
        for (int[] chessRow : chessData) {
            listTag.add(new IntArrayTag(chessRow));
        }
        tag.put(CHESS_DATA, listTag);
        tag.putInt(STATUE, this.statue);
        tag.putBoolean(PLAYER_TURN, this.playerTurn);
        tag.putInt(CHESS_COUNTER, this.chessCounter);
        tag.put(LATEST_CHESS_POINT, Point.toTag(this.latestChessPoint));
        super.saveAdditional(tag);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        ListTag listTag = nbt.getList(CHESS_DATA, Tag.TAG_INT_ARRAY);
        int rows = Math.min(listTag.size(), GomokuUtil.GRID_SIZE);
        for (int i = 0; i < rows; i++) {
            int[] intArray = listTag.getIntArray(i);
            int cols = Math.min(intArray.length, GomokuUtil.GRID_SIZE);
            System.arraycopy(intArray, 0, this.chessData[i], 0, cols);
        }
        this.statue = nbt.getInt(STATUE);
        this.playerTurn = nbt.getBoolean(PLAYER_TURN);
        this.chessCounter = nbt.getInt(CHESS_COUNTER);
        if (nbt.contains(LATEST_CHESS_POINT, Tag.TAG_COMPOUND)) {
            Point point = Point.fromTag(nbt.getCompound(LATEST_CHESS_POINT));
            this.latestChessPoint = isValidPoint(point) ? point : Point.NULL;
        } else {
            this.latestChessPoint = Point.NULL;
        }
    }

    /**
     * 载入一个残局，对应上游 {@code TileEntityGomoku#setStateData}。
     *
     * <p>五子棋的残局数据里只有棋盘、回合数与最后一手，并不记录
     * 「该谁下」，所以这里把回合交给玩家；否则上一局偶然停在女仆回合，
     * 换上残局后就永远点不动。</p>
     */
    public void setStateData(GomokuCodec.StateData stateData) {
        this.chessData = stateData.board();
        this.chessCounter = stateData.turnCount();
        this.latestChessPoint = stateData.latestPoint();
        this.statue = Statue.IN_PROGRESS.ordinal();
        this.playerTurn = true;
        this.refresh();
    }

    public void reset() {
        this.chessData = new int[GomokuUtil.GRID_SIZE][GomokuUtil.GRID_SIZE];
        this.statue = Statue.IN_PROGRESS.ordinal();
        this.playerTurn = true;
        this.chessCounter = 0;
        this.latestChessPoint = Point.NULL;
    }

    /**
     * 调试用：铺满棋盘只留三个空位（上游「御币点击」那条路径）。
     *
     * <p>奇偶交替填充，因此不会出现五连 —— 否则一铺完就已经终局了。</p>
     */
    public void clickWithDebug() {
        int size = GomokuUtil.GRID_SIZE;
        int[][] drawBoard = new int[size][size];
        for (int x = 0; x < size; x++) {
            boolean blackFirst = (x / 2) % 2 == 0;
            for (int y = 0; y < size; y++) {
                // 最后一行留三个空位
                if (x == size - 1 && y >= size - 3) {
                    drawBoard[x][y] = Point.EMPTY;
                    continue;
                }
                if (blackFirst) {
                    drawBoard[x][y] = (y % 2 == 0) ? Point.BLACK : Point.WHITE;
                } else {
                    drawBoard[x][y] = (y % 2 == 0) ? Point.WHITE : Point.BLACK;
                }
            }
        }
        this.chessData = drawBoard;
        this.latestChessPoint = new Point(size - 1, size - 5, Point.WHITE);
        this.chessCounter = size * size - 3;
        this.statue = Statue.IN_PROGRESS.ordinal();
        this.playerTurn = true;
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

    public int[][] getChessData() {
        return chessData;
    }

    /**
     * 落子。{@code type} 由调用方保证是 {@link Point#BLACK} / {@link Point#WHITE}
     * 且该点为空 —— 三个入口（玩家点击、AI 回包、调试填充）都会先自己校验。
     */
    public void setChessData(int x, int y, int type) {
        this.chessData[x][y] = type;
        this.latestChessPoint = new Point(x, y, type);
        this.chessCounter += 1;
    }

    public boolean isPlayerTurn() {
        return playerTurn;
    }

    public void setPlayerTurn(boolean playerTurn) {
        this.playerTurn = playerTurn;
    }

    public void setStatue(Statue statue) {
        this.statue = statue.ordinal();
    }

    public Statue getStatue() {
        return Statue.values()[Mth.clamp(statue, 0, Statue.values().length - 1)];
    }

    public int getChessCounter() {
        return chessCounter;
    }

    public Point getLatestChessPoint() {
        return latestChessPoint;
    }

    /**
     * 判断一个坐标是否落在 15×15 棋盘内。
     */
    public static boolean isValidPoint(Point point) {
        return point != Point.NULL
                && 0 <= point.x && point.x < GomokuUtil.GRID_SIZE
                && 0 <= point.y && point.y < GomokuUtil.GRID_SIZE;
    }
}
