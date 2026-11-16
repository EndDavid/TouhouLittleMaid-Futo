# -*- coding: utf-8 -*-
"""Seat the gomoku maid beside the WHITE bowl (she always plays white).

The board art's two bowls sit on the diagonals, antipodal through the block
centre, so "which side is white" depends on the board's facing.  Upstream
hard-codes `facing.getClockWise()`, which happens to be the white side for the
Z-axis facings only; for EAST and WEST it puts her beside the black bowl.
port/gomoku_seat_probe.py scores both rules for all four facings.

The replacement rule is derived rather than tabulated: take the white bowl's
centre in the un-posed model frame, push it through the same rotation the
renderer applies (BoardRenderUtil#applyFacing), and pick the horizontal
direction it points at most strongly.  If the model or the pose ever changes,
the seat follows automatically.
"""
import io
import sys

UTIL = 'src/main/java/com/github/tartaricacid/touhoulittlemaid/util/GomokuUtil.java'
BLOCK = 'src/main/java/com/github/tartaricacid/touhoulittlemaid/block/BlockGomoku.java'


def patch(path, edits):
    raw = io.open(path, encoding='utf-8', newline='').read()
    nl = '\r\n' if '\r\n' in raw else '\n'
    for old, new in edits:
        old = old.replace('\n', nl)
        new = new.replace('\n', nl)
        count = raw.count(old)
        if count != 1:
            sys.exit('%s: anchor found %d times: %r' % (path, count, old[:70]))
        raw = raw.replace(old, new)
    io.open(path, 'w', encoding='utf-8', newline='').write(raw)
    return len(edits)


# ------------------------------------------------------------------ GomokuUtil
util_edits = [
    ("package com.github.tartaricacid.touhoulittlemaid.util;\n"
     "\n"
     "import com.github.tartaricacid.touhoulittlemaid.block.properties.GomokuPart;\n",
     "package com.github.tartaricacid.touhoulittlemaid.util;\n"
     "\n"
     "import com.github.tartaricacid.touhoulittlemaid.block.properties.GomokuPart;\n"
     "import net.minecraft.core.Direction;\n"),

    ("    public static double lineAt(int index) {\n"
     "        return GRID_ORIGIN + index * GRID_STEP;\n"
     "    }\n",
     "    public static double lineAt(int index) {\n"
     "        return GRID_ORIGIN + index * GRID_STEP;\n"
     "    }\n"
     "\n"
     "    /**\n"
     "     * \u767d\u68cb\u76d2\u4e2d\u5fc3\u5728\u300c\u672a\u65cb\u8f6c\u7684\u6a21\u578b\u5750\u6807\u7cfb\u300d\u91cc\u7684\u4f4d\u7f6e\uff08\u683c\uff0c\u76f8\u5bf9\u65b9\u5757\u4e2d\u5fc3\uff09\u3002\n"
     "     *\n"
     "     * <p>\u4ece {@code gomoku.json} \u7684 {@code whiteBox} \u91cf\u51fa\u6765\u7684\uff1a\u4e00\u4e2a {@code [17, 0, 8]} \u8d77\u3001{@code [5, 1, 5]} \u5927\u7684 cube\uff0c\n"
     "     * \u6309 {@code GeoCube} \u7684\u7b97\u6cd5\uff08x \u53d6\u8d1f\u3001\u9664\u4ee5 16\uff09\u5c31\u662f {@code x -1.375..-1.0625} / {@code z 0.5..0.8125}\uff0c\n"
     "     * \u53d6\u4e2d\u5fc3\u5f97\u5230\u4e0b\u9762\u7684\u6570\u3002{@code blackBox} \u521a\u597d\u662f\u5b83\u7684\u76f8\u53cd\u6570 \u2014\u2014\n"
     "     * \u4e24\u4e2a\u68cb\u76d2\u5728\u5bf9\u89d2\u7ebf\u4e0a\u3001\u5173\u4e8e\u65b9\u5757\u4e2d\u5fc3\u5bf9\u79f0\u3002</p>\n"
     "     */\n"
     "    private static final double WHITE_BOWL_X = -1.21875D;\n"
     "    private static final double WHITE_BOWL_Z = 0.65625D;\n"
     "\n"
     "    /**\n"
     "     * \u5973\u4ec6\u5e94\u8be5\u5750\u5728\u54ea\u4e00\u4fa7\uff08\u56db\u4e2a\u6c34\u5e73\u65b9\u5411\u4e4b\u4e00\uff09\u3002\n"
     "     *\n"
     "     * <h2>\u4e3a\u4ec0\u4e48\u4e0d\u80fd\u7528 {@code facing.getClockWise()}</h2>\n"
     "     * <p>\u4e0a\u6e38\u5199\u7684\u5c31\u662f {@code getClockWise()}\u3002\u4f46\u4e24\u4e2a\u68cb\u76d2\u5728<b>\u5bf9\u89d2\u7ebf</b>\u4e0a\uff08\u89c1 {@link #WHITE_BOWL_X}\uff09\uff0c\n"
     "     * \u800c\u68cb\u76d8\u7684\u59ff\u6001\u5bf9 Z \u8f74\u671d\u5411\u989d\u5916\u8f6c\u4e86 180\u00b0\u3001\u5bf9 X \u8f74\u671d\u5411\u6ca1\u6709\uff08\u89c1 {@code BoardRenderUtil#applyFacing}\uff09\u3002\n"
     "     * \u4e24\u4ef6\u4e8b\u5408\u5728\u4e00\u8d77\uff0c\u7ed3\u679c\u5c31\u662f\u300c\u987a\u65f6\u9488\u4e00\u683c\u300d\u53ea\u5728 NORTH / SOUTH \u6070\u597d\u6307\u5411\u767d\u68cb\u76d2\uff0c\n"
     "     * \u5728 EAST / WEST \u6307\u5411\u7684\u662f<b>\u9ed1</b>\u68cb\u76d2 \u2014\u2014 \u5973\u4ec6\u660e\u660e\u6267\u767d\uff0c\u5374\u5750\u5728\u9ed1\u5b50\u76d2\u65c1\u8fb9\u3002</p>\n"
     "     *\n"
     "     * <p>\u6240\u4ee5\u8fd9\u91cc\u4e0d\u67e5\u8868\uff0c\u800c\u662f\u628a\u767d\u68cb\u76d2\u7684\u4e2d\u5fc3\u6309\u7167\u6e32\u67d3\u5668\u7528\u7684\u540c\u4e00\u4e2a\u65cb\u8f6c\u9001\u4e00\u904d\uff0c\n"
     "     * \u518d\u9009\u5b83\u6700\u9760\u8fd1\u7684\u6c34\u5e73\u65b9\u5411\u3002\u6a21\u578b\u6216\u59ff\u6001\u4ee5\u540e\u518d\u6539\uff0c\u5ea7\u4f4d\u4e5f\u4f1a\u81ea\u5df1\u8ddf\u4e0a\u3002</p>\n"
     "     */\n"
     "    public static Direction seatDirection(Direction facing) {\n"
     "        // \u4e0e BoardRenderUtil#applyFacing \u9010\u5b57\u5bf9\u5e94\uff1aYP(facing.toYRot())\uff0cZ \u8f74\u671d\u5411\u518d\u8865 180\u00b0\u3002\n"
     "        double angle = facing.toYRot() + (facing.getAxis() == Direction.Axis.Z ? 180.0D : 0.0D);\n"
     "        double rad = Math.toRadians(angle);\n"
     "        double cos = Math.cos(rad);\n"
     "        double sin = Math.sin(rad);\n"
     "        // Vector3f.YP.rotationDegrees(a)\uff08\u4e0e Vec3#yRot \u540c\u4e00\u5957\uff09\uff1a\n"
     "        //     (x, z) -> (x cos a + z sin a, -x sin a + z cos a)\n"
     "        double bowlX = WHITE_BOWL_X * cos + WHITE_BOWL_Z * sin;\n"
     "        double bowlZ = -WHITE_BOWL_X * sin + WHITE_BOWL_Z * cos;\n"
     "        Direction best = Direction.NORTH;\n"
     "        double bestDot = -Double.MAX_VALUE;\n"
     "        for (Direction direction : Direction.Plane.HORIZONTAL) {\n"
     "            double dot = direction.getStepX() * bowlX + direction.getStepZ() * bowlZ;\n"
     "            if (dot > bestDot) {\n"
     "                bestDot = dot;\n"
     "                best = direction;\n"
     "            }\n"
     "        }\n"
     "        return best;\n"
     "    }\n"),
]

