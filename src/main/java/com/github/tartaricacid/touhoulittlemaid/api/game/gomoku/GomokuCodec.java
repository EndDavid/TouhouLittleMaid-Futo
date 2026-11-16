package com.github.tartaricacid.touhoulittlemaid.api.game.gomoku;

import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * 五子棋棋局的文本编码，移植自上游 1.20.1 的 {@code api/game/gomoku/GomokuCodec}。
 *
 * <p>残局道具（{@code gomoku_board_state}）的 NBT 里存不下 {@code int[15][15]}，
 * 所以压成一行字符串再放进物品。格式：</p>
 *
 * <pre>
 *     &lt;base64&gt;, &lt;turnCount&gt;, &lt;x&gt;_&lt;y&gt;
 * </pre>
 *
 * <ol>
 *     <li>棋盘是两张 15×15 的<b>位图</b>（先黑后白，行优先）拼成 450 字节；</li>
 *     <li>对这 450 字节做 Deflate 压缩，再做 URL 安全的 Base64（无填充）编码；</li>
 *     <li>后面跟上回合数与「最后一手」的坐标。</li>
 * </ol>
 *
 * <p>压缩是必要的：{@code int[15][15]} 直接写会有一大堆重复的 0，Deflate 之后
 * 一般只剩几十字节，一行 tooltip 就能放下。</p>
 *
 * <h2>为什么解码要这么小心</h2>
 * <p>这段代码的输入来自<b>数据包写的 JSON</b> 与<b>玩家手里的物品 NBT</b> —— 都不是可信数据。
 * 上游解出来的数组长度、坐标都没有校验，本移植版把边界补齐：坐标越界时退回
 * {@link Point#NULL}，解压长度不对时抛 {@link IllegalArgumentException}
 * （调用方 {@code ClientBoardStateTooltip} 会捕获并当作「没有棋局」处理）。</p>
 */
public final class GomokuCodec {
    private static final int SIZE = 15;
    private static final int BOARD_BYTES = 2 * SIZE * SIZE;

    private GomokuCodec() {
    }

    public static String encode(StateData stateData) {
        int[][] board = stateData.board();
        int turnCount = stateData.turnCount();
        Point latestPoint = stateData.latestPoint();

        // 将棋盘数据编码为位图
        byte[] data = new byte[BOARD_BYTES];
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                int type = board[i][j];
                int pos = i * SIZE + j;
                if (type == Point.BLACK) {
                    data[pos] = 1;
                } else if (type == Point.WHITE) {
                    data[SIZE * SIZE + pos] = 1;
                }
            }
        }

        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, false);
        deflater.setInput(data);
        deflater.finish();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            while (!deflater.finished()) {
                int len = deflater.deflate(buf);
                if (len > 0) {
                    out.write(buf, 0, len);
                }
            }
            byte[] compressed = out.toByteArray();
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(compressed);
            return payload + ", " + turnCount + ", " + latestPoint.x + "_" + latestPoint.y;
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode gomoku state", e);
        } finally {
            deflater.end();
        }
    }

    public static StateData decode(String dataStr) {
        // 去掉空白方便解析
        String clean = StringUtils.deleteWhitespace(dataStr);
        String[] parts = StringUtils.splitPreserveAllTokens(clean, ',');
        if (parts.length != 3) {
            throw new IllegalArgumentException("Data must be in format <base64>, turnCount, x_y");
        }

        byte[] compressed = Base64.getUrlDecoder().decode(parts[0]);
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        byte[] data = new byte[BOARD_BYTES];
        try {
            int resultLength = inflater.inflate(data);
            if (resultLength != data.length) {
                throw new IllegalArgumentException("Decompressed data length mismatch");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decompress data", e);
        } finally {
            inflater.end();
        }

        int[][] board = new int[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                int pos = i * SIZE + j;
                if (data[pos] == 1) {
                    board[i][j] = Point.BLACK;
                } else if (data[SIZE * SIZE + pos] == 1) {
                    board[i][j] = Point.WHITE;
                } else {
                    board[i][j] = Point.EMPTY;
                }
            }
        }

        int turnCount = Integer.parseInt(parts[1]);
        String[] pointParts = parts[2].split("_");
        if (pointParts.length != 2) {
            throw new IllegalArgumentException("Latest point must be in format x_y");
        }
        int x = Integer.parseInt(pointParts[0]);
        int y = Integer.parseInt(pointParts[1]);
        // 越界的坐标退回 NULL，而不是让调用方拿着 -1 去索引数组
        boolean inBoard = 0 <= x && x < SIZE && 0 <= y && y < SIZE;
        Point latestPoint = inBoard ? new Point(x, y, board[x][y]) : Point.NULL;

        return new StateData(board, turnCount, latestPoint);
    }

    /**
     * 解码结果：棋盘、回合数、最后一手。
     */
    public record StateData(int[][] board, int turnCount, Point latestPoint) {
    }
}

