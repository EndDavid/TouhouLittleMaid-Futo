# -*- coding: utf-8 -*-
"""Add the gomoku / western-chess documentation to README.md.

README.md is CRLF-only and must not be touched by editor tooling (it has been
truncated before), so every edit here is an exact-anchor replacement with an
occurrence assertion, and every I/O call passes newline=''.
"""
import io
import sys

PATH = 'README.md'

raw = io.open(PATH, encoding='utf-8', newline='').read()
assert '\r\n' in raw, 'README should be CRLF'

edits = []


def sub(old, new):
    global raw
    count = raw.count(old)
    if count != 1:
        sys.exit('anchor found %d times: %r' % (count, old[:70]))
    raw = raw.replace(old, new)
    edits.append(old[:40])


# 1) status table row
sub(
    u'| **棋类（中国象棋：3\u00d73 多方块 + 对局 AI）** | **完成**（只有象棋，五子棋 / 国际象棋未移植） |',
    u'| **棋类（象棋 / 五子棋 / 国际象棋：各 3\u00d73 多方块 + 对局 AI）** | **完成**（三块棋盘全部可玩） |')

# 2) chapter title
sub(u'## 棋类（中国象棋）', u'## 棋类（中国象棋 / 五子棋 / 国际象棋）')

# 3) replace the old "only cchess" limitation block with the new sections
anchor = (
    u'### 已知限制\r\n'
    u'\r\n'
    u'- **只移植了中国象棋**。五子棋（`gomoku` + 1712 行 `ZhiZhangAIService`）与\r\n'
    u'  国际象棋（`wchess`）未移植，因此手册里只放了一页象棋，\r\n'
    u'  `$registered` 里也只登记了 `touhou_little_maid:cchess`。\r\n')

