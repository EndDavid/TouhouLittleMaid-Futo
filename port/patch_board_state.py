# -*- coding: utf-8 -*-
"""Wire the endgame items into the three boards, and add the TE setters.

Upstream 1.20.1 applies an endgame *before* the "empty hand only" gate, and
returns SUCCESS right away.  Two adaptations are needed here:

  * `ItemBoardState.getState` can return a malformed string (the item NBT is
    player-writable), and `Position#fromFen` / `GomokuCodec#decode` throw on
    that.  Upstream lets the exception escape; on a server that is a crash in
    the middle of a block interaction, so it is caught and turned into a
    chat message instead.
  * The win/draw flags (`checkmate` / `repeat` / `moveNumberLimit`) and the
    "whose turn" flag are *not* part of the position string, so applying an
    endgame to a table that already finished a game would leave it
    unplayable.  They are cleared when an endgame is loaded.
"""
import io
import sys

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


BASE = 'src/main/java/com/github/tartaricacid/touhoulittlemaid/'

# --------------------------------------------------------------- TileEntityCChess
patch(BASE + 'tileentity/TileEntityCChess.java', [
    ("    public void reset() {\n"
     "        this.chessCounter = 0;\n",
     "    /**\n"
     "     * \u8f7d\u5165\u4e00\u4e2a\u6b8b\u5c40\uff08FEN\uff09\uff0c\u5bf9\u5e94\u4e0a\u6e38 {@code TileEntityCChess#setEndgame}\u3002\n"
     "     *\n"
     "     * <p>\u4e0e\u4e0a\u6e38\u7684\u5dee\u5f02\uff1a\u8fd9\u91cc\u628a\u4e09\u4e2a\u7ec8\u5c40\u6807\u5fd7\u4e00\u5e76\u6e05\u6389\u3002\u5b83\u4eec\u4e0d\u5c5e\u4e8e FEN\uff0c\n"
     "     * \u800c\u300c\u5df2\u7ecf\u5224\u8f93\u8d62\u300d\u7684\u68cb\u76d8\u518d\u8f7d\u5165\u6b8b\u5c40\u4f1a\u56e0\u4e3a\u6807\u5fd7\u6ca1\u6e05\u800c\u6c38\u8fdc\u4e0d\u80fd\u4e0b\u3002</p>\n"
     "     */\n"
     "    public void setEndgame(String fen) {\n"
     "        this.chessCounter = 0;\n"
     "        this.selectChessPoint = 0;\n"
     "        this.chessData.fromFen(fen);\n"
     "        this.checkmate = false;\n"
     "        this.repeat = false;\n"
     "        this.moveNumberLimit = false;\n"
     "        this.refresh();\n"
     "    }\n"
     "\n"
     "    public void reset() {\n"
     "        this.chessCounter = 0;\n"),
])

# --------------------------------------------------------------- TileEntityWChess
patch(BASE + 'tileentity/TileEntityWChess.java', [
    ("    public void reset() {\n"
     "        this.chessCounter = 0;\n",
     "    /**\n"
     "     * \u8f7d\u5165\u4e00\u4e2a\u6b8b\u5c40\uff08FEN\uff09\uff0c\u5bf9\u5e94\u4e0a\u6e38 {@code TileEntityWChess} \u5bf9\u5e94\u65b9\u6cd5\u3002\n"
     "     * \u7ec8\u5c40\u6807\u5fd7\u4e00\u5e76\u6e05\u6389\u7684\u7406\u7531\u540c {@code TileEntityCChess#setEndgame}\u3002\n"
     "     */\n"
     "    public void setEndgame(String fen) {\n"
     "        this.chessCounter = 0;\n"
     "        this.selectChessPoint = 0;\n"
     "        this.chessData.fromFen(fen);\n"
     "        this.checkmate = false;\n"
     "        this.repeat = false;\n"
     "        this.moveNumberLimit = false;\n"
     "        this.refresh();\n"
     "    }\n"
     "\n"
     "    public void reset() {\n"
     "        this.chessCounter = 0;\n"),
])

