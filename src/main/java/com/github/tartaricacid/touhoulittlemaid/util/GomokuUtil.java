package com.github.tartaricacid.touhoulittlemaid.util;

import com.github.tartaricacid.touhoulittlemaid.block.properties.GomokuPart;
import net.minecraft.core.Direction;

import javax.annotation.Nullable;

/**
 * 五子棋盘面的格线几何。<b>渲染器和点击判定共用这一份数字。</b>
 *
 * <h2>这些数字是量出来的，不是猜的</h2>
 * <p>{@code gomoku.json} 里那块「格线层」是一块 {@code size [151, 151, 151]}、
 * {@code inflate -59.5} 的 cube，box-UV 的 up 面取到贴图上 151×151 的一整块正方形，
 * 而这块正方形在方块空间里正好铺满 2×2 格。直接用脚本把它解码出来数一数
 * （{@code port/board_grid_probe.py}），15 条格线落在贴图内的第 5、15、25 … 145 列 / 行 —— 
 * 间距 10 texel，两端各留 5 texel，<b>关于中心完全对称</b>。</p>
 *
 * <p>换算成方块空间：</p>
 * <pre>
 *     每 texel    = 2 / 151            = 0.013245 格
 *     间距        = 10 texel           = 0.132450 格
 *     第 0 条线   = -1 + 5.5 texel     = -0.927152 格
 *     第 14 条线  = +0.927152 格
 * </pre>
 *
 * <h2>为什么不再沿用上游的九个偏移三元组</h2>
 * <p>上游 {@code BlockGomoku#getChessPos} 给九宫格的每一格各写了一组
 * {@code (xOffset, xStartOffset, xIndexOffset)}，而这三组之间彼此并不自洽
 * （同为第 4 号交叉点，从 CENTER 格算出来是 0.115，从 LEFT_CENTER 格算出来是 0.1014，
 * 差 0.0136 格 = 0.2 像素），与贴图上真正画出来的格线也差，
 * 累到棋盘另一头差 0.042 格 = <b>0.65 像素</b> —— 而一颗棋子本身只有
 * 1.5 像素宽（{@code gomoku_piece.json} 的 2 像素减去两侧各 0.25 的 inflate），
 * 也就是棋子会偏离格线约四分之一个棋子。</p>
 *
 * <p>所以这里改成：把点击点换算成「盘面坐标」（方块中心为 0、整块盘面 -1..1），
 * 再四舍五入到最近的格线。判定容差是半格 —— 既保证「点在交叉点附近就落子」，
 * 又保证<b>棋子画在哪里就能点在哪里</b>。上游那套写法有近一半的面积是点不到的死区
 * （0.07 的判定框对着 0.1316 的格子）。</p>
 */
public final class GomokuUtil {
    /**
     * 交叉点个数（15 × 15）。
     */
    public static final int GRID_SIZE = 15;

    /**
     * 相邻两条格线的间距（格）。
     */
    public static final double GRID_STEP = 0.132450D;

    /**
     * 第 0 条格线在盘面坐标里的位置。
     */
    public static final double GRID_ORIGIN = -0.927152D;

    private GomokuUtil() {
    }

    /**
     * 第 {@code index} 条格线在盘面坐标里的位置（x、z 相同）。
     */
    public static double lineAt(int index) {
        return GRID_ORIGIN + index * GRID_STEP;
    }

    /**
     * 白棋盒中心在「未旋转的模型坐标系」里的位置（格，相对方块中心）。
     *
     * <p>从 {@code gomoku.json} 的 {@code whiteBox} 量出来的：一个 {@code [17, 0, 8]} 起、{@code [5, 1, 5]} 大的 cube，
     * 按 {@code GeoCube} 的算法（x 取负、除以 16）就是 {@code x -1.375..-1.0625} / {@code z 0.5..0.8125}，
     * 取中心得到下面的数。{@code blackBox} 刚好是它的相反数 ——
     * 两个棋盒在对角线上、关于方块中心对称。</p>
     */
    private static final double WHITE_BOWL_X = -1.21875D;
    private static final double WHITE_BOWL_Z = 0.65625D;

