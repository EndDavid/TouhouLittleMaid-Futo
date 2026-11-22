package com.github.tartaricacid.touhoulittlemaid.network;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
/**
 * 请求全部站点列表（C2S），打开设置界面时发一次。
 */
public final class GetSiteListPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "get_site_list");
    private GetSiteListPacket() {
    }
    public static void send() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(CHANNEL, PacketByteBufs.create());
    }
    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL,
                (server, player, handler, buf, responseSender) ->
                        server.execute(() -> SyncSiteListPacket.sendTo(player)));
    }
}
