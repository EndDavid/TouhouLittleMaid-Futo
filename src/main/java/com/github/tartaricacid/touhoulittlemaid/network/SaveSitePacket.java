package com.github.tartaricacid.touhoulittlemaid.network;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.AvailableSites;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;
/**
 * 保存或删除一个站点（C2S），对应 1.20.1 的 SaveLLMSiteMessage / SaveTTSSiteMessage。
 *
 * <p>服务端复核「玩家有权限（等级 2）」后写入 available_sites.yml 并重新加载，
 * 再把新列表广播给所有在线玩家（各自界面即时刷新）。</p>
 */
public final class SaveSitePacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "save_site");
    private SaveSitePacket() {
    }
    /** 保存（新增或覆盖）。{@code enabled} 由界面里的开关决定。 */
    public static void sendSave(String name, String type, String apiType, String url,
                                String apiKey, List<String> models) {
        sendSave(name, type, apiType, url, apiKey, models, true);
    }

    /** 保存并显式指定 {@code enabled}（站点列表上的勾选框走这条）。 */
    public static void sendSave(String name, String type, String apiType, String url,
                                String apiKey, List<String> models, boolean enabled) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(true);
        buf.writeUtf(name);
        buf.writeUtf(type);
        buf.writeUtf(apiType);
        buf.writeUtf(url);
        buf.writeUtf(apiKey);
        buf.writeBoolean(enabled);
        buf.writeVarInt(models.size());
        for (String model : models) {
            buf.writeUtf(model);
        }
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(CHANNEL, buf);
    }
    /** 删除。 */
    public static void sendDelete(String name) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(false);
        buf.writeUtf(name);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(CHANNEL, buf);
    }
    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    boolean isSave = buf.readBoolean();
                    String name = buf.readUtf();
                    String type = isSave ? buf.readUtf() : "";
                    String apiType = isSave ? buf.readUtf() : "";
                    String url = isSave ? buf.readUtf() : "";
                    String apiKey = isSave ? buf.readUtf() : "";
                    boolean enabled = isSave && buf.readBoolean();
                    List<String> models = new ArrayList<>();
                    if (isSave) {
                        int count = Math.min(Math.max(buf.readVarInt(), 0), 256);
                        for (int i = 0; i < count; i++) {
                            models.add(buf.readUtf());
                        }
                    }
                    server.execute(() -> handle(player, isSave, name, type, apiType, url, apiKey,
                            enabled, models));
                });
    }
    private static void handle(ServerPlayer player, boolean isSave, String name, String type,
                               String apiType, String url, String apiKey, boolean enabled,
                               List<String> models) {
        // 会写磁盘上的配置文件，按管理操作处理（与 /tlm ai_chat reload 同级）
        if (!player.hasPermissions(2)) {
            return;
        }
        if (isSave) {
            AvailableSites.saveSite(name, type, apiType, url, apiKey, models, enabled);
        } else {
            AvailableSites.removeSite(name);
        }
        // 两个列表都刷新：站点表（选择用）与站点全集（编辑用）
        for (ServerPlayer online : net.fabricmc.fabric.api.networking.v1.PlayerLookup.all(player.getServer())) {
            SyncAiSettingPacket.sendTo(online);
            SyncSiteListPacket.sendTo(online);
        }
    }
}