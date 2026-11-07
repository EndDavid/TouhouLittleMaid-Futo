package com.github.tartaricacid.touhoulittlemaid.capability;

import net.minecraft.nbt.FloatTag;
import net.minecraft.util.Mth;

/**
 * 玩家的灵力（Power / P 点）数值，移植自上游 {@code capability/PowerCapability}。
 *
 * <p><b>与 P 点的换算：</b>1.00 灵力 = 100 P 点。所以在 {@code EntityPowerPoint} 里
 * 一律用 {@code value / 100.0F} 换算成灵力，配置项（如 {@code MaidFairyPowerPoint}）
 * 也是「灵力」为单位，乘以 100 才是掉落物上的 P 点数值。</p>
 *
 * <p><b>与上游的差异：</b>上游把它挂在 Forge capability 上
 * （{@code PowerCapabilityProvider} + {@code ICapabilitySerializable}）。
 * Fabric 没有 capability 系统，本移植版改为：</p>
 * <ul>
 *     <li>{@code mixin/PlayerPowerMixin} 给 {@code Player} 挂一个字段，
 *         并把本类暴露成 {@code IPower};</li>
 *     <li>{@code mixin/ServerPlayerPowerMixin} 负责存档（{@code FutoPower} 标签）；</li>
 *     <li>{@code event/PowerEvents} 负责死亡扣减与脏数据同步。</li>
 * </ul>
 * <p>因此<b>本类只保留纯粹的数据语义</b>，不再实现 {@code INBTSerializable}。</p>
 */
public class PowerCapability {
    /**
     * 灵力上限。上游同名常量，也是「P 点最多存 5.00」这条规则的唯一来源。
     */
    public static final float MAX_POWER = 5.0F;

    private float power = 0.0F;
    private boolean dirty;

    /**
     * 增加灵力，超过上限就顶到上限（<b>不会</b>溢出成经验——溢出转经验是
     * {@code EntityPowerPoint#playerTouch} 自己判断的）。
     */
    public void add(float points) {
        if (points + this.power <= MAX_POWER) {
            this.power += points;
        } else {
            this.power = MAX_POWER;
        }
        markDirty();
    }

    /**
     * 扣减灵力，不足则归零。
     */
    public void min(float points) {
        if (points <= this.power) {
            this.power -= points;
        } else {
            this.power = 0.0F;
        }
        markDirty();
    }

    public void set(float points) {
        this.power = Mth.clamp(points, 0, MAX_POWER);
        markDirty();
    }

    public float get() {
        return this.power;
    }

    /**
     * 标记「需要同步 / 需要存档」。
     *
     * <p>上游靠 it 做两件事：{@code CapabilityEvent#playerTickEvent} 里发现脏就发
     * {@code SyncCapabilityMessage}；同时 Forge 的 capability 存档也是靠这个时机落盘。
     * 本移植版只在服务端脏时发同步包，存档走 {@code ServerPlayer} 的 NBT。</p>
     */
    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public FloatTag serializeNBT() {
        return FloatTag.valueOf(this.power);
    }

    public void deserializeNBT(FloatTag nbt) {
        this.power = nbt.getAsFloat();
    }
}

