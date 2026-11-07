package com.github.tartaricacid.touhoulittlemaid.entity.misc;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 生物对女仆的敌对关系分类，移植自上游 {@code entity/misc/MonsterType}。
 *
 * <p>顺序与上游一致（FRIENDLY / NEUTRAL / HOSTILE）—— 攻击列表界面的左右翻页
 * 与排序都依赖这个顺序。序列化用的是 {@link #getSerializedName()} 而不是序号,
 * 枚举以后增删也不会串位。</p>
 */
public enum MonsterType {
    FRIENDLY,
    NEUTRAL,
    HOSTILE;

    public MonsterType getPrevious() {
        int index = this.ordinal() - 1;
        if (index < 0) {
            index = values().length - 1;
        }
        return values()[index % values().length];
    }

    public MonsterType getNext() {
        int ordinal = this.ordinal();
        int length = MonsterType.values().length;
        return MonsterType.values()[(ordinal + 1) % length];
    }

    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ENGLISH);
    }

    /**
     * 攻击列表界面里显示的名字（带颜色，颜色在语言文件里）。
     */
    public Component getComponent() {
        return new TranslatableComponent("gui.touhou_little_maid.monster_type." + this.getSerializedName());
    }

    /**
     * 按序列化名反查；查不到返回 {@code null}。
     *
     * <p>与 {@code PickType#byId} 同样的理由：这个值来自存档与网络,
     * 写坏了不该让整只女仆读档失败，由调用方回退默认值。</p>
     */
    @Nullable
    public static MonsterType byName(String name) {
        for (MonsterType type : values()) {
            if (type.getSerializedName().equals(name)) {
                return type;
            }
        }
        return null;
    }
}
