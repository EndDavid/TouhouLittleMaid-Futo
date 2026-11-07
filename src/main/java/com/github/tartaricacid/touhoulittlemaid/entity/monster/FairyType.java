package com.github.tartaricacid.touhoulittlemaid.entity.monster;

/**
 * 女仆妖精的 18 种发色变体，移植自上游 {@code entity/monster/FairyType}。
 *
 * <p><b>顺序即含义：</b>序数被写进同步数据 {@code DATA_FAIRY_TYPE}，
 * 渲染器再用 {@code switch (ordinal)} 选贴图 {@code maid_fairy_<n>.png}、
 * 存档键 {@code FairyType} 也直接存序数。因此<b>只能往后追加</b>，
 * 中间插入或调序会让老存档里的妖精换发色。</p>
 */
public enum FairyType {
    BLACK,
    ORANGE,
    WHITE,
    YELLOW,
    BROWN,
    RED,
    GREEN,
    BLUE,
    PINK,
    LIME,
    CYAN,
    PURPLE,
    GRAY,
    LIGHT_GRAY,
    LIGHT_BLUE,
    MAGENTA,
    LIGHT_PURPLE,
    GOLD
}

