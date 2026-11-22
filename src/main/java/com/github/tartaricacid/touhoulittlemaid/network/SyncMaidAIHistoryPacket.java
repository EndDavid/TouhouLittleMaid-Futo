package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.HistoryChat;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 把某只女仆的历史对话发给玩家（S2C）。
 *
 * <p>载荷是「条数 + 每条的 role 名 + 文本」，按时间从旧到新排。</p>
 */
public final class SyncMaidAIHistoryPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "sync_maid_ai_history");

    private SyncMaidAIHistoryPacket() {
    }

    /**
     * 客户端缓存。打开历史界面时读它，避免开界面后再等一次往返。
     */
    @Environment(EnvType.CLIENT)
    public static final List<String[]> CLIENT_HISTORY = new ArrayList<>();

    public static void send(ServerPlayer player, int entityId, EntityMaid maid) {
        List<HistoryChat> history = new ArrayList<>(maid.getAiChatManager().getHistory().getDeque());
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeInt(entityId);
        buf.writeVarInt(history.size());
        for (HistoryChat chat : history) {
            buf.writeUtf(chat.role().name());
            buf.writeUtf(chat.message());
        }
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void initClient() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(CHANNEL,
                (client, handler, buf, responseSender) -> {
                    buf.readInt();
                    int size = buf.readVarInt();
                    int safeSize = Math.min(Math.max(size, 0), 512);
                    List<String[]> history = new ArrayList<>(safeSize);
                    for (int i = 0; i < safeSize; i++) {
                        String role = buf.readUtf();
                        String message = buf.readUtf();
                        history.add(new String[]{role, message});
                    }
                    client.execute(() -> {
                        CLIENT_HISTORY.clear();
                        CLIENT_HISTORY.addAll(history);
                        if (client.screen instanceof com.github.tartaricacid.touhoulittlemaid.client.screen.ai.AIHistoryScreen screen) {
                            screen.refresh();
                        } else {
                            client.setScreen(new com.github.tartaricacid.touhoulittlemaid.client.screen.ai.AIHistoryScreen());
                        }
                    });
                });
    }
}