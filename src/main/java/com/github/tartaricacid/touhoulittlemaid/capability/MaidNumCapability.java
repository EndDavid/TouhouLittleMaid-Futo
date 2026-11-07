package com.github.tartaricacid.touhoulittlemaid.capability;

import net.minecraft.nbt.IntTag;
import net.minecraft.util.Mth;

/**
 * 玩家可拥有的女仆数量（上游同名 capability）。
 *
 * <p>上游通过 Forge capability 挂在玩家身上；Fabric 没有 capability 系统，
 * 本移植版的 {@code get()} 改为按需统计（见 {@link #canAdd(int)}），
 * 只保留上游的语义与上限规则。</p>
 *
 * <p>上限读配置 {@code MaidConfig.OWNER_MAX_MAID_NUM}（上游默认 {@code Integer.MAX_VALUE}）。</p>
 */
public class MaidNumCapability {
    /**
     * 兜底上限：只有在配置尚未加载时才会用到。
     */
    public static final int DEFAULT_MAX_NUM = 10;

    private int num = 0;
    private boolean dirty;

    /**
     * 判断在已有 {@code current} 只女仆的情况下还能不能再驯服一只。
     */
    public boolean canAdd(int current) {
        return current + 1 <= getMaxNum();
    }

    public boolean canAdd() {
        return canAdd(this.num);
    }

    public void add() {
        this.add(1);
    }

    public void add(int num) {
        this.num = Math.min(this.num + num, getMaxNum());
        markDirty();
    }

    public void min(int num) {
        this.num = num <= this.num ? this.num - num : 0;
        markDirty();
    }

    public void set(int num) {
        this.num = Mth.clamp(num, 0, getMaxNum());
        markDirty();
    }

    public int getMaxNum() {
        return com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig.ownerMaxMaidNum();
    }

    public int get() {
        return this.num;
    }

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public IntTag serializeNBT() {
        return IntTag.valueOf(this.num);
    }

    public void deserializeNBT(IntTag nbt) {
        this.num = nbt.getAsInt();
    }
}

