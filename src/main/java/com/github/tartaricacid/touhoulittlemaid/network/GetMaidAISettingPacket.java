package com.github.tartaricacid.touhoulittlemaid.network;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.CharacterSetting;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.SettingReader;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import java.util.Optional;
/**
 * 请求这只女仆的角色设定正文（C2S），供角色设定编辑器显示。
 *
 * <p>正文是服务端从 settings/*.yml 读出来的原文（已去掉注释与空行），
 * 客户端没有这份数据，所以打开编辑器时现要一次。</p>
 */
public final class GetMaidAISettingPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "get_maid_ai_setting");
    private GetMaidAISettingPacket() {
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
        Optional<CharacterSetting> setting = SettingReader.getSetting(maid.getModelId());
        SyncMaidAISettingPacket.send(player, entityId,
                setting.map(CharacterSetting::getRawSetting).orElse(""));
    }
}
