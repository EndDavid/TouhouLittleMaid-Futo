package com.github.tartaricacid.touhoulittlemaid.network;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
/**
 * 把这只女仆的角色设定正文发给玩家（S2C）。
 *
 * <p>空串表示这只模型还没有设定文件（还没自动生成，或被删掉了）。</p>
 */
public final class SyncMaidAISettingPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "sync_maid_ai_setting");
    private SyncMaidAISettingPacket() {
    }
    /** 客户端缓存的正文。 */
    @Environment(EnvType.CLIENT)
    public static String CLIENT_SETTING = "";
    public static void send(ServerPlayer player, int entityId, String rawSetting) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeInt(entityId);
        buf.writeUtf(rawSetting == null ? "" : rawSetting);
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }
    @Environment(EnvType.CLIENT)
    public static void initClient() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(CHANNEL,
                (client, handler, buf, responseSender) -> {
                    buf.readInt();
                    String text = buf.readUtf();
                    client.execute(() -> {
                        CLIENT_SETTING = text;
                        if (client.screen instanceof
                                com.github.tartaricacid.touhoulittlemaid.client.screen.ai.AISettingEditScreen screen) {
                            screen.refresh();
                        }
                    });
                });
    }
}
