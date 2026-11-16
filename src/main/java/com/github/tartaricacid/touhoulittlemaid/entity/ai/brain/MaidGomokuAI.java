package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain;

import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.AIService;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Point;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Statue;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.ZhiZhangAIService;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

public final class MaidGomokuAI {
    /**
     * 四档棋力的搜索配置。{@code new AIConfig(深度, 最大节点数, debug, 算杀模式, 算杀深度)}。
     */
    private static final AIService.AIConfig EASY_CONFIG = new AIService.AIConfig(1, 10, false, 0, 6);
    private static final AIService.AIConfig NORMAL_CONFIG = new AIService.AIConfig(4, 10, false, 0, 6);
    private static final AIService.AIConfig HARD_CONFIG = new AIService.AIConfig(6, 10, false, 1, 8);
    private static final AIService.AIConfig HELL_CONFIG = new AIService.AIConfig(8, 10, false, 1, 10);

    public static final int EASY_COUNT = 2;
    public static final int NORMAL_COUNT = 8;
    public static final int HARD_COUNT = 24;

    public static int getRank(EntityMaid maid) {
        int maidCount = maid.getGameRecordManager().getGomokuWinCount();
        if (maidCount <= EASY_COUNT) {
            return 1;
        } else if (maidCount <= NORMAL_COUNT) {
            return 2;
        } else if (maidCount <= HARD_COUNT) {
            return 3;
        } else {
            return 4;
        }
    }

    /**
     * 按女仆胜场数取一档棋力。
     *
     * <p><b>与上游的唯一差异：每次都新建一个实例。</b>上游把四档写成
     * {@code public static final AIService EASY = new ZhiZhangAIService(...)} 四个单例，
     * 而 {@code ZhiZhangAIService} 把「当前局面 / 最佳点 / 回合数」全存在<b>实例字段</b>里 ——
     * 两个客户端同时开局（或者一个客户端连开两盘）就会互相覆盖，
     * 表现是女仆偶尔下出完全不属于当前棋局的落点。搜索本身是纯 CPU 的，
     * 建实例的代价可以忽略。</p>
     */
    public static AIService getService(int winCount) {
        if (winCount <= EASY_COUNT) {
            return new ZhiZhangAIService(EASY_CONFIG);
        } else if (winCount <= NORMAL_COUNT) {
            return new ZhiZhangAIService(NORMAL_CONFIG);
        } else if (winCount <= HARD_COUNT) {
            return new ZhiZhangAIService(HARD_CONFIG);
        } else {
            return new ZhiZhangAIService(HELL_CONFIG);
        }
    }

    /**
     * 获取棋局状态
     */
    public static Statue getStatue(int[][] chessData, Point point) {
        int rows = chessData[0].length;
        int cols = chessData.length;
        int x = point.x;
        int y = point.y;
        int type = point.type;

        // 横轴
        int k = 1;
        for (int i = 1; i < 5; i++) {
            int preX = x - i;
            if (preX < 0) {
                break;
            }
            if (chessData[preX][y] != type) {
                break;
            }
            if (++k == 5) {
                return Statue.WIN;
            }
        }
        for (int i = 1; i < 5; i++) {
            int nextX = x + i;
            if (nextX > rows - 1) {
                break;
            }
            if (chessData[nextX][y] != type) {
                break;
            }
            if (++k == 5) {
                return Statue.WIN;
            }
        }

        // 纵轴
        k = 1;
        for (int i = 1; i < 5; i++) {
            int preY = y - i;
            if (preY < 0) {
                break;
            }
            if (chessData[x][preY] != type) {
                break;
            }
            if (++k == 5) {
                return Statue.WIN;
            }
        }
        for (int i = 1; i < 5; i++) {
            int nextY = y + i;
            if (nextY > cols - 1) {
                break;
            }
            if (chessData[x][nextY] != type) {
                break;
            }
            if (++k == 5) {
                return Statue.WIN;
            }
        }

        // 左对角线
        k = 1;
        for (int i = 1; i < 5; i++) {
            int preX = x - i;
            int preY = y - i;
            if (preX < 0 || preY < 0) {
                break;
            }
            if (chessData[preX][preY] != type) {
                break;
            }
            if (++k == 5) {
                return Statue.WIN;
            }
        }
        for (int i = 1; i < 5; i++) {
            int nextX = x + i;
            int nextY = y + i;
            if (nextX > rows - 1 || nextY > cols - 1) {
                break;
            }
            if (chessData[nextX][nextY] != type) {
                break;
            }
            if (++k == 5) {
                return Statue.WIN;
            }
        }

        // 右对角线
        k = 1;
        for (int i = 1; i < 5; i++) {
            int nextX = x + i;
            int preY = y - i;
            if (nextX > rows - 1 || preY < 0) {
                break;
            }
            if (chessData[nextX][preY] != type) {
                break;
            }
            if (++k == 5) {
                return Statue.WIN;
            }
        }
        for (int i = 1; i < 5; i++) {
            int preX = x - i;
            int nextY = y + i;
            if (preX < 0 || nextY > cols - 1) {
                break;
            }
            if (chessData[preX][nextY] != type) {
                break;
            }
            if (++k == 5) {
                return Statue.WIN;
            }
        }

        // 判断棋盘是否已满
        for (int[] chessDatum : chessData) {
            for (int j = 0; j < rows; j++) {
                if (chessDatum[j] == 0) {
                    // 还有空位，游戏继续
                    return Statue.IN_PROGRESS;
                }
            }
        }

        return Statue.DRAW;
    }
}