# --------------------------------------------------------------- TileEntityGomoku
patch(BASE + 'tileentity/TileEntityGomoku.java', [
    ("    public void reset() {\n"
     "        this.chessData = new int[GomokuUtil.GRID_SIZE][GomokuUtil.GRID_SIZE];\n",
     "    /**\n"
     "     * \u8f7d\u5165\u4e00\u4e2a\u6b8b\u5c40\uff0c\u5bf9\u5e94\u4e0a\u6e38 {@code TileEntityGomoku#setStateData}\u3002\n"
     "     *\n"
     "     * <p>\u4e94\u5b50\u68cb\u7684\u6b8b\u5c40\u6570\u636e\u91cc\u53ea\u6709\u68cb\u76d8\u3001\u56de\u5408\u6570\u4e0e\u6700\u540e\u4e00\u624b\uff0c\u5e76\u4e0d\u8bb0\u5f55\n"
     "     * \u300c\u8be5\u8c01\u4e0b\u300d\uff0c\u6240\u4ee5\u8fd9\u91cc\u628a\u56de\u5408\u4ea4\u7ed9\u73a9\u5bb6\uff1b\u5426\u5219\u4e0a\u4e00\u5c40\u5076\u7136\u505c\u5728\u5973\u4ec6\u56de\u5408\uff0c\n"
     "     * \u6362\u4e0a\u6b8b\u5c40\u540e\u5c31\u6c38\u8fdc\u70b9\u4e0d\u52a8\u3002</p>\n"
     "     */\n"
     "    public void setStateData(GomokuCodec.StateData stateData) {\n"
     "        this.chessData = stateData.board();\n"
     "        this.chessCounter = stateData.turnCount();\n"
     "        this.latestChessPoint = stateData.latestPoint();\n"
     "        this.statue = Statue.IN_PROGRESS.ordinal();\n"
     "        this.playerTurn = true;\n"
     "        this.refresh();\n"
     "    }\n"
     "\n"
     "    public void reset() {\n"
     "        this.chessData = new int[GomokuUtil.GRID_SIZE][GomokuUtil.GRID_SIZE];\n"),
    ("import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Point;\n",
     "import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.GomokuCodec;\n"
     "import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Point;\n"),
])

# --------------------------------------------------------------- BlockCChess
patch(BASE + 'block/BlockCChess.java', [
    ("        if (!(level instanceof ServerLevel serverLevel) || hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) {\n"
     "            return InteractionResult.PASS;\n"
     "        }\n"
     "        GomokuPart part = state.getValue(PART);\n"
     "        BlockPos centerPos = pos.subtract(new Vec3i(part.getPosX(), 0, part.getPosY()));\n"
     "\n"
     "        if (!(level.getBlockEntity(centerPos) instanceof TileEntityCChess chess)) {\n"
     "            return InteractionResult.FAIL;\n"
     "        }\n",
     "        if (!(level instanceof ServerLevel serverLevel) || hand != InteractionHand.MAIN_HAND) {\n"
     "            return InteractionResult.PASS;\n"
     "        }\n"
     "        GomokuPart part = state.getValue(PART);\n"
     "        BlockPos centerPos = pos.subtract(new Vec3i(part.getPosX(), 0, part.getPosY()));\n"
     "\n"
     "        if (!(level.getBlockEntity(centerPos) instanceof TileEntityCChess chess)) {\n"
     "            return InteractionResult.FAIL;\n"
     "        }\n"
     "\n"
     "        // \u5982\u679c\u624b\u91cc\u62ff\u7684\u662f\u6b8b\u5c40\u9053\u5177\uff0c\u90a3\u4e48\u76f4\u63a5\u628a\u68cb\u5c40\u6362\u6210\u5b83\u8bb0\u5f55\u7684\u90a3\u4e00\u5c40\uff08\u4e0a\u6e38\u540c\u6b3e\uff0c\n"
     "        // \u5e76\u4e14\u8fd9\u4e00\u6b65\u5728\u300c\u53ea\u80fd\u7a7a\u624b\u4e0b\u68cb\u300d\u4e4b\u524d\uff09\u3002\n"
     "        if (player.getMainHandItem().is(InitItems.CCHESS_BOARD_STATE)) {\n"
     "            String data = ItemBoardState.getState(player.getMainHandItem()) == null\n"
     "                    ? null : ItemBoardState.getState(player.getMainHandItem())[0];\n"
     "            if (data == null || data.isEmpty()) {\n"
     "                return InteractionResult.PASS;\n"
     "            }\n"
     "            try {\n"
     "                chess.setEndgame(data);\n"
     "            } catch (Exception e) {\n"
     "                // \u7269\u54c1 NBT \u662f\u73a9\u5bb6\u53ef\u5199\u7684\uff0c\u574f\u7684 FEN \u4e0d\u80fd\u8ba9\u670d\u52a1\u7aef\u5d29\u5728\u65b9\u5757\u4ea4\u4e92\u91cc\n"
     "                player.displayClientMessage(new net.minecraft.network.chat.TranslatableComponent(\n"
     "                        \"message.touhou_little_maid.board_state.bad_data\"), false);\n"
     "                return InteractionResult.FAIL;\n"
     "            }\n"
     "            level.playSound(null, centerPos, InitSounds.GOMOKU_RESET, SoundSource.BLOCKS, 1.0f, 1.0f);\n"
     "            return InteractionResult.SUCCESS;\n"
     "        }\n"
     "\n"
     "        // \u4e0b\u68cb\u5fc5\u987b\u7a7a\u624b\n"
     "        if (!player.getMainHandItem().isEmpty()) {\n"
     "            return InteractionResult.PASS;\n"
     "        }\n"),
    ("import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;\n",
     "import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;\n"
     "import com.github.tartaricacid.touhoulittlemaid.item.ItemBoardState;\n"),
])

