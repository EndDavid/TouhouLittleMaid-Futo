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
 * 「请求这只女仆的 AI 设置」的 C2S 包，移植自上游 {@code network/message/GetMaidAIDataMessage}。
 *
 * <p>收到后由服务端回一份 {@link OpenMaidAIDataScreenPacket}（带完整设置）。
 * 为什么不让客户端直接读：设置里的站点 / 模型 / 温度只有服务端那份才是权威，
 * 客户端那份可能是登录时同步下来的旧快照。</p>
 */
public final class GetMaidAIDataPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "get_maid_ai_data");

    private GetMaidAIDataPacket() {
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
        if (!(entity instanceof EntityMaid maid) || !maid.isAlive()) {
            return;
        }
        if (!maid.isOwnedByPlayer(player)) {
            return;
        }
        // 回一份完整设置（含站点名 / 模型 / 温度 / TTS / 主人称呼 / 自定义设定）
        OpenMaidAIDataScreenPacket.send(player, entityId, maid.getAiChatManager());
    }
}

