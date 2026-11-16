package com.github.tartaricacid.touhoulittlemaid.util;

import com.github.tartaricacid.touhoulittlemaid.api.game.xqwlight.Position;
import net.minecraft.world.phys.Vec3;

/**
 * 中国象棋的坐标换算与局面判定，移植自上游 {@code util/CChessUtil}。
 *
 * <h2>为什么点击坐标是手调出来的</h2>
 * <p>{@link #getClickPosition} 里那两个魔数（{@code 1.365} / {@code 0.304}）不是随便写的：
 * 棋盘模型是 3×3 格、画在方块空间里的，九十个交叉点的间距正好 0.304 格，
 * 左下角（红方视角的 FILE_LEFT / RANK_TOP）落在 {@code (1.365, 1.370)}。
 * 这些数字与模型几何一一对应，改任何一个都必须同时改 {@code models/bedrock/block/cchess.json}。</p>
 *
 * <p>{@link #isClickResetArea} 圈的是棋盘侧面那块「重开」按钮 —— 模型上画了两条竖线的地方，
 * 点中间不生效、点那两条才生效，所以判定是两段区间相加。</p>
 */
public final class CChessUtil {
    // 女仆必输残局，测试用
    // rnbakab1r/9/8R/p1p1C4/1C4p1p/9/P1P1P1P1P/2N5N/9/R1BAKAB2
    // 长打残局
    // 1C1a2br1/3rak3/7c1/p3P2Rp/5n3/9/P1R3p1P/4C4/4A3N/2BAK3c
    // 六十回合自然限着
    // 3aka3/9/9/9/9/9/9/9/9/3AKA3
    public static final String INIT = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR";

    private CChessUtil() {
    }

    /**
     * 这一点是不是「重开棋盘」区域。
     */
    public static boolean isClickResetArea(Vec3 clickPos) {
        double x = clickPos.x;
        double z = clickPos.z;
        if (1.199 < x && x < 1.431) {
            return (0.464 < z && z < 1.061) || (-1.061 < z && z < -0.464);
        }
        return false;
    }

    /**
     * 把方块空间里的点击点换算成棋盘坐标；没点到交叉点（或超出棋盘）返回 {@code -1}。
     */
    public static int getClickPosition(Vec3 clickPos) {
        double x = (clickPos.x + 1.365) / 0.304;
        double z = (clickPos.z + 1.370) / 0.304;
        int xRound = (int) Math.round(x);
        int zRound = (int) Math.round(z);
        double xAbs = Math.abs(xRound - x);
        double zAbs = Math.abs(zRound - z);

        // 0.3 是「离交叉点多近才算点中」；再大就会把相邻两格的空隙全吃掉
        if (xAbs < 0.3 && zAbs < 0.3) {
            xRound += Position.FILE_LEFT;
            zRound += Position.RANK_TOP;
            if (xRound <= Position.FILE_RIGHT && zRound <= Position.RANK_BOTTOM) {
                return Position.COORD_XY(xRound, zRound);
            }
        }
        return -1;
    }

    /**
     * 取某个交叉点上的棋子编码（{@code 0} = 空）。
     *
     * <p>引擎的坐标是 {@code 0x88} 布局，所以必须走 {@code COORD_XY}，
     * 不能直接用 {@code y * 9 + x} 索引。与上游 1.20.1 同名方法一致。</p>
     */
    public static byte piecesIndex(int x, int y, byte[] data) {
        return data[Position.COORD_XY(x, y)];
    }

    public static boolean isRed(byte piecesIndex) {
        return (piecesIndex & 8) == 8;
    }

    public static boolean isBlack(byte piecesIndex) {
        return (piecesIndex & 16) == 16;
    }

    public static boolean isPlayer(Position position) {
        return position.sdPlayer == 0;
    }

    public static boolean isMaid(Position position) {
        return position.sdPlayer == 1;
    }

    /**
     * 六十回合自然限着（判和）。
     */
    public static boolean reachMoveLimit(Position position) {
        return position.moveNum > 60;
    }

    /**
     * 三回合长打（判和）。
     */
    public static boolean isRepeat(Position position) {
        return position.repStatus(3) > 0;
    }
}

