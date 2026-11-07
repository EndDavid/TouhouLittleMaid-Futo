package com.github.tartaricacid.touhoulittlemaid.capability;

import net.minecraft.world.entity.player.Player;

/**
 * 「身上带灵力」的标记接口，由 {@code mixin/PlayerPowerMixin} 织入
 * {@link Player}。
 *
 * <p>上游等价物是 Forge 的 {@code player.getCapability(PowerCapabilityProvider.POWER_CAP)}
 * —— 拿不到就说明对方没有这个能力（理论上不会发生）。本移植版因为对象一定存在于
 * 每个 {@code Player} 实例上，所以返回值非空；但为了贴近上游的调用习惯，
 * 仍提供静态的 {@link #getPower(Player)} 便捷入口。</p>
 *
 * <p><b>客户端也有实例</b>：服务端通过 {@code PowerSyncPacket} 把值写进本地实例，
 * HUD（{@code client/event/ShowPowerEvent}）直接读它 —— 与上游
 * {@code SyncCapabilityMessage} 的做法一致。</p>
 */
public interface IPower {
    /**
     * 取本实体身上的灵力对象（永不为 null）。
     */
    PowerCapability getPower();

    /**
     * 便捷入口：给非 mixin 调用方用。
     *
     * @return 玩家身上的灵力对象；传入 {@code null} 时返回一个临时对象，
     *         这样调用方不用到处判空（与原版「capability 缺失时静默跳过」等价，
     *         因为写入临时对象不会影响任何人）。
     */
    static PowerCapability getPower(Player player) {
        if (player instanceof IPower holder) {
            return holder.getPower();
        }
        return new PowerCapability();
    }
}

