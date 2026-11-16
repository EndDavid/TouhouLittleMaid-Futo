package com.github.tartaricacid.touhoulittlemaid.inventory.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * 「这张残局道具是哪个棋的、棋局长什么样」这条 tooltip 的数据载体，
 * 移植自上游 1.20.1 的 {@code inventory/tooltip/BoardStateTooltip}。
 *
 * <p>它只有两个字段：棋种（决定画哪张棋盘背景）与那行编码后的棋局。
 * 真正的解码与绘制在客户端（{@code client/tooltip/ClientBoardStateTooltip}），
 * 因为 {@code TooltipComponent} 本身是<b>通用</b>类型 —— 服务端也会构造它。</p>
 */
public record BoardStateTooltip(String type, String stateData) implements TooltipComponent {
    public static final String GOMOKU = "gomoku";
    public static final String XIANGQI = "xiangqi";
    public static final String CHESS = "chess";

    public static BoardStateTooltip ofGomoku(String stateData) {
        return new BoardStateTooltip(GOMOKU, stateData);
    }

    public static BoardStateTooltip ofXiangqi(String stateData) {
        return new BoardStateTooltip(XIANGQI, stateData);
    }

    public static BoardStateTooltip ofChess(String stateData) {
        return new BoardStateTooltip(CHESS, stateData);
    }
}

