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
 * 让模型重写一份角色设定（C2S）。
 *
 * <p>服务端复核归属后调用 MaidAIChatManager#regenerateSetting；
 * 已有同名设定文件时不会覆盖，只会再弹一次「角色设定为空」的提示。</p>
 */
public final class RegenerateAISettingPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "regenerate_ai_setting");
    private RegenerateAISettingPacket() {
    }
    /**
     * @param name 女仆名字的已翻译文本（未命名时是模型译名），与
     *             {@link SendUserChatPacket} 同一个理由：只有客户端能翻译。
     */
    public static void send(int entityId, String language, String name) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeInt(entityId);
        buf.writeUtf(language == null ? "English (US)" : language);
        buf.writeUtf(name == null ? "" : name);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(CHANNEL, buf);
    }
    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    int entityId = buf.readInt();
                    String language = buf.readUtf();
                    String name = buf.readUtf();
                    server.execute(() -> handle(player, entityId, language, name));
                });
    }
    private static void handle(ServerPlayer player, int entityId, String language, String name) {
        Entity entity = player.level.getEntity(entityId);
        if (!(entity instanceof EntityMaid maid) || !maid.isAlive() || !maid.isOwnedByPlayer(player)) {
            return;
        }
        maid.getAiChatManager().regenerateSetting(language, name);
    }
}
