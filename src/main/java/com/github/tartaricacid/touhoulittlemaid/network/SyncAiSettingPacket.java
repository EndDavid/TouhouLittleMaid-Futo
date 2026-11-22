package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.AvailableSites;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.SettingReader;
import com.github.tartaricacid.touhoulittlemaid.util.ByteBufUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 站点表与角色设定清单的同步包（S2C），移植自上游 SyncAiSettingMessage。
 *
 * <p>客户端要知道两件事，只有服务端知道：</p>
 * <ol>
 *     <li>哪些模型能聊天：取决于服务端加载到的角色设定文件。
 *         按 T 开界面的快捷键靠它判断，否则会对没有设定的女仆开界面并必然失败。</li>
 *     <li>有哪些站点与模型可选：设置界面的下拉框。
 *         只发站点名与模型列表，绝不发 api_key。</li>
 * </ol>
 *
 * <p>发送时机与上游相同：玩家登录，以及 /tlm ai_chat reload 之后。</p>
 */
public final class SyncAiSettingPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "sync_ai_setting");

    private SyncAiSettingPacket() {
    }

    /**
     * 组装当前状态并广播给全服（供 /tlm ai_chat reload 使用）。
     */
    public static void broadcast(MinecraftServer server) {
        Set<String> settings = new LinkedHashSet<>(SettingReader.getAllSettingKeys());
        Map<String, List<String>> chatSites = AvailableSites.getClientChatSites();
        Map<String, List<String>> ttsSites = AvailableSites.getClientTtsSites();
        for (ServerPlayer player : PlayerLookup.all(server)) {
            sendTo(player, settings, chatSites, ttsSites);
        }
    }

    /**
     * 给单个玩家发一份（登录时用）。
     */
    public static void sendTo(ServerPlayer player) {
        sendTo(player,
                new LinkedHashSet<>(SettingReader.getAllSettingKeys()),
                AvailableSites.getClientChatSites(),
                AvailableSites.getClientTtsSites());
    }

    private static void sendTo(ServerPlayer player, Set<String> settings,
                               Map<String, List<String>> chatSites, Map<String, List<String>> ttsSites) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        ByteBufUtils.writeStringSet(buf, settings);
        ByteBufUtils.writeSites(buf, chatSites);
        ByteBufUtils.writeSites(buf, ttsSites);
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void initClient() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(CHANNEL,
                (client, handler, buf, responseSender) -> {
                    // 缓冲区出回调即失效，三个集合必须在切回主线程之前解完
                    Set<String> settings = ByteBufUtils.readStringSet(buf);
                    Map<String, List<String>> chatSites = ByteBufUtils.readSites(buf);
                    Map<String, List<String>> ttsSites = ByteBufUtils.readSites(buf);
                    client.execute(() -> handle(settings, chatSites, ttsSites));
                });
    }

    @Environment(EnvType.CLIENT)
    private static void handle(Set<String> settings, Map<String, List<String>> chatSites,
                               Map<String, List<String>> ttsSites) {
        com.github.tartaricacid.touhoulittlemaid.client.screen.ai.AIChatScreen.CLIENT_CHAT_SITES.clear();
        com.github.tartaricacid.touhoulittlemaid.client.screen.ai.AIChatScreen.CLIENT_CHAT_SITES.putAll(chatSites);
        com.github.tartaricacid.touhoulittlemaid.client.screen.ai.AIChatScreen.CLIENT_TTS_SITES.clear();
        com.github.tartaricacid.touhoulittlemaid.client.screen.ai.AIChatScreen.CLIENT_TTS_SITES.putAll(ttsSites);
        com.github.tartaricacid.touhoulittlemaid.client.event.PressAIChatKeyEvent.CAN_CHAT_MAID_IDS.clear();
        com.github.tartaricacid.touhoulittlemaid.client.event.PressAIChatKeyEvent.CAN_CHAT_MAID_IDS.addAll(settings);
    }
}