    /**
     * 女仆应该坐在哪一侧（四个水平方向之一）。
     *
     * <h2>为什么不能用 {@code facing.getClockWise()}</h2>
     * <p>上游写的就是 {@code getClockWise()}。但两个棋盒在<b>对角线</b>上（见 {@link #WHITE_BOWL_X}），
     * 「顺时针一格」与「白盒在哪一侧」之间没有任何必然关系 —— 按 {@code port/gomoku_seat_probe.py}
     * 算下来，四个朝向<b>全部</b>会坐到黑盒那一侧：女仆明明执白，却坐在黑子盒旁边。</p>
     *
     * <p>顺带记一个容易弄错的常量：{@code Direction#toYRot()} 是 {@code 2d data value * 90}，
     * 也就是 <b>SOUTH 0 / WEST 90 / NORTH 180 / EAST 270</b>（NORTH 不是 0）。
     * 它当成 NORTH = 0 会让两个 Z 轴朝向的结论正好反过来。</p>
     *
     * <p>所以这里不查表，而是把白棋盒的中心按照渲染器用的同一个旋转送一遍，
     * 再选它最靠近的水平方向。模型或姿态以后再改，座位也会自己跟上。</p>
     */
    public static Direction seatDirection(Direction facing) {
        // 与 BoardRenderUtil#applyFacing 逐字对应：YP(facing.toYRot())，Z 轴朝向再补 180°。
        // 合起来四个朝向实际是 NORTH 0 / SOUTH 180 / EAST 270 / WEST 90 度。
        double angle = facing.toYRot() + (facing.getAxis() == Direction.Axis.Z ? 180.0D : 0.0D);
        double rad = Math.toRadians(angle);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        // Vector3f.YP.rotationDegrees(a)（与 Vec3#yRot 同一套）：
        //     (x, z) -> (x cos a + z sin a, -x sin a + z cos a)
        double bowlX = WHITE_BOWL_X * cos + WHITE_BOWL_Z * sin;
        double bowlZ = -WHITE_BOWL_X * sin + WHITE_BOWL_Z * cos;
        Direction best = Direction.NORTH;
        double bestDot = -Double.MAX_VALUE;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            double dot = direction.getStepX() * bowlX + direction.getStepZ() * bowlZ;
            if (dot > bestDot) {
                bestDot = dot;
                best = direction;
            }
        }
        return best;
    }

    /**
     * 把「点了九宫格里某一格的哪个位置」换算成交叉点下标。
     *
     * @param part   被点到的部件（提供该格相对中心格的偏移）
     * @param localX 点击点在<b>该格自身</b>里的局部 x（0..1）
     * @param localZ 同上，z
     * @return {@code [x, y]}（0..14），没点在交叉点附近时返回 {@code null}
     */
    @Nullable
    public static int[] getGridPoint(GomokuPart part, double localX, double localZ) {
        int[] gridX = nearestLine(part.getPosX() + localX - 0.5D);
        int[] gridZ = nearestLine(part.getPosY() + localZ - 0.5D);
        if (gridX == null || gridZ == null) {
            return null;
        }
        // 两个方向必须同时命中，否则点在格子中间会「落到斜对面的交叉点」上
        if (gridX[1] != 0 || gridZ[1] != 0) {
            return null;
        }
        return new int[]{gridX[0], gridZ[0]};
    }

    /**
     * 找离 {@code board} 最近的格线。
     *
     * @return {@code [index, 0]}：命中；{@code [index, 1]}：最近的是 {@code index}，但离得太远；
     * {@code null}：已经超出棋盘范围
     */
    @Nullable
    private static int[] nearestLine(double board) {
        int index = (int) Math.round((board - GRID_ORIGIN) / GRID_STEP);
        if (index < 0 || index >= GRID_SIZE) {
            return null;
        }
        double distance = Math.abs(board - lineAt(index));
        return new int[]{index, distance <= GRID_STEP * 0.5D ? 0 : 1};
    }
}

