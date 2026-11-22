package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIDataSerializable;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * 「保存这只女仆的 AI 设置」的 C2S 包，移植自上游 {@code network/message/SaveMaidAIDataMessage}。
 *
 * <p>载荷：{@code entityId} + 8 个设置字段（顺序由
 * {@link MaidAIDataSerializable#encode} 决定，两边共用这一类以保证不会错位）。</p>
 *
 * <p>服务端同样复核「存在 / 活着 / 是她的主人」，然后整份 {@code copyFrom}。
 * 与上游一致，这里不校验站点名是否存在于站点表 —— 填错了就回退到第一个可用站点
 * （见 {@code MaidAIChatData#getChatSite}），比拒绝保存更好排查。</p>
 */
public final class SaveMaidAIDataPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "save_maid_ai_data");

    private SaveMaidAIDataPacket() {
    }

    /**
     * 客户端调用。{@code data} 通常就是这只女仆本地那份（客户端拿到的副本）。
     */
    public static void send(int entityId, MaidAIDataSerializable data) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeInt(entityId);
        data.encode(buf);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(CHANNEL, buf);
    }

    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    int entityId = buf.readInt();
                    MaidAIDataSerializable data = new MaidAIDataSerializable();
                    data.decode(buf);
                    server.execute(() -> handle(player, entityId, data));
                });
    }

    private static void handle(ServerPlayer player, int entityId, MaidAIDataSerializable data) {
        Entity entity = player.level.getEntity(entityId);
        if (!(entity instanceof EntityMaid maid) || !maid.isAlive()) {
            return;
        }
        if (!maid.isOwnedByPlayer(player)) {
            return;
        }
        maid.getAiChatManager().copyFrom(data);
    }
}

