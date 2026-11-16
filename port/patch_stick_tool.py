# -*- coding: utf-8 -*-
"""Port the creative "stick" authoring tool for the gomoku board.

Upstream 1.20.1 has it in BlockGomoku#onCreativePlayerClick, and *only* there:

    gohei  -> fill the whole board, leaving three empty points (debug)
    stick  -> click a stone box   -> print the encoded game into chat, click to copy
              click an intersection -> place a stone of the side whose turn it is,
                                        then flip the turn so the next click is
                                        the other colour

There is no stick tool for the two chess boards upstream.  That is not an
oversight -- a chess position is a FEN string you can type by hand, while the
gomoku format is deflate+base64, so only gomoku needs an exporter.  This port
keeps that asymmetry rather than inventing a second tool.

1.18.2 differences handled here:

  * `ComponentUtils.copyOnClickText` is 1.19+; it is rebuilt below from
    Style#withClickEvent + withHoverEvent + withInsertion.  The click action
    COPY_TO_CLIPBOARD is allowed from the server side in 1.18.2 (verified by
    reading the ClickEvent$Action enum initialiser: iconst_1), which is what
    makes "click the message to copy" work at all.
  * `Player#sendSystemMessage` is 1.20+; 1.18.2 spells the same thing
    `displayClientMessage(x, false)` (actionBar=false -> SYSTEM chat).
  * the click-to-cell maths is ours (`GomokuUtil#getGridPoint`), not upstream's
    nine offset triples, for the reasons documented in that class.
"""
import io
import sys

BASE = 'src/main/java/com/github/tartaricacid/touhoulittlemaid/'
EDITS = []


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
    EDITS.append(path)


# ------------------------------------------------------------ TileEntityGomoku
patch(BASE + 'tileentity/TileEntityGomoku.java', [
    ("    /**\n"
     "     * 载入一个残局，对应上游 {@code TileEntityGomoku#setStateData}。\n",
     "    /**\n"
     "     * 把当前棋局打包成可导出的形式（棋盘 + 回合数 + 最后一手）。\n"
     "     *\n"
     "     * <p>对应上游 {@code TileEntityGomoku#getStateData}。它和 {@link #setStateData}\n"
     "     * 是一对：木棍导出走这里，残局道具导入走那边。</p>\n"
     "     *\n"
     "     * <p><b>注意不要传引用出去。</b>{@code chessData} 是这张棋盘自己的数组，\n"
     "     * {@code GomokuCodec#encode} 只读它，拿到的是拷贝出来的字符串，所以这里直接交出数组是安全的；\n"
     "     * 但反过来 {@link #setStateData} 必须<b>接管</b>传进来的数组（上游也是这么做的），\n"
     "     * 因为解码结果本来就是一次性对象。</p>\n"
     "     */\n"
     "    public GomokuCodec.StateData getStateData() {\n"
     "        return new GomokuCodec.StateData(this.chessData, this.chessCounter, this.latestChessPoint);\n"
     "    }\n"
     "\n"
     "    /**\n"
     "     * 载入一个残局，对应上游 {@code TileEntityGomoku#setStateData}。\n"),
])

