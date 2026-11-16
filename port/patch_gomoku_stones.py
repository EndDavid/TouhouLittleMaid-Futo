# -*- coding: utf-8 -*-
"""Fix the gomoku stones: they were drawn 180 degrees round and 0.11 blocks high.

Two independent mistakes, both the same *shape* as the chess bugs but with
opposite signs, so the reasoning is written down instead of guessed:

1.  Mirroring.  The chess renderer negates the lattice coordinate because
    BlockCChess rotates the click vector by the board's facing
    (``.yRot(facing.toYRot() * DEG)``) and the pose adds another 180 degrees
    for Z-facing boards -- so there the pose is a half turn *relative to the
    click frame*.  BlockGomoku does NOT rotate the click vector: it works in
    plain block-local axes.  Negating here therefore mirrored the stone to the
    opposite intersection.

    No compensation is needed either, because the rotation the pose applies
    (0 / 90 / 180 / 270 degrees) maps this lattice onto itself: the gomoku
    grid is a 15x15 square lattice centred on the block centre.  Drawing at
    the unrotated ``lineAt(k)`` therefore lands on a painted line for all four
    facings, and it is the same k the click handler returns.

2.  Double lift.  The parent translate already puts the origin at the table
    surface, so the stone only needs ``-minY``; using ``surface - minY``
    lifted it by another 0.11 blocks (~1.8 px) -- "the stones float a little".
    A 0.001 nudge keeps the bottom face off the coplanar grid layer.
"""
import io
import sys

PATH = 'src/main/java/com/github/tartaricacid/touhoulittlemaid/client/renderer/tileentity/TileEntityGomokuRenderer.java'

raw = io.open(PATH, encoding='utf-8', newline='').read()
if '\r\n' not in raw:
    sys.exit('expected CRLF')
edits = []


def sub(old, new):
    global raw
    count = raw.count(old)
    if count != 1:
        sys.exit('anchor found %d times: %r' % (count, old[:80]))
    raw = raw.replace(old, new)
    edits.append(old.strip().splitlines()[0][:48])


# ---------------------------------------------------------------- 1. double lift
sub(
    u'        float pieceLift = surface - pb[1];\r\n',
    u'        // 棋子的 y 是【相对桌面】的：上面那句 translate 已经把原点放到桌面高度，\r\n'
    u'        // 所以只要把棋子底面 pb[1] 抬到 0 即可。再减一次 surface 就是抬了两遍，\r\n'
    u'        // 表现是棋子悬空 0.11 格 \u2248 1.8 像素（本次报告的现象）。\r\n'
    u'        // +0.001 是为了不让棋子底面与格线层共面打架（0.016 像素，看不见）。\r\n'
    u'        float pieceLift = -pb[1] + 0.001F;\r\n')

# ------------------------------------------------------- 2. no board rotation for stones
sub(
    u'        int[][] chessData = gomoku.getChessData();\r\n'
    u'        poseStack.pushPose();\r\n'
    u'        poseStack.translate(0.5, surface, 0.5);\r\n'
    u'        BoardRenderUtil.applyFacing(poseStack, facing);\r\n',
    u'        int[][] chessData = gomoku.getChessData();\r\n'
    u'        poseStack.pushPose();\r\n'
    u'        poseStack.translate(0.5, surface, 0.5);\r\n'
    u'        // 棋子【不】跟着棋盘转，因为点击判定也不跟着转：\r\n'
    u'        //   * BlockGomoku#use 拿到的是原始方块内坐标，没有按朝向旋转过；\r\n'
    u'        //   * applyFacing 只是把棋盘画给玩家看，转的是 0/90/180/270 度；\r\n'
    u'        //   * 这 15\u00d715 的格线是「以方块中心为中心的正方形」，任何 90 度整数倍\r\n'
    u'        //     旋转都把它映回自己。\r\n'
    u'        // 所以不旋转地摆在 lineAt(k) 上，既落在画出来的格线上，也和点击返回的 k 一致。\r\n'
    u'        // （中国象棋那边必须取负 —— 它的点击向量按朝向转过，姿态相对点击坐标系才是半圈，\r\n'
    u'        //   见 BoardRenderUtil#applyFacing。）\r\n')

# --------------------------------------------------------- 3. no negation in the loop
sub(
    u'                float localX = (float) GomokuUtil.lineAt(x);\r\n'
    u'                float localZ = (float) GomokuUtil.lineAt(y);\r\n'
    u'                // 姿态比点击坐标系多转半圈，所以格线坐标取负（见 BoardRenderUtil#applyFacing）\r\n'
    u'                float renderX = pieceCenterX - localX;\r\n'
    u'                float renderZ = pieceCenterZ - localZ;\r\n',
    u'                // 棋子模型的中心未必正好在原点，所以平移量要减掉它\r\n'
    u'                float renderX = (float) GomokuUtil.lineAt(x) - pieceCenterX;\r\n'
    u'                float renderZ = (float) GomokuUtil.lineAt(y) - pieceCenterZ;\r\n')

