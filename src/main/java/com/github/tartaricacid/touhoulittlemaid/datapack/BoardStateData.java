package com.github.tartaricacid.touhoulittlemaid.datapack;

import com.github.tartaricacid.touhoulittlemaid.datapack.pojo.BoardStateRecord;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.google.common.collect.Lists;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

/**
 * 三种棋的残局数据表，移植自上游 1.20.1 的 {@code datapack/BoardStateData}。
 *
 * <p>数据来自数据包（{@code data/<namespace>/board_states/{gomoku,xiangqi,chess}.json}），
 * 由 {@link BoardStateDataReload} 在资源重载时填进来。三条列表分开存，
 * 因为战利品与道具是「按物品类型」取用同一批数据的。</p>
 *
 * <h2>为什么返回不可变空表而不是 {@code null}</h2>
 * <p>调用方（战利品注入）会在<b>每次</b>箱子被打开时跑一遍，而数据包可能一条都没提供。
 * 返回 {@code null} 会让调用方到处判空，而一个共享的空表既安全又明确。</p>
 */
public final class BoardStateData {
    private static final List<BoardStateRecord> CHESS_RECORDS = Lists.newArrayList();
    private static final List<BoardStateRecord> XIANGQI_RECORDS = Lists.newArrayList();
    private static final List<BoardStateRecord> GOMOKU_RECORDS = Lists.newArrayList();

    private BoardStateData() {
    }

    public static void clear() {
        CHESS_RECORDS.clear();
        XIANGQI_RECORDS.clear();
        GOMOKU_RECORDS.clear();
    }

    public static void addChessRecords(List<BoardStateRecord> records) {
        CHESS_RECORDS.addAll(records);
    }

    public static void addXiangqiRecords(List<BoardStateRecord> records) {
        XIANGQI_RECORDS.addAll(records);
    }

    public static void addGomokuRecords(List<BoardStateRecord> records) {
        GOMOKU_RECORDS.addAll(records);
    }

    public static List<BoardStateRecord> getChessRecords() {
        return Collections.unmodifiableList(CHESS_RECORDS);
    }

    public static List<BoardStateRecord> getXiangqiRecords() {
        return Collections.unmodifiableList(XIANGQI_RECORDS);
    }

    public static List<BoardStateRecord> getGomokuRecords() {
        return Collections.unmodifiableList(GOMOKU_RECORDS);
    }

    /**
     * 三张表是否都还是空的（即「还没读过数据」）。
     *
     * <p>战利品注入用它判断要不要自己读一次：加载顺序不可靠，
     * 而「全空」只可能意味着还没读，不可能意味着真没数据（模组自带 5+5+5 条）。</p>
     */
    public static boolean isEmpty() {
        return CHESS_RECORDS.isEmpty() && XIANGQI_RECORDS.isEmpty() && GOMOKU_RECORDS.isEmpty();
    }

    /**
     * 按物品取它自己的那批残局。不是残局道具就返回空表。
     */
    public static List<BoardStateRecord> getRecordsByItem(ItemStack stack) {
        Item item = stack.getItem();
        if (item == InitItems.WCHESS_BOARD_STATE) {
            return getChessRecords();
        }
        if (item == InitItems.CCHESS_BOARD_STATE) {
            return getXiangqiRecords();
        }
        if (item == InitItems.GOMOKU_BOARD_STATE) {
            return getGomokuRecords();
        }
        return Collections.emptyList();
    }
}

