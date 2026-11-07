package com.github.tartaricacid.touhoulittlemaid.entity.passive;

import org.jetbrains.annotations.Nullable;

/**
 * 拾取类型，移植自上游 {@code entity/passive/PickType}。
 *
 * <p>女仆的「拾物模式」不只是开关，还分「只捡物品」「只捡经验」「全捡」三档。
 * 上游由 {@code configManager.getPickupType()} 提供，本移植版暂时存在同步数据里
 * （{@code EntityMaid#getPickupType()}），GUI 与命令后续再接上。</p>
 */
public enum PickType {
    ONLY_ITEM("item", true, false),
    ONLY_XP("xp", false, true),
    ALL("all", true, true);

    private final String id;
    private final boolean pickItem;
    private final boolean pickXp;

    PickType(String id, boolean pickItem, boolean pickXp) {
        this.id = id;
        this.pickItem = pickItem;
        this.pickXp = pickXp;
    }

    public String getId() {
        return this.id;
    }

    public boolean canPickItem() {
        return this.pickItem;
    }

    public boolean canPickXp() {
        return this.pickXp;
    }

    /**
     * 按 id 查类型；查不到返回 {@code null}，由调用方回退默认值。
     *
     * <p>不直接抛异常：这个 id 来自存档 / 网络，写坏了不该让整只女仆读档失败
     * （本项目在 {@code MaidTask} 上已经吃过一次这个亏）。</p>
     */
    @Nullable
    public static PickType byId(String id) {
        for (PickType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }

    public static String getTransKey(final PickType pickType) {
        return switch (pickType) {
            case ONLY_ITEM -> "gui.touhou_little_maid.maid_config.value.item";
            case ONLY_XP -> "gui.touhou_little_maid.maid_config.value.xp";
            default -> "gui.touhou_little_maid.maid_config.value.all";
        };
    }

    public static PickType getNextPickType(final PickType pickType) {
        return values()[(pickType.ordinal() + 1) % values().length];
    }

    public static PickType getPreviousPickType(final PickType pickType) {
        int index = pickType.ordinal() - 1;
        if (index < 0) {
            index = values().length - 1;
        }
        return values()[index % values().length];
    }
}

