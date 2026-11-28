package com.github.tartaricacid.touhoulittlemaid.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.world.data.MaidInfo;
import com.github.tartaricacid.touhoulittlemaid.world.data.MaidWorldData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 周期性把「已加载的女仆在哪儿」记进 {@link MaidWorldData}。
 */
public final class MaidWorldDataTicker {
    /**
     * 刷新间隔（tick）。60 tick = 3 秒。
     *
     * <p>不设得更短是因为每次都要遍历全服实体；3 秒的位置误差对本功能无影响
     * （女仆卸载后不会再动）。</p>
     */
    private static final int INTERVAL_TICKS = 60;

    private MaidWorldDataTicker() {
    }

    public static void initServer() {
        ServerTickEvents.END_SERVER_TICK.register(MaidWorldDataTicker::onEndTick);
    }

    private static void onEndTick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL_TICKS != 0) {
            return;
        }
        // 维度名 -> 该维度的 ServerLevel，供下面按记录里的维度定位
        Map<String, ServerLevel> levels = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            levels.put(level.dimension().location().toString(), level);
        }

        // 本轮的「活着的、已加载的」女仆：既用来刷新记录，也用来判断幽灵记录
        Set<UUID> alive = new HashSet<>();
        MaidWorldData data = null;
        for (ServerLevel level : levels.values()) {
            if (data == null) {
                data = MaidWorldData.get(level);
            }
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof EntityMaid maid) || !maid.isAlive() || maid.isRemoved()) {
                    continue;
                }
                // 野生女仆没有主人，记下来也没人能用，跳过
                if (maid.getOwnerUUID() == null) {
                    continue;
                }
                alive.add(maid.getUUID());
                if (data != null) {
                    data.addInfo(maid);
                }
            }
        }
        if (data == null) {
            return;
        }

        // 幽灵清理：区块加载着却没有这只女仆 -> 她已经不在了
        for (MaidInfo info : data.allInfos()) {
            if (alive.contains(info.getEntityId())) {
                continue;
            }
            ServerLevel level = levels.get(info.getDimension());
            if (level == null) {
                // 这个维度已经不在了（比如自定义维度被移除），记录留着没有意义
                data.removeInfo(info.getOwnerId(), info.getEntityId());
                continue;
            }
            if (level.isLoaded(info.getChunkPos())) {
                data.removeInfo(info.getOwnerId(), info.getEntityId());
            }
        }
    }
}
