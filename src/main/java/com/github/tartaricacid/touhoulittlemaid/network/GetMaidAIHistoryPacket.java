package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * 请求某只女仆的对话历史（C2S），对应 1.20.1 HistoryAIChatScreen 的数据来源。
 *
 * <p>1.20.1 的聊天管理器整体同步到客户端，历史记录直接在客户端就能读；
 * 本移植版只同步设置、不同步历史（历史里有整段对话，没必要常驻），
 * 所以打开历史界面时现要一份。</p>
 */
public final class GetMaidAIHistoryPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "get_maid_ai_history");

    private GetMaidAIHistoryPacket() {
    }

    public static void send(int entityId) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeInt(entityId);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(CHANNEL, buf);
    }

    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    int entityId = buf.readInt();
                    server.execute(() -> handle(player, entityId));
                });
    }

    private static void handle(ServerPlayer player, int entityId) {
        Entity entity = player.level.getEntity(entityId);
        if (!(entity instanceof EntityMaid maid) || !maid.isAlive() || !maid.isOwnedByPlayer(player)) {
            return;
        }
        SyncMaidAIHistoryPacket.send(player, entityId, maid);
    }
}