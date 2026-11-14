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
 *
 * <h2>为什么需要这个类（上游没有）</h2>
 * <p>仆从铃与小号的「她不在加载范围内」那一半，全靠 {@code MaidWorldData} 里的
 * 位置记录。上游在 {@code EntityMaid#onRemovedFromWorld()} 里写、在
 * {@code #onAddedToWorld()} 里删 —— 这两个是 <b>Forge 给 {@code Entity} 打的补丁</b>，
 * 原版 1.18.2 里<b>并不存在</b>（已用 javap 核对 {@code net.minecraft.world.entity.Entity}，
 * 两个方法都没有）。Fabric API 0.77 也没有对应的实体加载 / 卸载事件
 * （{@code fabric-entity-events-v1} 1.4.9 里没有 {@code ServerEntityEvents}，
 * 那是后来的版本才加的）。</p>
 *
 * <p>上游自己也知道原版缺这个钩子：它的 {@code PersistentEntitySectionManagerMixin}
 * 就是为此写的（在区块卸载前把女仆传送回玩家）。那条路要 Mixin 进
 * {@code PersistentEntitySectionManager} 的私有字段与 {@code processUnloads}，
 * 风险高、还依赖命名细节。</p>
 *
 * <h2>本移植版的做法</h2>
 * <p><b>每 3 秒刷新一次在加载中的女仆位置，读取时按「她所在区块是否已加载」过滤。</b></p>
 * <ul>
 *     <li>写入侧：本类。频率 {@link #INTERVAL_TICKS}，每次只覆盖同一条记录
 *         （{@code MaidWorldData#addInfo} 已改成 upsert）。</li>
 *     <li>读取侧：{@code MaidWorldData#getUnloadedMaidInfos}。区块已加载的记录会被
 *         过滤掉，所以「站在你面前的女仆」不会被当成「在未加载区块」。</li>
 * </ul>
 *
 * <p>效果与上游一致：区块卸载后，铃 / 小号能拿到她<b>卸载前最多 3 秒</b>的位置。
 * 女仆在未加载期间不会移动，所以这 3 秒的误差在本功能里看不出来。</p>
 *
 * <h2>顺带做的清理</h2>
 * <p>女仆死掉后记录会变成「幽灵」：她已经不在了，铃却还指着一个位置。
 * 上游靠 {@code onRemovedFromWorld} 的 {@code isAlive()} 判断避免，本移植版改为：
 * <b>记录所在区块已加载、但那只女仆不在该维度的实体列表里</b> —— 说明她确实没了，
 * 删掉。这个判据是精确的：区块加载着，她却不在，那她只能是死了或被移除。</p>
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
