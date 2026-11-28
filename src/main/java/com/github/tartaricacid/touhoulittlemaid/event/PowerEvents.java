package com.github.tartaricacid.touhoulittlemaid.event;

import com.github.tartaricacid.touhoulittlemaid.capability.IPower;
import com.github.tartaricacid.touhoulittlemaid.capability.PowerCapability;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MiscConfig;
import com.github.tartaricacid.touhoulittlemaid.network.PowerSyncPacket;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;

import java.util.UUID;

/**
 * 灵力的「死亡惩罚」与「脏数据同步」。
 */
public final class PowerEvents {
    /**
     * 「这一 tick 刚刚死亡」的玩家，避免同一次死亡被同一来源重复处理。
     *
     * <p>{@code ALLOW_DEATH} 每次致命伤都会回调；玩家的死亡流程里有可能出现
     * 多段伤害在同一 tick 内连续致命（例如爆炸 + 摔落），不设标记会扣两次。</p>
     */
    private static final java.util.Set<UUID> DEATH_HANDLED = new java.util.HashSet<>();

    private PowerEvents() {
    }

    public static void init() {
        // 死亡扣减：只在没开 keepInventory 时扣，且每次死亡只扣一次
        ServerPlayerEvents.ALLOW_DEATH.register((player, source, amount) -> {
            UUID id = player.getUUID();
            if (!DEATH_HANDLED.add(id)) {
                return true;
            }
            boolean keepInventory = player.level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
            if (!keepInventory) {
                IPower.getPower(player).min((float) MiscConfig.playerDeathLossPowerPoint());
            }
            return true;
        });

        // 重生 / 换维度时把旧玩家身上的数值抄给新玩家
        // （不判断 alive：扣减已在 ALLOW_DEATH 里做完，这里只管搬运）
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            IPower.getPower(newPlayer).set(IPower.getPower(oldPlayer).get());
            // 新实例已经「重生」过一次了，清掉标记，下次死亡照常扣
            DEATH_HANDLED.remove(newPlayer.getUUID());
        });

        // 脏数据同步：每个服务端 tick 末，把脏的玩家灵力推给对应客户端
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                PowerCapability power = IPower.getPower(player);
                if (power.isDirty()) {
                    power.setDirty(false);
                    PowerSyncPacket.send(player, power.get());
                }
            }
        });
    }
}