# ------------------------------------------------------------------ BlockGomoku
block_edits = [
    ("    @Override\n"
     "    public void startMaidSit(EntityMaid maid, BlockState state, Level worldIn, BlockPos pos) {\n"
     "        // \u4e94\u5b50\u68cb\u7684\u76d8\u9762\u662f 2\u00d72 \u683c\uff0c\u5973\u4ec6\u5750\u5728\u4fa7\u8fb9\u4e00\u683c\u534a\u5904\u521a\u597d\u9762\u5bf9\u68cb\u76d8\n"
     "        this.sitMaidAt(maid, state, worldIn, pos, state.getValue(FACING).getClockWise(), 1.5D);\n"
     "    }\n",
     "    @Override\n"
     "    public void startMaidSit(EntityMaid maid, BlockState state, Level worldIn, BlockPos pos) {\n"
     "        // \u4e94\u5b50\u68cb\u7684\u76d8\u9762\u662f 2\u00d72 \u683c\uff0c\u5973\u4ec6\u5750\u5728\u4fa7\u8fb9\u4e00\u683c\u534a\u5904\u521a\u597d\u9762\u5bf9\u68cb\u76d8\u3002\n"
     "        //\n"
     "        // \u65b9\u5411\u4e0d\u80fd\u7528 getClockWise()\uff08\u4e0a\u6e38\u662f\u90a3\u4e48\u5199\u7684\uff09\uff1a\u4e24\u4e2a\u68cb\u76d2\u5728\u5bf9\u89d2\u7ebf\u4e0a\uff0c\n"
     "        // \u987a\u65f6\u9488\u4e00\u683c\u53ea\u5728 Z \u8f74\u671d\u5411\u6307\u5411\u767d\u68cb\u76d2\uff0cEAST / WEST \u4f1a\u6307\u5411\u9ed1\u68cb\u76d2\uff0c\n"
     "        // \u800c\u5973\u4ec6\u627e\u7684\u662f\u767d\u5b50\u3002\u63a8\u5bfc\u4e0e\u6d4b\u8bd5\u89c1 GomokuUtil#seatDirection\n"
     "        // \u4e0e port/gomoku_seat_probe.py\u3002\n"
     "        this.sitMaidAt(maid, state, worldIn, pos, GomokuUtil.seatDirection(state.getValue(FACING)), 1.5D);\n"
     "    }\n"),
]

print('GomokuUtil: %d anchors' % patch(UTIL, util_edits))
print('BlockGomoku: %d anchors' % patch(BLOCK, block_edits))

