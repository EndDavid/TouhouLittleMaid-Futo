package com.github.tartaricacid.touhoulittlemaid.entity.data;

import com.github.tartaricacid.touhoulittlemaid.entity.misc.MonsterType;
import net.minecraft.ResourceLocationException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 女仆的攻击名单，对应上游 {@code entity/data/inner/AttackListData}。
 *
 * <p>语义：<b>只覆盖</b>默认判定 —— 名单里出现过的生物用名单里的关系，
 * 没出现的仍然走 {@code DefaultMonsterType.getMonsterType(target)}。
 * 所以空名单等于「完全按原版规则办事」，这也是默认值。</p>
 *
 * <p><b>与上游的差异：</b>上游把这份数据挂在 Task Data 系统
 * （{@code EntityMaid#getData(InitTaskData.ATTACK_LIST)}）上，那套系统需要
 * {@code ResourceKey} + Codec + 自定义同步；本移植版的数据只有这一份，
 * 直接用普通字段 + NBT 更省事，而且它本身就是每只女仆独立的数据，
 * 不存在「按 Task 存多份」的需求。</p>
 *
 * <p>存档格式（{@code MaidAttackList} 标签）：</p>
 * <pre>
 * { Entries: [ { Id: "minecraft:zombie", Type: "hostile" }, ... ] }
 * </pre>
 */
public final class AttackListData {
    private static final String TAG_LIST = "MaidAttackList";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_ID = "Id";
    private static final String TAG_TYPE = "Type";

    private final Map<ResourceLocation, MonsterType> attackGroups = new HashMap<>();

    /**
     * 可写的映射本体（GUI 直接改它，所以不返回副本）。
     */
    public Map<ResourceLocation, MonsterType> attackGroups() {
        return this.attackGroups;
    }

    public static AttackListData empty() {
        return new AttackListData();
    }

    /**
     * 写进父标签（会整体覆盖旧的 {@code MaidAttackList}）。
     */
    public void save(CompoundTag parent) {
        ListTag list = new ListTag();
        for (Map.Entry<ResourceLocation, MonsterType> entry : this.attackGroups.entrySet()) {
            CompoundTag element = new CompoundTag();
            element.putString(TAG_ID, entry.getKey().toString());
            element.putString(TAG_TYPE, entry.getValue().getSerializedName());
            list.add(element);
        }
        CompoundTag tag = new CompoundTag();
        tag.put(TAG_ENTRIES, list);
        parent.put(TAG_LIST, tag);
    }

    /**
     * 从父标签读出来。
     *
     * <p>坏条目<b>逐条跳过</b>，而不是整段放弃：名单是玩家手填的，
     * 里面出现一个拼错的实体 id 很正常，不该让其它几十条一起失效。</p>
     */
    public static AttackListData load(CompoundTag parent) {
        AttackListData data = new AttackListData();
        if (!parent.contains(TAG_LIST, Tag.TAG_COMPOUND)) {
            return data;
        }
        ListTag list = parent.getCompound(TAG_LIST).getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag element = list.getCompound(i);
            try {
                ResourceLocation id = new ResourceLocation(element.getString(TAG_ID));
                MonsterType type = MonsterType.byName(element.getString(TAG_TYPE));
                if (type != null) {
                    data.attackGroups.put(id, type);
                }
            } catch (ResourceLocationException ignored) {
                // 单个坏条目直接丢掉
            }
        }
        return data;
    }

    /**
     * 把名单写进菜单的下发数据（{@code ExtendedScreenHandlerFactory#writeScreenOpeningData}）。
     *
     * <p>用菜单数据而不是单独的同步包：这份名单只有「打开任务设置界面」的那一刻需要，
     * 而菜单数据本来就只发给打开界面的那一个玩家，正好省掉一条通道和一次全量同步。</p>
     */
    public void writeToBuf(net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeVarInt(this.attackGroups.size());
        for (Map.Entry<ResourceLocation, MonsterType> entry : this.attackGroups.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            buf.writeUtf(entry.getValue().getSerializedName());
        }
    }

    /**
     * 从菜单的下发数据读回名单（客户端构造界面时调用）。
     */
    public static AttackListData readFromBuf(net.minecraft.network.FriendlyByteBuf buf) {
        AttackListData data = new AttackListData();
        int size = buf.readVarInt();
        for (int i = 0; i < size; i++) {
            ResourceLocation id = buf.readResourceLocation();
            MonsterType type = MonsterType.byName(buf.readUtf());
            if (type != null) {
                data.attackGroups.put(id, type);
            }
        }
        return data;
    }

    /**
     * 按 HOSTILE / NEUTRAL / FRIENDLY 分组排好序的 id 列表（界面用它做显示顺序）。
     */
    public java.util.List<ResourceLocation> sortedKeys() {
        java.util.List<ResourceLocation> keys = new java.util.ArrayList<>();
        for (MonsterType type : new MonsterType[]{MonsterType.HOSTILE, MonsterType.NEUTRAL, MonsterType.FRIENDLY}) {
            for (Map.Entry<ResourceLocation, MonsterType> entry : this.attackGroups.entrySet()) {
                if (entry.getValue() == type) {
                    keys.add(entry.getKey());
                }
            }
        }
        return keys;
    }
}