# ------------------------------------------------- 4. latest-move marker, same frame
sub(
    u'        float localX = (float) GomokuUtil.lineAt(point.x);\r\n'
    u'        float localZ = (float) GomokuUtil.lineAt(point.y);\r\n'
    u'        // 标记必须跟着棋盘转动，否则棋盘朝东 / 朝西时它会跑到另一颗棋子上方。\r\n'
    u'        // 这里把「棋子用的那套变换」手算一遍（translate(0.5,\u00b7,0.5) + Ry(\u03b8)），\r\n'
    u'        // 因为文字的 billboard 旋转必须发生在<b>世界坐标</b>里，不能被棋盘的旋转带着走。\r\n'
    u'        float renderX = -localX;\r\n'
    u'        float renderZ = -localZ;\r\n'
    u'        double yaw = Math.toRadians(facing.toYRot() + (facing.getAxis() == Direction.Axis.Z ? 180.0 : 0.0));\r\n'
    u'        double cos = Math.cos(yaw);\r\n'
    u'        double sin = Math.sin(yaw);\r\n'
    u'        float worldX = (float) (0.5 + renderX * cos + renderZ * sin);\r\n'
    u'        float worldZ = (float) (0.5 - renderX * sin + renderZ * cos);\r\n',
    u'        // 和棋子同一个坐标系（「不跟着棋盘转」的那一套），但必须用世界坐标：\r\n'
    u'        // 文字的 billboard 旋转不能被棋盘的旋转带着走。\r\n'
    u'        float worldX = (float) (0.5 + GomokuUtil.lineAt(point.x));\r\n'
    u'        float worldZ = (float) (0.5 + GomokuUtil.lineAt(point.y));\r\n')

# ------------------------------------------------------ 5. class javadoc: the two rules
sub(
    u' * <p>\u8fd9\u4e0e {@link GomokuUtil} \u91cc\u7684\u4e24\u4e2a\u5e38\u6570\u662f\u540c\u4e00\u7ec4\u6570\u5b57 \u2014\u2014 \u70b9\u51fb\u5224\u5b9a\u548c\u6e32\u67d3\u5171\u7528\u5b83\u4eec\uff0c\r\n'
    u' * \u6240\u4ee5\u300c\u68cb\u5b50\u753b\u5728\u54ea\u91cc\u300d\u548c\u300c\u70b9\u54ea\u91cc\u80fd\u843d\u5b50\u300d\u5728\u7eb8\u9762\u4e0a\u5c31\u4e0d\u53ef\u80fd\u4e0d\u4e00\u81f4\u3002</p>\r\n',
    u' * <p>\u8fd9\u4e0e {@link GomokuUtil} \u91cc\u7684\u4e24\u4e2a\u5e38\u6570\u662f\u540c\u4e00\u7ec4\u6570\u5b57 \u2014\u2014 \u70b9\u51fb\u5224\u5b9a\u548c\u6e32\u67d3\u5171\u7528\u5b83\u4eec\u3002</p>\r\n'
    u' *\r\n'
    u' * <h2>\u4e3a\u4ec0\u4e48\u68cb\u5b50\u4e0d\u8ddf\u7740\u68cb\u76d8\u8f6c</h2>\r\n'
    u' * <p>{@code BlockGomoku#use} \u62ff\u5230\u7684\u662f<b>\u539f\u59cb\u65b9\u5757\u5185\u5750\u6807</b>\uff0c\u6ca1\u6709\u6309\u671d\u5411\u65cb\u8f6c\u8fc7\uff1b\r\n'
    u' * \u800c\u68cb\u76d8\u672c\u8eab\u8fd8\u8981\u8f6c\u7ed9\u73a9\u5bb6\u770b\u3002\u4e24\u8fb9\u8981\u5bf9\u4e0a\uff0c\u53ea\u9700\u4e00\u4e2a\u4e8b\u5b9e\uff1a\r\n'
    u' * applyFacing \u8f6c\u7684\u662f 0/90/180/270 \u5ea6\uff0c\u800c\u8fd9\u5f20 15\u00d715 \u7684\u683c\u7ebf\u662f\r\n'
    u' * <b>\u4ee5\u65b9\u5757\u4e2d\u5fc3\u4e3a\u4e2d\u5fc3\u7684\u6b63\u65b9\u5f62</b>\uff0c\u4efb\u4f55 90 \u5ea6\u6574\u6570\u500d\u65cb\u8f6c\u90fd\u628a\u5b83\u6620\u56de\u81ea\u5df1\u3002\r\n'
    u' * \u6240\u4ee5\u68cb\u5b50\u5728<b>\u4e0d\u65cb\u8f6c</b>\u7684\u5750\u6807\u7cfb\u91cc\u6446\u5230 {@code lineAt(k)} \u4e0a\uff0c\r\n'
    u' * \u5c31\u4e00\u5b9a\u843d\u5728\u753b\u51fa\u6765\u7684\u683c\u7ebf\u4e0a\uff0c\u4e14\u5c31\u662f\u70b9\u51fb\u8fd4\u56de\u7684\u90a3\u4e2a k\u3002</p>\r\n'
    u' *\r\n'
    u' * <p>\u8fd9\u4e0e\u4e2d\u56fd\u8c61\u68cb\u90a3\u8fb9\u6070\u597d\u76f8\u53cd\uff1a\u8c61\u68cb\u7684\u70b9\u51fb\u5411\u91cf\u5148\u6309\u671d\u5411\u8f6c\u8fc7\r\n'
    u' * \uff08{@code .yRot(facing.toYRot() * DEG)}\uff09\uff0c\u59ff\u6001\u76f8\u5bf9\u70b9\u51fb\u5750\u6807\u7cfb\u624d\u662f\u534a\u5708\uff0c\r\n'
    u' * \u6240\u4ee5\u5b83\u5fc5\u987b\u53d6\u8d1f\u3002\u4e24\u5757\u68cb\u76d8\u5ea6\u91cf\u65b9\u5f0f\u4e00\u6837\u3001\u7ed3\u8bba\u76f8\u53cd\uff0c\r\n'
    u' * \u5dee\u522b\u53ea\u5728\u300c\u70b9\u51fb\u6709\u6ca1\u6709\u5148\u8f6c\u300d\u8fd9\u4e00\u6b65\u3002</p>\r\n')

io.open(PATH, 'w', encoding='utf-8', newline='').write(raw)
print('gomoku renderer patched: %d anchors' % len(edits))

