package com.github.tartaricacid.touhoulittlemaid.client.gui.item;

import com.github.tartaricacid.touhoulittlemaid.network.WirelessIOPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 隙间 GUI 的客户端发包封装。
 *
 * <p>把 {@code ClientPlayNetworking} 的引用限制在这里，{@link WirelessIOPacket}
 * 就能留在 common 包中 —— 专用服务端不会去解析客户端类。</p>
 */
public final class WirelessIOClientPacketSender {
    private WirelessIOClientPacketSender() {
    }

    /**
     * 切换 I/O 方向与黑白名单。<b>两个值一起发</b>：
     * 上游也是这样（一个包带两个布尔），服务端一次写两个标签，
     * 免得两次往返之间出现「方向已改、过滤模式还没改」的中间态。
     */
    public static void sendGuiMode(boolean isMaidToChest, boolean isBlacklist) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeByte(WirelessIOPacket.SET_GUI_MODE);
        buf.writeBoolean(isMaidToChest);
        buf.writeBoolean(isBlacklist);
        ClientPlayNetworking.send(WirelessIOPacket.CHANNEL, buf);
    }

    /**
     * 提交新的槽位配置；传 {@code null} 或空数组表示「取消」——
     * 服务端只重开界面，不改 NBT。
     */
    public static void sendSlotConfig(byte[] config) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeByte(WirelessIOPacket.SET_SLOT_CONFIG);
        buf.writeByteArray(config == null ? new byte[0] : config);
        ClientPlayNetworking.send(WirelessIOPacket.CHANNEL, buf);
    }
}

