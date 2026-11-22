package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.AIConfig;
import com.github.tartaricacid.touhoulittlemaid.data.ChatTokensData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 玩家累计 token 用量的同步包（S2C），对应 1.20.1 界面顶部那行 Token 计数。
 *
 * <p>登录时发一次，之后每次聊天回包再用一次，保证计数不会落后。</p>
 */
public final class SyncChatTokensPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "sync_chat_tokens");

    private SyncChatTokensPacket() {
    }

    /**
     * 客户端缓存的「当前 / 上限」，界面直接读它们。
     * <p>负的上限表示不限制（显示为无穷）。</p>
     */
    @Environment(EnvType.CLIENT)
    public static int CLIENT_CURRENT = 0;
    @Environment(EnvType.CLIENT)
    public static int CLIENT_MAX = -1;

    public static void sendTo(ServerPlayer player) {
        ChatTokensData data = ChatTokensData.get(player.getServer());
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeInt(data.get(player.getUUID()));
        buf.writeInt(maxTokens());
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }

    private static int maxTokens() {
        try {
            if (AIConfig.MAX_TOKENS_PER_PLAYER != null) {
                Integer value = AIConfig.MAX_TOKENS_PER_PLAYER.get();
                if (value != null && value > 0) {
                    return value;
                }
            }
        } catch (Exception ignored) {
            // 配置尚未加载：按不限制处理
        }
        return -1;
    }

    @Environment(EnvType.CLIENT)
    public static void initClient() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(CHANNEL,
                (client, handler, buf, responseSender) -> {
                    int current = buf.readInt();
                    int max = buf.readInt();
                    client.execute(() -> {
                        CLIENT_CURRENT = current;
                        CLIENT_MAX = max;
                    });
                });
    }
}