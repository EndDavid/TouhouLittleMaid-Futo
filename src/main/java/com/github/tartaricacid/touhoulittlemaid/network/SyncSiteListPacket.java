package com.github.tartaricacid.touhoulittlemaid.network;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.AvailableSites;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * 把 available_sites.yml 的<b>全部</b>站点发给玩家（S2C），供设置界面编辑。
 *
 * <p>与 {@link SyncAiSettingPacket} 的区别：那个只发「填了 api_key、能直接用」的站点，
 * 这个发全部（含未填 key 的），因为设置界面要能编辑/删除每一条。</p>
 *
 * <p><b>密钥会一起发下去。</b>这是 1.20.1 的做法（站点评测界面要能显示并修改 key），
 * 且只发给已在服务器里的玩家。</p>
 */
public final class SyncSiteListPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "sync_site_list");
    private SyncSiteListPacket() {
    }
    /**
     * 一条站点：名称 / 类型（chat 或 tts）/ 接口类型 / 地址 / 密钥 / 模型列表。
     */
    public record Entry(String name, String type, String apiType, String url, String apiKey,
                        List<String> models, boolean enabled) {
    }
    /** 客户端缓存。 */
    @Environment(EnvType.CLIENT)
    public static final List<Entry> CLIENT_SITES = new ArrayList<>();
    public static void sendTo(ServerPlayer player) {
        Map<String, LinkedHashMap<String, Object>> raw = AvailableSites.getRawSites();
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(raw.size());
        raw.forEach((name, map) -> {
            buf.writeUtf(name);
            buf.writeUtf(str(map.get("type")));
            buf.writeUtf(str(map.get("api_type")));
            buf.writeUtf(str(map.get("url")));
            buf.writeUtf(str(map.get("api_key")));
            Object models = map.get("models");
            List<?> list = models instanceof List<?> l ? l : List.of();
            buf.writeVarInt(list.size());
            for (Object m : list) {
                buf.writeUtf(str(m));
            }
            buf.writeBoolean(AvailableSites.isEnabled(name));
        });
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }
    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }
    @Environment(EnvType.CLIENT)
    public static void initClient() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(CHANNEL,
                (client, handler, buf, responseSender) -> {
                    int size = buf.readVarInt();
                    int safeSize = Math.min(Math.max(size, 0), 256);
                    List<Entry> entries = new ArrayList<>(safeSize);
                    for (int i = 0; i < safeSize; i++) {
                        String name = buf.readUtf();
                        String type = buf.readUtf();
                        String apiType = buf.readUtf();
                        String url = buf.readUtf();
                        String apiKey = buf.readUtf();
                        int modelCount = Math.min(Math.max(buf.readVarInt(), 0), 256);
                        List<String> models = new ArrayList<>(modelCount);
                        for (int j = 0; j < modelCount; j++) {
                            models.add(buf.readUtf());
                        }
                        boolean enabled = buf.readBoolean();
                        entries.add(new Entry(name, type, apiType, url, apiKey, models, enabled));
                    }
                    client.execute(() -> {
                        CLIENT_SITES.clear();
                        CLIENT_SITES.addAll(entries);
                        if (client.screen instanceof
                                com.github.tartaricacid.touhoulittlemaid.client.screen.ai.AISiteListScreen screen) {
                            screen.refresh();
                        }
                    });
                });
    }
}
