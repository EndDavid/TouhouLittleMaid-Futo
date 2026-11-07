package com.github.tartaricacid.touhoulittlemaid.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * 每 tick 更新「被主人抱着」的女仆位置 —— 客户端那一半的<b>兜底</b>。
 *
 * <p><b>为什么需要它：</b>乘客的坐标<b>不由服务端同步</b>
 * （{@code ServerEntity} 只发「谁骑谁」这个关系），客户端得自己算。
 * 原版算位置的地方是 {@code Entity#rideTick()} → {@code getVehicle().positionRider(this)}，
 * 而 {@code rideTick()} 只在 {@code Level#tickPassenger(载具, 乘客)} 里被调到。
 * 本地玩家在客户端并不一定走那条 tick 路径，于是女仆的位置可能<b>根本没人更新</b> ——
 * 表现就是「抱起后模型留在原地，放下时才瞬移过来」。</p>
 *
 * <p>所以这里做两件事：</p>
 * <ol>
 *     <li>遍历本地玩家 / 全服玩家的<b>乘客</b>，正常刷一遍（与上游 {@code positionRider} 同效）；</li>
 *     <li>再扫描玩家附近「{@code carrierId != 0}」的女仆 —— 这条路径
 *         <b>完全不依赖乘客关系</b>。即使客户端没收到 / 没能复现乘客关系，
 *         只要同步数据里有「谁抱着她」，位置就一定会被算出来。</li>
 * </ol>
 */
public final class MaidCarryTicker {
    private MaidCarryTicker() {
    }

    /**
     * 客户端：本地玩家的乘客 + 附近带 carrierId 的女仆。
     */
    public static void initClient() {
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            if (minecraft.level == null || minecraft.player == null) {
                return;
            }
            tickPassengers(minecraft.player);
            // 兜底扫描：半径 8 格足够 —— 她每 tick 都被贴回玩家身前 0.75 格
            AABB area = minecraft.player.getBoundingBox().inflate(8.0D);
            for (Entity entity : minecraft.level.getEntities(minecraft.player, area,
                    e -> e instanceof EntityMaid maid && maid.getCarrierId() != 0)) {
                ((EntityMaid) entity).updateCarriedPosition();
            }
        });
    }

    /**
     * 服务端：全服每个玩家的乘客。
     */
    public static void initServer() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                tickPassengers(player);
            }
        });
    }

    private static void tickPassengers(Player player) {
        for (Entity passenger : player.getPassengers()) {
            if (passenger instanceof EntityMaid maid) {
                maid.updateCarriedPosition();
            }
        }
    }
}

