package com.github.tartaricacid.touhoulittlemaid.data;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 每位玩家累计消耗的 AI token 数，对应 1.20.1 的 ChatTokensAttachment。
 *
 * <p>1.20.1 用的是 Fabric 的 DataAttachment（挂在玩家身上、随玩家存档）。
 * 1.18.2 的 Fabric API 0.77 还没有 attachment API，所以这里换成世界级的 SavedData，
 * 以玩家 UUID 为键 —— 语义相同（跨重启持久化），只是文件落在 world/data 下。</p>
 *
 * <p><b>为什么存在主世界</b>：SavedData 必须挂在某个 level 上，主世界永远存在，
 * 而玩家可能在任何维度说话。换维度不会丢计数。</p>
 */
public final class ChatTokensData extends SavedData {
    private static final String FILE_ID = TouhouLittleMaid.MOD_ID + "_ai_tokens";
    private static final String TAG_PLAYER = "Player";
    private static final String TAG_UUID = "Uuid";
    private static final String TAG_TOKENS = "Tokens";

    private final Map<UUID, Integer> tokens = new HashMap<>();

    public static ChatTokensData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return new ChatTokensData();
        }
        return overworld.getDataStorage().computeIfAbsent(ChatTokensData::load, ChatTokensData::new, FILE_ID);
    }

    private static ChatTokensData load(CompoundTag tag) {
        ChatTokensData data = new ChatTokensData();
        ListTag list = tag.getList(TAG_PLAYER, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID(TAG_UUID)) {
                data.tokens.put(entry.getUUID(TAG_UUID), entry.getInt(TAG_TOKENS));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        tokens.forEach((uuid, value) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(TAG_UUID, uuid);
            entry.putInt(TAG_TOKENS, value);
            list.add(entry);
        });
        tag.put(TAG_PLAYER, list);
        return tag;
    }

    public int get(UUID player) {
        return tokens.getOrDefault(player, 0);
    }

    public void add(UUID player, int amount) {
        if (amount <= 0) {
            return;
        }
        int next = get(player) + amount;
        tokens.put(player, next);
        setDirty();
    }
}