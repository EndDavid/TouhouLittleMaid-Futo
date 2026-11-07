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
 *
 * <p>上游对应 {@code event/CapabilityEvent} 里的两段：
 * {@code PlayerEvent.Clone}（死亡扣灵力）与 {@code TickEvent.PlayerTickEvent}
 * （脏了就发 {@code SyncCapabilityMessage}）。</p>
 *
 * <h3>死亡惩罚为什么拆成两个回调</h3>
 * <p>上游在 clone 事件里判断 {@code isWasDeath()}，然后写
 * {@code newPower = oldPower - PLAYER_DEATH_LOSS_POWER_POINT}。本移植版的灵力是
 * <b>玩家实体上的字段</b>，重生时会换一个新 {@code ServerPlayer} 实例，
 * 所以同样离不开「复制」这一步。这里把它拆得更明确：</p>
 * <ol>
 *     <li>{@code ALLOW_DEATH}：在<b>旧</b>玩家身上扣掉配置值
 *         （即使玩家不复活就退出，扣减也不会丢）；</li>
 *     <li>{@code COPY_FROM}：无论如何把旧玩家的值抄给新玩家。</li>
 * </ol>
 * <p>两步合起来的净效果与上游一致：死亡失去 {@code PlayerDeathLossPowerPoint}，
 * 其余保留；{@code keepInventory} 打开时不扣。</p>
 *
 * <p><b>关于「死亡后无论如何都会失去所有 P 点」这条 wiki 说法：</b>上游代码里
 * 扣的是<b>配置值</b>（默认 1.00），不是"全部"。以代码为准 —— 默认扣 1.00，
 * 把配置调到 5.00 才等价于"全丢"。</p>
 *
 * <p><b>为什么用 {@code ALLOW_DEATH} 而不是 {@code ServerLivingEntityEvents#AFTER_DEATH}：</b>
 * Fabric API 0.77 对应的 {@code fabric-entity-events-v1} 是 <b>1.4.9</b>，
 * 这个版本里还没有 {@code ServerLivingEntityEvents}（它是后续版本才加的）。
 * {@code ServerPlayerEvents} 在这一版里已经齐全，且 {@code ALLOW_DEATH} 正好
 * 在玩家血量归零的那一刻回调，语义足够。</p>
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
