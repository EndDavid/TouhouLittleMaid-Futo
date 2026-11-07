package com.github.tartaricacid.touhoulittlemaid.entity.favorability;

import com.github.tartaricacid.touhoulittlemaid.entity.favorability.FavorabilityManager;

/**
 * 好感度变动类型（上游 {@code entity/favorability/Type}）。
 *
 * <p>{@code point} 是这类行为带来的好感度点数，{@code cooldown} 是这类行为触发后
 * 多久才能再次计入（tick）。两者都是「按类型」而不是「按次」的，
 * 这就是为什么女仆不会站着不动一直吃——吃完一次后 2 分钟内不会再算工作餐。</p>
 *
 * <p>上游这份表里大部分类型属于「娱乐方块 / 棋类 / 睡觉」等尚未移植的子系统，
 * 但它们的数值仍然照抄，这样以后接上时不用再回来改。</p>
 */
public class Type {
    public static final Type BOOKSHELF = new Type("BookShelf", 2, 24000);
    public static final Type COMPUTER = new Type("Computer", 2, 24000);
    public static final Type KEYBOARD = new Type("Keyboard", 2, 24000);
    public static final Type GOMOKU = new Type("Gomoku", 2, 24000);
    public static final Type SLEEP = new Type("Sleep", 2, 24000);
    public static final Type GOMOKU_WIN = new Type("GomokuWin", 8, 12000);
    public static final Type CCHESS_WIN = new Type("CChessWin", 4, 18000);
    /**
     * 上游这里把 {@code WCHESS_WIN} 的类型名也写成了 {@code "CChessWin"}
     * （与上一行重名，后注册的会覆盖先注册的）。这里照抄这个 bug：
     * 改掉的话，未来接上棋类时行为会和上游不一致。
     */
    public static final Type WCHESS_WIN = new Type("CChessWin", 4, 18000);
    public static final Type WORK_MEAL = new Type("WorkMeal", 1, 2 * 60 * 20);
    public static final Type ON_HOME_MEAL = new Type("OnHomeMeal", 1, 24000);
    public static final Type HOME_MEAL = new Type("HomeMeal", 1, 60 * 20);
    public static final Type DEATH = new Type("Death", -2, 12000);

    private final String typeName;
    private final int point;
    private final int cooldown;
    private final boolean isReduce;

    public Type(String typeName, int point, int cooldown) {
        this.typeName = typeName;
        this.point = Math.abs(point);
        this.cooldown = cooldown;
        this.isReduce = point < 0;
        FavorabilityManager.TYPES.put(typeName, this);
    }

    public String getTypeName() {
        return typeName;
    }

    public int getPoint() {
        return point;
    }

    public int getCooldown() {
        return cooldown;
    }

    public boolean isReduce() {
        return isReduce;
    }
}

