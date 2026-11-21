package com.github.tartaricacid.touhoulittlemaid.block.properties;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * 野餐垫的「哪一块」，移植自上游 {@code block/properties/PicnicMatPart}。
 *
 * <p>野餐垫是 5×5 的多方块结构，但<b>只有中心那一格带方块实体</b>：
 * 食物存在中心格里，四张座位也只记在中心格上。其余 24 格的作用只有两个 ——
 * 挡住玩家（贴地 1/16 格的碰撞箱走上去会垫脚）和显示贴图，
 * 所以它们只需要一个 {@link #SIDE} 标记来区分自己不是中心。</p>
 */
public enum PicnicMatPart implements StringRepresentable {
    CENTER,
    SIDE;

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase(Locale.US);
    }

    public boolean isCenter() {
        return this == CENTER;
    }
}

