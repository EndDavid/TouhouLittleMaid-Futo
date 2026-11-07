package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 服务端 → 客户端的灵力同步包，上游 {@code SyncCapabilityMessage} 的简化版。
 *
 * <p>上游那个包同时同步「灵力」与「女仆数量」两个数值；本移植版的
 * {@code MaidNumCapability} 是<b>按需统计</b>的（没有持久化、也不给客户端用），
 * 所以这里只带一个 float。</p>
 *
 * <p><b>为什么必须有它：</b>灵力存在玩家实体上（见 {@code PlayerPowerMixin}），
 * 客户端自己算不出来。HUD 要在手持御币时显示 {@code ×1.23}，只能靠服务端推。
 * 与上游一致：只在 {@code dirty} 时推，而不是每 tick 都发。</p>
 */
public final class PowerSyncPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "power_sync");

    private PowerSyncPacket() {
    }

    /**
     * 服务端调用：把灵力值推给指定玩家。
     */
    public static void send(ServerPlayer player, float power) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeFloat(power);
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }

    /**
     * 客户端接收后写回本地玩家。
     *
     * <p>写成静态方法而不是 lambda，是为了让 {@code ClientPlayNetworking} 只在客户端
     * 初始化器里被引用一次，避免专用服务端加载到客户端类。</p>
     */
    public static void applyTo(Player player, float power) {
        if (player instanceof com.github.tartaricacid.touhoulittlemaid.capability.IPower holder) {
            holder.getPower().set(power);
            // 客户端不需要再往服务端推，清掉脏位避免无意义的重发
            holder.getPower().setDirty(false);
        }
    }
}

