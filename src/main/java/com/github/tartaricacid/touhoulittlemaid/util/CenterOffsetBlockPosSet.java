package com.github.tartaricacid.touhoulittlemaid.util;

import net.minecraft.core.BlockPos;

import java.util.BitSet;

/**
 * 基于中心偏移量的快速方块位置标记缓存，移植自上游 {@code util/CenterOffsetBlockPosSet}。
 *
 * <p>把「以某点为中心、半边长 x/y/z 的立方体」压进一个 {@link BitSet}，
 * 用于 {@code MaidPathFindingBFS} 记录「已访问过的方块」，比 {@code HashSet&lt;BlockPos&gt;}
 * 快一个数量级（BFS 每 tick 要查成百上千次）。</p>
 *
 * <p>注意：越界查询返回 {@code true}（视为「已访问」），这样 BFS 就不会把搜索扩到
 * 预设范围之外。</p>
 */
public class CenterOffsetBlockPosSet {
    private final int cx;
    private final int cy;
    private final int cz;
    private final int x;
    private final int y;
    private final int z;
    private final BitSet bitset;

    public CenterOffsetBlockPosSet(int x, int y, int z, int cx, int cy, int cz) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.bitset = new BitSet(x * y * z * 8);
    }

    public void markVis(BlockPos pos) {
        int tx = pos.getX() - cx + x;
        int ty = pos.getY() - cy + y;
        int tz = pos.getZ() - cz + z;
        int index = 4 * x * y * tz + 2 * y * tx + ty;
        if (tx < 0 || ty < 0 || tz < 0 || tx >= x * 2 || ty >= y * 2 || tz >= z * 2) {
            return;
        }
        bitset.set(index, true);
    }

    public boolean isVis(BlockPos pos) {
        int tx = pos.getX() - cx + x;
        int ty = pos.getY() - cy + y;
        int tz = pos.getZ() - cz + z;
        if (tx < 0 || ty < 0 || tz < 0 || tx >= x * 2 || ty >= y * 2 || tz >= z * 2) {
            return true;
        }
        int index = 4 * x * y * tz + 2 * y * tx + ty;
        return bitset.get(index);
    }

    public boolean isVis(int ix, int iy, int iz) {
        int tx = ix - cx + x;
        int ty = iy - cy + y;
        int tz = iz - cz + z;
        if (tx < 0 || ty < 0 || tz < 0 || tx >= x * 2 || ty >= y * 2 || tz >= z * 2) {
            return true;
        }
        int index = 4 * x * y * tz + 2 * y * tx + ty;
        return bitset.get(index);
    }

    public void clear() {
        bitset.clear();
    }
}