# ----------------------------------------------------------------- BlockGomoku
patch(BASE + 'block/BlockGomoku.java', [
    # imports: chat styles for the copy-to-clipboard message, plus stick / codec
    ("import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Point;\n",
     "import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.GomokuCodec;\n"
     "import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Point;\n"),
    ("import net.minecraft.network.chat.TranslatableComponent;\n",
     "import net.minecraft.network.chat.ClickEvent;\n"
     "import net.minecraft.network.chat.ComponentUtils;\n"
     "import net.minecraft.network.chat.HoverEvent;\n"
     "import net.minecraft.network.chat.MutableComponent;\n"
     "import net.minecraft.network.chat.Style;\n"
     "import net.minecraft.network.chat.TextComponent;\n"
     "import net.minecraft.network.chat.TranslatableComponent;\n"),
    ("import net.minecraft.world.item.ItemStack;\n",
     "import net.minecraft.world.item.ItemStack;\n"
     "import net.minecraft.world.item.Items;\n"),
    # use(): hand the creative-only clicks to a helper, exactly like upstream
    ("        // 创造模式下拿御币点一下 = 铺满棋盘只留三个空位（调试 AI 用）\n"
     "        Item held = player.getMainHandItem().getItem();\n"
     "        if (player.getAbilities().instabuild && held instanceof ItemHakureiGohei) {\n"
     "            gomoku.clickWithDebug();\n"
     "            gomoku.refresh();\n"
     "            level.playSound(null, centerPos, InitSounds.GOMOKU_RESET, SoundSource.BLOCKS, 1.0f, 1.0f);\n"
     "            return InteractionResult.SUCCESS;\n"
     "        }\n",
     "        // 创造模式的两个额外用法（御币铺满 / 木棍预设与导出）。\n"
     "        // 上游把这两件事收在一个方法里，返回 null 表示「我不管，继续往下走」。\n"
     "        if (player.getAbilities().instabuild) {\n"
     "            InteractionResult creative = this.onCreativePlayerClick(level, pos, player, gomoku,\n"
     "                    centerPos, location, part, facing);\n"
     "            if (creative != null) {\n"
     "                return creative;\n"
     "            }\n"
     "        }\n"),
    # the helper itself, placed right before sitPosition()
    ("    @Override\n"
     "    protected Vec3 sitPosition() {\n",
     "    /**\n"
     "     * 创造模式下的两种额外用法，移植自上游 {@code BlockGomoku#onCreativePlayerClick}。\n"
     "     *\n"
     "     * <p>返回 {@code null} 表示「这两个工具我都不认识」，让 {@link #use} 继续按正常流程走 ——\n"
     "     * 上游用同一个约定，别改成返回 {@code PASS}，那样会把后面的残局道具分支挡掉。</p>\n"
     "     *\n"
     "     * <h2>为什么只有五子棋有木棍工具</h2>\n"
     "     * <p>上游就是这样：两张象棋棋盘没有这个分支。原因也很实在 —— 象棋的局面是一个 FEN，\n"
     "     * 手写就行；五子棋的格式是 deflate + base64，人不可能手搓，才需要一个导出器。</p>\n"
     "     */\n"
     "    @Nullable\n"
     "    private InteractionResult onCreativePlayerClick(Level level, BlockPos pos, Player player,\n"
     "                                                    TileEntityGomoku gomoku, BlockPos centerPos,\n"
     "                                                    Vec3 location, GomokuPart part, Direction facing) {\n"
     "        Item item = player.getMainHandItem().getItem();\n"
     "\n"
     "        // 御币：铺满棋盘只留三个空位，方便调试 AI 的收尾判断\n"
     "        if (item instanceof ItemHakureiGohei) {\n"
     "            gomoku.clickWithDebug();\n"
     "            gomoku.refresh();\n"
     "            level.playSound(null, centerPos, InitSounds.GOMOKU_RESET, SoundSource.BLOCKS, 1.0f, 1.0f);\n"
     "            return InteractionResult.SUCCESS;\n"
     "        }\n"
     "\n"
     "        if (item != Items.STICK) {\n"
     "            return null;\n"
     "        }\n"
     "\n"
     "        // 点棋盒 = 导出当前棋局（点聊天栏里那条消息就能复制，再贴进数据包的 board_states/*.json）\n"
     "        if (isClickChessBox(location.x, location.z, part, facing)) {\n"
     "            String result = GomokuCodec.encode(gomoku.getStateData());\n"
     "            // 1.20 的 ComponentUtils#copyOnClickText：点一下就把字符串塞进剪贴板。\n"
     "            // 1.18.2 没有这个工厂，这里用同样的三件套拼出来（点击 / 悬停提示 / 插入串）。\n"
     "            MutableComponent copyable = ComponentUtils.mergeStyles(new TextComponent(result),\n"
     "                    Style.EMPTY\n"
     "                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, result))\n"
     "                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,\n"
     "                                    new TranslatableComponent(\"chat.copy.click\")))\n"
     "                            .withInsertion(result));\n"
     "            // 1.18.2 的对应物：displayClientMessage(x, false) 就是 SYSTEM 聊天栏（actionBar=false）。\n"
     "            player.displayClientMessage(copyable, false);\n"
     "            return InteractionResult.SUCCESS;\n"
     "        }\n"
     "\n"
     "        // 点交叉点 = 落一颗「轮到谁的颜色」，并把回合翻给对面，于是下一次点击是另一种颜色。\n"
     "        // 这是排残局用的直接编辑：落在已有棋子上是【覆盖】（上游同款），不检查该点是否为空。\n"
     "        int[] grid = GomokuUtil.getGridPoint(part, location.x, location.z);\n"
     "        if (grid == null) {\n"
     "            return InteractionResult.FAIL;\n"
     "        }\n"
     "        int type = gomoku.isPlayerTurn() ? Point.BLACK : Point.WHITE;\n"
     "        gomoku.setChessData(grid[0], grid[1], type);\n"
     "        level.playSound(null, pos, InitSounds.GOMOKU, SoundSource.BLOCKS,\n"
     "                1.0f, 0.8F + level.random.nextFloat() * 0.4F);\n"
     "        gomoku.setPlayerTurn(!gomoku.isPlayerTurn());\n"
     "        gomoku.refresh();\n"
     "        return InteractionResult.SUCCESS;\n"
     "    }\n"
     "\n"
     "    @Override\n"
     "    protected Vec3 sitPosition() {\n"),
])

print('patched %d file(s):' % len(EDITS))
for e in EDITS:
    print('  ' + e)