# --------------------------------------------------------------- BlockWChess
patch(BASE + 'block/BlockWChess.java', [
    ("        if (!(level instanceof ServerLevel serverLevel) || hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) {\n"
     "            return InteractionResult.PASS;\n"
     "        }\n"
     "        GomokuPart part = state.getValue(PART);\n"
     "        BlockPos centerPos = pos.subtract(new Vec3i(part.getPosX(), 0, part.getPosY()));\n"
     "\n"
     "        if (!(level.getBlockEntity(centerPos) instanceof TileEntityWChess chess)) {\n"
     "            return InteractionResult.FAIL;\n"
     "        }\n",
     "        if (!(level instanceof ServerLevel serverLevel) || hand != InteractionHand.MAIN_HAND) {\n"
     "            return InteractionResult.PASS;\n"
     "        }\n"
     "        GomokuPart part = state.getValue(PART);\n"
     "        BlockPos centerPos = pos.subtract(new Vec3i(part.getPosX(), 0, part.getPosY()));\n"
     "\n"
     "        if (!(level.getBlockEntity(centerPos) instanceof TileEntityWChess chess)) {\n"
     "            return InteractionResult.FAIL;\n"
     "        }\n"
     "\n"
     "        // \u6b8b\u5c40\u9053\u5177\uff1a\u628a\u68cb\u5c40\u6362\u6210\u5b83\u8bb0\u5f55\u7684\u90a3\u4e00\u5c40\uff08\u4e0a\u6e38\u540c\u6b3e\uff0c\u5728\u300c\u53ea\u80fd\u7a7a\u624b\u300d\u4e4b\u524d\uff09\n"
     "        if (player.getMainHandItem().is(InitItems.WCHESS_BOARD_STATE)) {\n"
     "            String data = ItemBoardState.getState(player.getMainHandItem()) == null\n"
     "                    ? null : ItemBoardState.getState(player.getMainHandItem())[0];\n"
     "            if (data == null || data.isEmpty()) {\n"
     "                return InteractionResult.PASS;\n"
     "            }\n"
     "            try {\n"
     "                chess.setEndgame(data);\n"
     "            } catch (Exception e) {\n"
     "                player.displayClientMessage(new TranslatableComponent(\n"
     "                        \"message.touhou_little_maid.board_state.bad_data\"), false);\n"
     "                return InteractionResult.FAIL;\n"
     "            }\n"
     "            level.playSound(null, centerPos, InitSounds.GOMOKU_RESET, SoundSource.BLOCKS, 1.0f, 1.0f);\n"
     "            return InteractionResult.SUCCESS;\n"
     "        }\n"
     "\n"
     "        // \u4e0b\u68cb\u5fc5\u987b\u7a7a\u624b\n"
     "        if (!player.getMainHandItem().isEmpty()) {\n"
     "            return InteractionResult.PASS;\n"
     "        }\n"),
    ("import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;\n",
     "import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;\n"
     "import com.github.tartaricacid.touhoulittlemaid.item.ItemBoardState;\n"),
])

# --------------------------------------------------------------- BlockGomoku
patch(BASE + 'block/BlockGomoku.java', [
    ("        // \u4e0b\u68cb\u5fc5\u987b\u7a7a\u624b\n"
     "        if (!player.getMainHandItem().isEmpty()) {\n"
     "            return InteractionResult.PASS;\n"
     "        }\n",
     "        // \u6b8b\u5c40\u9053\u5177\uff1a\u628a\u68cb\u5c40\u6362\u6210\u5b83\u8bb0\u5f55\u7684\u90a3\u4e00\u5c40\uff08\u4e0a\u6e38\u540c\u6b3e\uff0c\u5728\u300c\u53ea\u80fd\u7a7a\u624b\u300d\u4e4b\u524d\uff09\n"
     "        if (player.getMainHandItem().is(InitItems.GOMOKU_BOARD_STATE)) {\n"
     "            String data = ItemBoardState.getState(player.getMainHandItem()) == null\n"
     "                    ? null : ItemBoardState.getState(player.getMainHandItem())[0];\n"
     "            if (data == null || data.isEmpty()) {\n"
     "                return InteractionResult.PASS;\n"
     "            }\n"
     "            try {\n"
     "                gomoku.setStateData(com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.GomokuCodec\n"
     "                        .decode(data));\n"
     "            } catch (Exception e) {\n"
     "                player.displayClientMessage(new TranslatableComponent(\n"
     "                        \"message.touhou_little_maid.board_state.bad_data\"), false);\n"
     "                return InteractionResult.FAIL;\n"
     "            }\n"
     "            level.playSound(null, centerPos, InitSounds.GOMOKU_RESET, SoundSource.BLOCKS, 1.0f, 1.0f);\n"
     "            return InteractionResult.SUCCESS;\n"
     "        }\n"
     "\n"
     "        // \u4e0b\u68cb\u5fc5\u987b\u7a7a\u624b\n"
     "        if (!player.getMainHandItem().isEmpty()) {\n"
     "            return InteractionResult.PASS;\n"
     "        }\n"),
    ("import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;\n",
     "import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;\n"
     "import com.github.tartaricacid.touhoulittlemaid.item.ItemBoardState;\n"),
])

print('patched %d files' % len(EDITS))
for e in EDITS:
    print('  ' + e)

