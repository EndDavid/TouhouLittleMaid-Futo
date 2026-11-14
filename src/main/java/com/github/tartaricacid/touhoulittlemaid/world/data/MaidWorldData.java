package com.github.tartaricacid.touhoulittlemaid.world.data;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityTombstone;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MaidWorldData extends SavedData {
    private static final String IDENTIFIER = "touhou_little_maid_world_data";
    private static final String MAID_INFOS_TAG = "MaidInfos";
    private static final String MAID_TOMBSTONES_TAG = "MaidTombstones";
    private final Map<UUID, List<MaidInfo>> infos = Maps.newHashMap();
    private final Map<UUID, List<MaidInfo>> tombstones = Maps.newHashMap();

    @Nullable
    public static MaidWorldData get(Level level) {
        if (level instanceof ServerLevel) {
            ServerLevel overWorld = level.getServer().getLevel(Level.OVERWORLD);
            if (overWorld == null) {
                return null;
            }
            DimensionDataStorage storage = overWorld.getDataStorage();
            MaidWorldData data = storage.computeIfAbsent(MaidWorldData::load, MaidWorldData::new, IDENTIFIER);
            data.setDirty();
            return data;
        }
        return null;
    }

    public static MaidWorldData load(CompoundTag tag) {
        MaidWorldData data = new MaidWorldData();
        if (tag.contains(MAID_INFOS_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag infosTag = tag.getCompound(MAID_INFOS_TAG);
            for (String key : infosTag.getAllKeys()) {
                ListTag listTag = infosTag.getList(key, Tag.TAG_COMPOUND);
                for (int i = 0; i < listTag.size(); i++) {
                    CompoundTag infoTag = listTag.getCompound(i);
                    String dimension = infoTag.getString("Dimension");
                    BlockPos chunkPos = NbtUtils.readBlockPos(infoTag.getCompound("ChunkPos"));
                    UUID ownerId = infoTag.getUUID("OwnerId");
                    UUID maidId = infoTag.getUUID("MaidId");
                    long timestamp = infoTag.getLong("Timestamp");
                    MutableComponent name = Component.Serializer.fromJson(infoTag.getString("Name"));
                    List<MaidInfo> maidInfos = data.infos.computeIfAbsent(ownerId, uuid -> Lists.newArrayList());
                    maidInfos.add(new MaidInfo(dimension, chunkPos, ownerId, maidId, timestamp, name));
                }
            }
        }
        if (tag.contains(MAID_TOMBSTONES_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag tombstonesTag = tag.getCompound(MAID_TOMBSTONES_TAG);
            for (String key : tombstonesTag.getAllKeys()) {
                ListTag listTag = tombstonesTag.getList(key, Tag.TAG_COMPOUND);
                for (int i = 0; i < listTag.size(); i++) {
                    CompoundTag infoTag = listTag.getCompound(i);
                    String dimension = infoTag.getString("Dimension");
                    BlockPos chunkPos = NbtUtils.readBlockPos(infoTag.getCompound("ChunkPos"));
                    UUID ownerId = infoTag.getUUID("OwnerId");
                    UUID tombstoneId = infoTag.getUUID("TombstoneId");
                    long timestamp = infoTag.getLong("Timestamp");
                    MutableComponent name = Component.Serializer.fromJson(infoTag.getString("Name"));
                    List<MaidInfo> tombstoneInfos = data.tombstones.computeIfAbsent(ownerId, uuid -> Lists.newArrayList());
                    tombstoneInfos.add(new MaidInfo(dimension, chunkPos, ownerId, tombstoneId, timestamp, name));
                }
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag infosTag = new CompoundTag();
        infos.forEach((id, data) -> {
            ListTag listTag = new ListTag();
            data.forEach(info -> {
                CompoundTag infoTag = new CompoundTag();
                infoTag.putString("Dimension", info.getDimension());
                infoTag.put("ChunkPos", NbtUtils.writeBlockPos(info.getChunkPos()));
                infoTag.putUUID("OwnerId", info.getOwnerId());
                infoTag.putUUID("MaidId", info.getEntityId());
                infoTag.putLong("Timestamp", info.getTimestamp());
                infoTag.putString("Name", Component.Serializer.toJson(info.getName()));
                listTag.add(infoTag);
            });
            infosTag.put(id.toString(), listTag);
        });

        CompoundTag tombstonesTag = new CompoundTag();
        tombstones.forEach((id, data) -> {
            ListTag listTag = new ListTag();
            data.forEach(info -> {
                CompoundTag infoTag = new CompoundTag();
                infoTag.putString("Dimension", info.getDimension());
                infoTag.put("ChunkPos", NbtUtils.writeBlockPos(info.getChunkPos()));
                infoTag.putUUID("OwnerId", info.getOwnerId());
                infoTag.putUUID("TombstoneId", info.getEntityId());
                infoTag.putLong("Timestamp", info.getTimestamp());
                infoTag.putString("Name", Component.Serializer.toJson(info.getName()));
                listTag.add(infoTag);
            });
            tombstonesTag.put(id.toString(), listTag);
        });
        tag.put(MAID_INFOS_TAG, infosTag);
        tag.put(MAID_TOMBSTONES_TAG, tombstonesTag);
        return tag;
    }

    /**
     * 记下 / 刷新一只女仆的位置。
     *
     * <p><b>这是 upsert，不是追加。</b>同一只女仆已有记录时替换旧记录，而不是再压一条。
     * 上游只在「女仆被移出世界」时调用一次，所以那里追加也够；本移植版改为
     * 周期性刷新（见 {@code event/MaidWorldDataTicker} 的说明），若继续追加
     * 会让这份数据每 3 秒膨胀一条，最终存档里堆着成百上千条重复记录。</p>
     *
     * <p>键是 {@code ownerId + maidId}：{@code MaidInfo} 按主人分组存放，
     * 同一只女仆换主人后应当算两条不同的记录，这里不做跨主人去重。</p>
     */
    public void addInfo(MaidInfo info) {
        UUID ownerId = info.getOwnerId();
        UUID maidId = info.getEntityId();
        List<MaidInfo> maidInfos = this.infos.computeIfAbsent(ownerId, uuid -> Lists.newArrayList());
        maidInfos.removeIf(existing -> existing.getEntityId().equals(maidId));
        maidInfos.add(info);
        this.setDirty();
    }

    public void addInfo(EntityMaid maid) {
        String dimension = maid.level.dimension().location().toString();
        BlockPos chunkPos = maid.blockPosition();
        UUID ownerId = maid.getOwnerUUID();
        UUID maidId = maid.getUUID();
        long timestamp = System.currentTimeMillis();
        Component name = maid.getDisplayName();
        this.addInfo(new MaidInfo(dimension, chunkPos, ownerId, maidId, timestamp, name));
    }

    public void removeInfo(EntityMaid maid) {
        UUID ownerId = maid.getOwnerUUID();
        if (this.infos.containsKey(ownerId)) {
            UUID maidId = maid.getUUID();
            this.infos.get(ownerId).removeIf(info -> info.getEntityId().equals(maidId));
            this.setDirty();
        }
    }

    @Nullable
    public List<MaidInfo> getInfos(UUID owner) {
        return infos.get(owner);
    }

    @Nullable
    public List<MaidInfo> getPlayerMaidInfos(Player player) {
        return this.infos.get(player.getUUID());
    }

    /**
     * 这位玩家名下、且<b>此刻所在区块确实没加载</b>的女仆记录。
     *
     * <p><b>为什么要在读取时过滤，而不是靠写入时删除：</b>
     * 本移植版的记录是「周期性刷新」的（见 {@code event/MaidWorldDataTicker}），
     * 所以一只正站在你面前的女仆<b>也有</b>记录。若直接把记录当成「她在别处」，
     * 小号会永远报「还有 N 只女仆在未加载区块」，仆从铃也会指着一个错误的位置。</p>
     *
     * <p>过滤条件有两层，缺一不可：</p>
     * <ol>
     *     <li>维度必须一致 —— 记录里的坐标只在它自己的维度里有意义；</li>
     *     <li>{@link Level#isLoaded} 必须为 {@code false} —— 区块已加载就说明
     *         她是能被直接找到的（此时应该走传送分支，不该画浮标）。</li>
     * </ol>
     *
     * <p>注意 {@link MaidInfo#getChunkPos()} 名字里虽然带 Chunk，存的其实是
     * <b>方块坐标</b>（见 {@link #addInfo(EntityMaid)} 用的是 {@code blockPosition()}）。
     * 上游同样的命名，这里保持字段语义一致，免得存档互不兼容。</p>
     */
    public List<MaidInfo> getUnloadedMaidInfos(Level level, Player player) {
        List<MaidInfo> all = this.infos.get(player.getUUID());
        List<MaidInfo> result = Lists.newArrayList();
        if (all == null) {
            return result;
        }
        String dimension = level.dimension().location().toString();
        for (MaidInfo info : all) {
            if (!info.getDimension().equals(dimension)) {
                continue;
            }
            if (!level.isLoaded(info.getChunkPos())) {
                result.add(info);
            }
        }
        return result;
    }

    /**
     * 所有女仆记录的快照（含已加载的）。仅供 {@code MaidWorldDataTicker} 做清理。
     */
    public List<MaidInfo> allInfos() {
        List<MaidInfo> result = Lists.newArrayList();
        this.infos.values().forEach(result::addAll);
        return result;
    }

    /**
     * 按主人 + 女仆 UUID 删除一条记录。
     *
     * <p>与 {@link #removeInfo(EntityMaid)} 的区别：那个要拿到实体本身，
     * 而清理逻辑手里只有记录（女仆可能已经不在了）。</p>
     */
    public void removeInfo(UUID ownerId, UUID maidId) {
        List<MaidInfo> maidInfos = this.infos.get(ownerId);
        if (maidInfos != null && maidInfos.removeIf(info -> info.getEntityId().equals(maidId))) {
            this.setDirty();
        }
    }

    public void addTombstones(MaidInfo info) {
        UUID ownerId = info.getOwnerId();
        List<MaidInfo> tombstoneInfos = this.tombstones.computeIfAbsent(ownerId, uuid -> Lists.newArrayList());
        tombstoneInfos.add(info);
        this.setDirty();
    }

    public void addTombstones(EntityMaid maid, EntityTombstone tombstone) {
        String dimension = maid.level.dimension().location().toString();
        BlockPos chunkPos = tombstone.blockPosition();
        UUID ownerId = maid.getOwnerUUID();
        UUID tombstoneId = tombstone.getUUID();
        long timestamp = System.currentTimeMillis();
        Component name = maid.getDisplayName();
        this.addTombstones(new MaidInfo(dimension, chunkPos, ownerId, tombstoneId, timestamp, name));
    }

    public void removeTombstones(EntityTombstone tombstone) {
        UUID ownerId = tombstone.getOwnerId();
        if (this.tombstones.containsKey(ownerId)) {
            UUID tombstoneId = tombstone.getUUID();
            this.tombstones.get(ownerId).removeIf(info -> info.getEntityId().equals(tombstoneId));
            this.setDirty();
        }
    }

    @Nullable
    public List<MaidInfo> getTombstones(UUID owner) {
        return tombstones.get(owner);
    }

    @Nullable
    public List<MaidInfo> getPlayerMaidTombstones(Player player) {
        return this.tombstones.get(player.getUUID());
    }
}
