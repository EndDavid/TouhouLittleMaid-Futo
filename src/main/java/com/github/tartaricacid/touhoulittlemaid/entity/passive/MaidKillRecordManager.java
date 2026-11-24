package com.github.tartaricacid.touhoulittlemaid.entity.passive;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Slime;

import javax.annotation.Nullable;

/**
 * 女仆的击杀记录，逐字移植自上游 {@code entity/passive/MaidKillRecordManager}。
 *
 * <p>四个计数（总数 / 史莱姆 / 凋灵 / 末影龙）本身只有一个用途：
 * 点亮四条「挑战」进度。计数会写进女仆 NBT 的 {@code KillRecord} 子标签
 * （与上游同名字段，方便两边存档互通）。</p>
 *
 * <h2>为什么计数必须落盘</h2>
 * <p>「百人斩」要求累计 100 次，「三百只史莱姆」要求 300 次 —— 一场游戏里跨几十次
 * 存档才凑得满。只放内存的话，区块一卸载（女仆被收进魂符 / 玩家走远）计数就归零，
 * 这两条进度就永远点不亮。</p>
 *
 * <h2>与上游的差异</h2>
 * <p>上游靠 Forge 的 {@code LivingDeathEvent} 拿到「谁杀的」；Fabric 侧的等价物是
 * {@code ServerLivingEntityEvents.AFTER_DEATH}，见 {@code event/MaidKillRecordEvent}。
 * 判定条件一致：{@code source.getEntity()} 是女仆才算她的击杀。</p>
 */
public final class MaidKillRecordManager {
    /**
     * 与上游同名的 NBT 键。
     */
    public static final String KILL_RECORD = "KillRecord";
    private static final String TOTAL_COUNT = "TotalCount";
    private static final String SLIME_COUNT = "Slime";
    private static final String WITHER_COUNT = "Wither";
    private static final String ENDER_DRAGON_COUNT = "EnderDragon";

    /**
     * 「百人斩」的门槛。上游写死 100，这里同样写死 —— 它是进度 JSON 之外的约定，
     * 改了不会报错，只会让进度悄悄点不亮。
     */
    private static final int KILL_100_THRESHOLD = 100;
    /**
     * 「三百只史莱姆」的门槛。
     */
    private static final int KILL_SLIME_300_THRESHOLD = 300;

    private int totalCount;
    private int slimeCount;
    private int witherCount;
    private int enderDragonCount;

    void addAdditionalSaveData(CompoundTag compound) {
        CompoundTag killRecord = new CompoundTag();
        killRecord.putInt(TOTAL_COUNT, this.totalCount);
        killRecord.putInt(SLIME_COUNT, this.slimeCount);
        killRecord.putInt(WITHER_COUNT, this.witherCount);
        killRecord.putInt(ENDER_DRAGON_COUNT, this.enderDragonCount);
        compound.put(KILL_RECORD, killRecord);
    }

    void readAdditionalSaveData(CompoundTag compound) {
        if (!compound.contains(KILL_RECORD)) {
            return;
        }
        CompoundTag killRecord = compound.getCompound(KILL_RECORD);
        this.totalCount = killRecord.getInt(TOTAL_COUNT);
        this.slimeCount = killRecord.getInt(SLIME_COUNT);
        this.witherCount = killRecord.getInt(WITHER_COUNT);
        this.enderDragonCount = killRecord.getInt(ENDER_DRAGON_COUNT);
    }

    /**
     * 女仆打死了一只生物。
     *
     * <p>注意顺序与上游一致：<b>先加计数、再判门槛</b>。
     * 反过来的话「第 100 只」那次会判在 99 上，进度要等到第 101 只才亮。</p>
     */
    public void onTargetDeath(EntityMaid maid, LivingEntity target) {
        LivingEntity owner = maid.getOwner();
        this.totalCount++;
        triggerKill(owner, TriggerType.MAID_KILL_MOB);
        if (this.totalCount >= KILL_100_THRESHOLD) {
            triggerKill(owner, TriggerType.KILL_100);
        }
        if (target instanceof Slime) {
            this.slimeCount++;
            if (this.slimeCount >= KILL_SLIME_300_THRESHOLD) {
                triggerKill(owner, TriggerType.KILL_SLIME_300);
            }
        }
        if (target instanceof WitherBoss) {
            this.witherCount++;
            triggerKill(owner, TriggerType.KILL_WITHER);
        }
        if (target instanceof EnderDragon) {
            this.enderDragonCount++;
            triggerKill(owner, TriggerType.KILL_DRAGON);
        }
    }

    private void triggerKill(@Nullable LivingEntity owner, String eventName) {
        if (owner instanceof ServerPlayer serverPlayer) {
            InitTrigger.MAID_EVENT.trigger(serverPlayer, eventName);
        }
    }

    public int getTotalCount() {
        return this.totalCount;
    }

    public int getSlimeCount() {
        return this.slimeCount;
    }

    public int getWitherCount() {
        return this.witherCount;
    }

    public int getEnderDragonCount() {
        return this.enderDragonCount;
    }
}

