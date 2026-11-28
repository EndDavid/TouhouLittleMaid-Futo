package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 钓鱼链路的诊断输出：把「为什么没在钓鱼」这件事在各环节各打一条日志。
 *
 * <h2>为什么需要</h2>
 * <p>钓鱼从「找坐具」到「抛钩」再到「客户端渲染」一共四段，<b>每一段失败都是完全静默的</b>：
 * 玩家能观察到的只有「她坐在船里一动不动」或「她站在水边不动」。四段分别是：</p>
 * <ol>
 *     <li>本站 {@code MaidFindSitTask} —— 找不到坐具 / 走到旁边但骑不上；</li>
 *     <li>{@code MaidUpdateActivityFromSchedule} —— 骑上了，但当前不是上班时段，
 *         活动不是 {@code RIDE_WORK}（它还负责把非工作点任务的女仆赶下船）；</li>
 *     <li>{@code MaidRideFindWaterTask} —— 活动对了，但没有水 / 主手没鱼竿 / 水超出活动范围；</li>
 *     <li>客户端 —— {@code MaidFishingHook} 拿不到 owner 时不画浮标（早期版本会直接自杀）。</li>
 * </ol>
 * <p>前三条统一前缀 {@code [Futo]}，按 {@code grep "\[Futo\]"} 就能把整条链路串起来看。</p>
 *
 * <h2>去重</h2>
 * <p>每只女仆每条通道只记住「上次说过的那句话」，内容没变就不打 —— 这些行为都是按
 * 12~100 tick 的间隔反复尝试的，不去重会刷屏。表上限 128 条，超出整体清空：
 * 丢一条诊断比泄漏内存划算。</p>
 */
final class MaidFishingDiagnostics {
    /**
     * 诊断通道：找坐具。与 {@code MaidRideFindWaterTask} 的 {@code STATE_*} 互不干扰。
     */
    private static final String CHANNEL_SIT = "sit";

    private static final Map<UUID, Map<String, String>> LAST = new HashMap<>();

    private MaidFishingDiagnostics() {
    }

    /**
     * 报告「找坐具」这一步的结果。
     *
     * @param seat   涉及的坐具，找不到时为 {@code null}
     * @param detail 说明，例如 {@code mounted} / {@code startRiding refused}
     */
    static void reportSit(EntityMaid maid, @Nullable Entity seat, String detail) {
        report(maid, CHANNEL_SIT, detail + (seat == null ? "" : " -> " + seat.getType().toString()
                                                                    + " at " + seat.blockPosition()));
    }

    private static void report(EntityMaid maid, String channel, String detail) {
        // 关闭时不记账（debug 级别默认关闭），整条诊断链路零开销
        if (!TouhouLittleMaid.LOGGER.isDebugEnabled()) {
            return;
        }
        UUID uuid = maid.getUUID();
        Map<String, String> perMaid = LAST.get(uuid);
        if (perMaid == null) {
            if (LAST.size() > 128) {
                LAST.clear();
            }
            perMaid = new HashMap<>(4);
            LAST.put(uuid, perMaid);
        }
        if (detail.equals(perMaid.get(channel))) {
            return;
        }
        perMaid.put(channel, detail);
        TouhouLittleMaid.LOGGER.debug("[Futo] fishing {} maid={} at={} -> {}",
                channel, uuid, maid.blockPosition(), detail);
    }
}
