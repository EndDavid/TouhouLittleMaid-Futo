package com.github.tartaricacid.touhoulittlemaid.api.block;

import java.util.UUID;

/**
 * 棋盘方块实体：报告它生成的座位实体。
 *
 * <p>对应上游 {@code api/block/IBoardGameEntityBlock}。</p>
 *
 * <p><b>它存在的唯一理由：</b>{@code MaidBoardGameTask} 需要判断「这张棋盘是不是已经有人坐了」。
 * 棋盘本身不保存「有没有人」，坐下的实体是通过 {@code SitId} 反查的 ——
 * 只要这个 UUID 对应的 {@code EntitySit} 还活着，就说明有人（玩家或别的女仆）正坐着。
 * 没有这个接口，AI 只能盲目挑最近的一张棋盘，然后和玩家抢同一个座位。</p>
 */
public interface IBoardGameEntityBlock {
    /**
     * 棋盘生成的座位实体的 UUID；从未有人坐过时返回 {@code Util.NIL_UUID}。
     */
    UUID getSitId();
}

