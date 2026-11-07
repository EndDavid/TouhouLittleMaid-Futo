package com.github.tartaricacid.touhoulittlemaid.item;

/**
 * 各等级背包的可用槽位数，移植自上游 {@code item/BackpackLevel}。
 *
 * <p>注意这些是<b>可用槽位</b>，不是「背包物品栏总容量」：女仆的物品栏统一是 36 格，
 * 背包只决定能用其中前多少格（其余格被隐藏）。</p>
 */
public final class BackpackLevel {
    public static final int EMPTY_CAPACITY = 6;
    public static final int SMALL_CAPACITY = 12;
    public static final int MIDDLE_CAPACITY = 24;
    public static final int BIG_CAPACITY = 36;
    public static final int CRAFTING_TABLE_CAPACITY = 18;
    public static final int FURNACE_CAPACITY = 18;
    public static final int TANK_CAPACITY = 18;

    private BackpackLevel() {
    }
}

