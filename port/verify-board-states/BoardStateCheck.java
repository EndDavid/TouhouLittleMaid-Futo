import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.GomokuCodec;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Point;

/**
 * 残局数据与引擎的离线自检：不启动游戏，直接编译真实的引擎与编解码器跑一遍。
 *
 * <p>它检查三件事，每一条都对应一处真实会踩的地方：</p>
 * <ol>
 *     <li><b>自带数据能读。</b>数据包里的 {@code board_states/*.json} 是手写的字符串，
 *         写错一个字符（base64 少一位、坐标漏了下划线）只会在「玩家开箱子时」才炸。
 *         这里逐条解码、再编码，要求与原串<b>逐字节相同</b> ——
 *         编码器只做确定性压缩，所以这是最强的校验。</li>
 *     <li><b>空棋盘也能来回。</b>用木棍在空棋盘上导出时，最后一手是 {@code Point.NULL}
 *         （坐标 -1、-1）。这一条专门验证那种串能编码、能解码，并且解码后仍然是 NULL
 *         —— 不然「导出没下过子的棋盘 = 一个导入就崩的串」。</li>
 *     <li><b>排残局的过程也是可逆的。</b>模拟木棍落子（往空点放一颗、换手、更新最后一手），
 *         再导出、再导入，要求棋盘、回合数、最后一手三者都对得上。</li>
 * </ol>
 *
 * <p>另外顺带把两种象棋的全部自带 FEN 也过一遍��擎：能从 FEN 解析、{@code toFen} 稳定、
 * 并且真的存在合法走法（不是「一读进来就是将死 / 无子」的坏数据）。</p>
 *
 * <p>运行方式见同目录的 {@code run-board-state-check.ps1}。</p>
 */
public final class BoardStateCheck {
    private static final String[] GOMOKU = BoardStateData.GOMOKU;
    private static final String[] XIANGQI = BoardStateData.XIANGQI;
    private static final String[] CHESS = BoardStateData.CHESS;

    public static void main(String[] args) {
        int bad = 0;

        // ---- 1. 自带数据：解码 -> 编码必须逐字节相同 ----
        for (int i = 0; i < GOMOKU.length; i++) {
            try {
                GomokuCodec.StateData state = GomokuCodec.decode(GOMOKU[i]);
                String again = GomokuCodec.encode(state);
                boolean same = again.equals(GOMOKU[i]);
                System.out.println("gomoku[" + i + "] turns=" + state.turnCount()
                        + " last=(" + state.latestPoint().x + "," + state.latestPoint().y + ")"
                        + " roundtrip=" + same);
                if (!same) {
                    System.out.println("    re-encoded: " + again);
                    bad++;
                }
            } catch (Exception e) {
                System.out.println("gomoku[" + i + "] FAILED: " + e);
                bad++;
            }
        }

        // ---- 2. 空棋盘 + Point.NULL ----
        try {
            int size = 15;
            int[][] empty = new int[size][size];
            String encoded = GomokuCodec.encode(new GomokuCodec.StateData(empty, 0, Point.NULL));
            GomokuCodec.StateData back = GomokuCodec.decode(encoded);
            boolean emptyOk = true;
            for (int[] row : back.board()) {
                for (int v : row) {
                    if (v != Point.EMPTY) {
                        emptyOk = false;
                    }
                }
            }
            boolean nullOk = back.latestPoint() == Point.NULL
                    || (back.latestPoint().x < 0 && back.latestPoint().y < 0);
            System.out.println("empty board: encoded=" + encoded
                    + " turns=" + back.turnCount()
                    + " empty=" + emptyOk + " latest-null=" + nullOk);
            if (!emptyOk || !nullOk || back.turnCount() != 0) {
                bad++;
            }
        } catch (Exception e) {
            System.out.println("empty board FAILED: " + e);
            bad++;
        }

        // ---- 3. 排残局：落子 -> 导出 -> 导入，三者要一致 ----
        for (int i = 0; i < GOMOKU.length; i++) {
            try {
                GomokuCodec.StateData state = GomokuCodec.decode(GOMOKU[i]);
                int[][] board = state.board();
                int[] target = firstEmpty(board);
                if (target == null) {
                    continue;
                }
                int turn = state.turnCount();
                // 木棍的规则：落一颗「轮到谁的颜色」，然后把回合翻过去
                int colour = (turn % 2 == 0) ? Point.BLACK : Point.WHITE;
                board[target[0]][target[1]] = colour;
                GomokuCodec.StateData edited =
                        new GomokuCodec.StateData(board, turn + 1, new Point(target[0], target[1], colour));
                GomokuCodec.StateData back = GomokuCodec.decode(GomokuCodec.encode(edited));
                boolean ok = back.turnCount() == turn + 1
                        && back.board()[target[0]][target[1]] == colour
                        && back.latestPoint().x == target[0]
                        && back.latestPoint().y == target[1];
                System.out.println("author gomoku[" + i + "] at (" + target[0] + "," + target[1] + ") ok=" + ok);
                if (!ok) {
                    bad++;
                }
            } catch (Exception e) {
                System.out.println("author gomoku[" + i + "] FAILED: " + e);
                bad++;
            }
        }

        // ---- 4. 两种象棋：FEN 能解析、稳定、且不是死局 ----
        for (int i = 0; i < XIANGQI.length; i++) {
            try {
                com.github.tartaricacid.touhoulittlemaid.api.game.xqwlight.Position position =
                        new com.github.tartaricacid.touhoulittlemaid.api.game.xqwlight.Position();
                position.fromFen(XIANGQI[i]);
                String fen = position.toFen();
                com.github.tartaricacid.touhoulittlemaid.api.game.xqwlight.Position again =
                        new com.github.tartaricacid.touhoulittlemaid.api.game.xqwlight.Position();
                again.fromFen(fen);
                int[] moves = new int[64];
                int count = position.generateAllMoves(moves);
                boolean stable = again.toFen().equals(fen);
                boolean sideOk = fen.endsWith(" w") == XIANGQI[i].contains(" w ");
                System.out.println("xiangqi[" + i + "] moves=" + count + " mate=" + position.isMate()
                        + " stable=" + stable + " side-ok=" + sideOk);
                if (count <= 0 || !stable || !sideOk) {
                    bad++;
                }
            } catch (Exception e) {
                System.out.println("xiangqi[" + i + "] FAILED: " + e);
                bad++;
            }
        }
        for (int i = 0; i < CHESS.length; i++) {
            try {
                com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position position =
                        new com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position();
                position.fromFen(CHESS[i]);
                String fen = position.toFen();
                com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position again =
                        new com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position();
                again.fromFen(fen);
                int[] moves = new int[128];
                int count = position.generateAllMoves(moves);
                boolean stable = again.toFen().equals(fen);
                boolean sideOk = fen.contains(" w ") == CHESS[i].contains(" w ");
                System.out.println("chess[" + i + "] moves=" + count + " mate=" + position.isMate()
                        + " stable=" + stable + " side-ok=" + sideOk);
                if (count <= 0 || !stable || !sideOk) {
                    bad++;
                }
            } catch (Exception e) {
                System.out.println("chess[" + i + "] FAILED: " + e);
                bad++;
            }
        }

        System.out.println(bad == 0 ? "ALL OK" : ("BAD=" + bad));
        if (bad != 0) {
            System.exit(1);
        }
    }

    private static int[] firstEmpty(int[][] board) {
        for (int x = 0; x < board.length; x++) {
            for (int y = 0; y < board[x].length; y++) {
                if (board[x][y] == Point.EMPTY) {
                    return new int[]{x, y};
                }
            }
        }
        return null;
    }
}

