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
 * 清除某只女仆的 AI 聊天历史（C2S），对应 1.20.1 的 {@code ClearMaidAIDataMessage}。
 *
 * <p>只清「历史对话」这一项：站点 / 模型 / 温度 / 主人称呼 / 自定义设定都不动。
 * 上游那个包会连带清掉压缩摘要，本移植版还没有摘要字段，所以只清队列。</p>
 *
 * <p>服务端复核与 {@link SendUserChatPacket} 一致：实体在、活着、且<b>请求者是主人</b>。
 * 少了最后一条，任何人都能抹掉别人女仆的对话记忆。</p>
 */
public final class ClearMaidAIChatHistoryPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "clear_maid_ai_chat_history");

    private ClearMaidAIChatHistoryPacket() {
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
        maid.getAiChatManager().clearHistory();
    }
}