new_block = (
    u'### 五子棋与国际象棋：同一套骨架，三份不同的「格线」\r\n'
    u'\r\n'
    u'两块新棋盘（`BlockGomoku` / `TileEntityGomoku`、`BlockWChess` / `TileEntityWChess`）\r\n'
    u'复用了象棋那三层结构，差异只在引擎与盘面几何：\r\n'
    u'\r\n'
    u'| | 中国象棋 | 五子棋 | 国际象棋 |\r\n'
    u'|---|---|---|---|\r\n'
    u'| 对战数据 | FEN（`xqwlight.Position`） | `int[15][15]`（无序列化入口） | FEN（`api/game/chess.Position`） |\r\n'
    u'| 存档字段 | 1 个字符串 | 15 条 `IntArrayTag` | 1 个字符串 |\r\n'
    u'| 谁先手 | 玩家红方 | 玩家黑方 | 玩家白方 |\r\n'
    u'| 算棋位置 | 客户端（`Search#searchMain`，1 秒） | 客户端（`ZhiZhangAIService`） | 客户端（`Search#searchMain`，1 秒） |\r\n'
    u'| 和局判据 | 六十回合 / 长打 | 棋盘填满 | 五十回合 / 长打 |\r\n'
    u'| 桌子尺寸 | 3\u00d73 格（模型 3\u00d73） | **3\u00d73 格（模型只有 2\u00d72）** | 3\u00d73 格（模型 3\u00d73） |\r\n'
    u'\r\n'
    u'三块棋盘共用 `client/renderer/tileentity/BoardRenderUtil`：`applyFacing` 与\r\n'
    u'「终局提示文字」都只有一份。上一轮象棋「右侧棋子不在格线上 / 字是倒的」\r\n'
    u'就是这段姿态里少了一句半圈 —— 各写一遍就一定会有一块写错。\r\n'
    u'\r\n'
    u'#### 格线是量出来的，三块棋盘用的是同一条结论\r\n'
    u'\r\n'
    u'`port/board_grid_probe.py` 干的事和象棋那支 `chess_grid_probe.py` 一样：\r\n'
    u'把棋盘 up 面的 box-UV 矩形解出来，扫竖线与横线的位置，换算成方块坐标，\r\n'
    u'再和**点击判定**用的格点对照。两块新盘的结果：\r\n'
    u'\r\n'
    u'- **国际象棋**：`wchess.json` 的板子 `x/z \u00b11.5`（3 格）对应贴图 `u 48..96`；\r\n'
    u'  8\u00d78 的格子区从 `u 56` 开始、到 `u 88` 结束，正好换算成世界 `-1.0..1.0`。\r\n'
    u'  这与 `WChessUtil#getClickPosition` 的 `floor((x + 1) / 0.25)` 完全吻合\r\n'
    u'  （第 0 格从 -1 起、中心 -0.875，间距 0.25），所以**不需要任何手调偏移**。\r\n'
    u'- **五子棋**：格线不是贴图，而是单独一层 cube（`uv [605,0]`，151\u00d7151 texel 铺满 2 格）。\r\n'
    u'  按**不透明像素**数一遍，15 条线落在 rect 内第 5、15、25 … 145 个 texel 上 ——\r\n'
    u'  间距 10 texel、两端各留 5 texel、关于中心完全对称。折算就是\r\n'
    u'  `间距 = 2/151\u00d710 = 0.132450`、`第 0 条 = -1 + 2/151\u00d75.5 = -0.927152`，\r\n'
    u'  也就是 `util/GomokuUtil` 里那两个常数。\r\n'
    u'\r\n'
    u'**为什么不再沿用上游那九个偏移三元组**：上游 `BlockGomoku#getChessPos` 给九宫格\r\n'
    u'每一格各写了一套 `(xOffset, xStartOffset, xIndexOffset)`，彼此并不自洽 ——\r\n'
    u'同为第 4 号交叉点，从 `CENTER` 算出来是 0.115、从 `LEFT_CENTER` 算出来是 0.1014，\r\n'
    u'差 0.0136 格；累到盘面另一头差 **0.042 格 \u2248 0.65 像素**，而一颗棋子裸宽只有 1.5 像素。\r\n'
    u'更糟的是判定框只有 0.07 宽、还从格线往一侧偏，**近一半面积是点不到的死区**。\r\n'
    u'现在点击与渲染共用 `GomokuUtil` 的同一组常数，把点击点四舍五入到最近的交叉点即可。\r\n'
    u'\r\n'
    u'#### 竖直方向：`main` 与「整张桌子」不是一回事\r\n'
    u'\r\n'
    u'| 棋盘 | 竖直基准 | 表面（棋子贴在这个高度） | 实测 |\r\n'
    u'|---|---|---|---|\r\n'
    u'| 国际象棋 | 模型原点即底面，偏移 0 | `wchess.json` 的 `maxY` | 0.125 |\r\n'
    u'| 五子棋 | 偏移 0（桌腿埋在地下 \u22121.89） | **只量 `main` 骨骼**的 `maxY` | 0.125625 |\r\n'
    u'\r\n'
    u'五子棋这一条是坑：整张桌子的包围盒 `maxY` 是 **0.292**，因为两个棋盒（`blackBox` /\r\n'
    u'`whiteBox` 那 8+8 个 cube）比桌面高出一截。拿它当表面，所有棋子会浮在半空 ——\r\n'
    u'`measureBone(model, "main")` 量到的才是桌面 + 那层格线。\r\n'
    u'\r\n'
    u'棋子本身（`gomoku_piece.json` / `wchess_pieces.json`）同样靠 `measureBone` 量：\r\n'
    u'五子棋底面 `0.015625`、国际象棋底面 `0`，抬到表面高度即可；国际象棋额外乘上游\r\n'
    u'`WChessPiecesModel` 里的 `scale(0.9)`（棋子裸宽 0.2375 放进 0.25 的格子会顶满）。\r\n'
    u'\r\n'
    u'#### 两个防作弊上的差异\r\n'
    u'\r\n'
    u'- **五子棋回包是一个裸坐标**，上游直接 `setChessData(x, y, type)`。改过的客户端能把\r\n'
    u'  坐标写成 999（服务端 `ArrayIndexOutOfBounds`，方块实体的存档也被写脏）、能往已有子的\r\n'
    u'  点再放一颗、能用 `BLACK` 冒充玩家。`GomokuToServerPacket` 因此逐条验：坐标在\r\n'
    u'  15\u00d715 内、该点为空、颜色**强制**为白（女仆永远执白是规则，不是请求）。\r\n'
    u'- **`ZhiZhangAIService` 是有状态的**（局面 / 最佳点 / 回合数全在实例字段里）。\r\n'
    u'  上游把四档棋力写成四个 `static final` 单例，两个客户端同时开局会互相覆盖。\r\n'
    u'  `MaidGomokuAI#getService` 因此每次新建实例 —— 搜索本身是纯 CPU 的，代价可忽略。\r\n'
    u'\r\n'
    u'### 已知限制\r\n'
    u'\r\n'
    u'- **三块棋盘都只有玩法，没有「残局道具」**（`cchess_board_state` / `gomoku_board_state` /\r\n'
    u'  `wchess_board_state` 三个物品 + `GomokuCodec` + 木棍导出 / 预设）。三块共用一套，\r\n'
    u'  留到后面统一做。\r\n'
    u'- **手册里只有象棋一页**（`$registered` 只登记了 `touhou_little_maid:cchess`）。\r\n'
    u'  五子棋 / 国际象棋的条目文字在 lang 里已经齐了，差的是 `entries/` 下的页面与注册。\r\n')

count = raw.count(anchor)
if count != 1:
    sys.exit('limitation anchor found %d times' % count)
raw = raw.replace(anchor, new_block)
edits.append('limitation block')

io.open(PATH, 'w', encoding='utf-8', newline='').write(raw)
print('README updated: %d edits (%s)' % (len(edits), ', '.join(edits)))

