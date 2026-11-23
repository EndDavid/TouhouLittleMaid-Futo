package com.github.tartaricacid.touhoulittlemaid.network;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.HistoryChat;
import com.github.tartaricacid.touhoulittlemaid.client.screen.ai.AIHistoryScreen;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import java.util.ArrayList;
import java.util.List;
/**
 * 把某只女仆的历史对话发给玩家（S2C）。
 *
 * <p>载荷：条数 + 每条的 {@code role 名 / 文本 / 游戏刻}，按时间从旧到新排。
 * 游戏刻是历史界面上那句「x小时前」的唯一来源 —— 客户端与传的是同一个
 * {@code level.getGameTime()}，换算才能对齐（见 {@code HistoryChatWidget}）。</p>
 */
public final class SyncMaidAIHistoryPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "sync_maid_ai_history");
    private SyncMaidAIHistoryPacket() {
    }
    /**
     * 一条历史记录的传输形态。
     *
     * @param role      {@code Role} 的枚举名（USER / ASSISTANT / …），客户端按名字反查
     * @param message   原始文本；助手侧是模型的 JSON 原文，界面显示前会取出 chat_text
     * @param gameTime  说话那一刻的 {@code level.getGameTime()}
     */
    public record Entry(String role, String message, long gameTime) {
    }
    /**
     * 客户端缓存。打开历史界面时读它，避免开界面后再等一次往返。
     */
    @Environment(EnvType.CLIENT)
    public static final List<Entry> CLIENT_HISTORY = new ArrayList<>();
    public static void send(ServerPlayer player, int entityId, EntityMaid maid) {
        // 历史队列是「add = offerFirst」，迭代顺序是新→旧；
        // 界面要从上往下按时间排，所以用 descendingIterator 反过来发
        List<HistoryChat> history = new ArrayList<>();
        maid.getAiChatManager().getHistory().getDeque().descendingIterator().forEachRemaining(history::add);
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeInt(entityId);
        buf.writeVarInt(history.size());
        for (HistoryChat chat : history) {
            buf.writeUtf(chat.role().name());
            buf.writeUtf(chat.message());
            buf.writeLong(chat.gameTime());
        }
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }
    @Environment(EnvType.CLIENT)
    public static void initClient() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(CHANNEL,
                (client, handler, buf, responseSender) -> {
                    int entityId = buf.readInt();
                    int size = buf.readVarInt();
                    int safeSize = Math.min(Math.max(size, 0), 512);
                    List<Entry> history = new ArrayList<>(safeSize);
                    for (int i = 0; i < safeSize; i++) {
                        String role = buf.readUtf();
                        String message = buf.readUtf();
                        long gameTime = buf.readLong();
                        history.add(new Entry(role, message, gameTime));
                    }
                    client.execute(() -> {
                        CLIENT_HISTORY.clear();
                        CLIENT_HISTORY.addAll(history);
                        if (client.screen instanceof AIHistoryScreen screen) {
                            screen.refresh();
                            return;
                        }
                        // 只有「确实是这只女仆」时才开界面：服务端会先复核归属，
                        // 但客户端不该因为一个迟到的包就弹出别人的记录
                        Entity entity = client.level == null ? null : client.level.getEntity(entityId);
                        if (entity instanceof EntityMaid maid) {
                            Minecraft.getInstance().setScreen(new AIHistoryScreen(maid));
                        }
                    });
                });
    }
}
