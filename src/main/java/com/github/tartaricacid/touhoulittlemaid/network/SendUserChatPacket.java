package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.apache.commons.lang3.StringUtils;

/**
 * 「玩家对某只女仆说了一句话」的 C2S 包，移植自上游 {@code network/message/SendUserChatMessage}。
 *
 * <p>载荷与上游一致：{@code maidId} + 文本 + 语言。</p>
 *
 * <h2>服务端复核（对应上游字节码逐行）</h2>
 * <ol>
 *     <li>实体存在且确实是女仆；</li>
 *     <li>女仆活着；</li>
 *     <li><b>发出请求的玩家就是她的主人</b> —— 少了这一条，任何人都能让别人的女仆
 *         花别人 API key 的额度说话。</li>
 * </ol>
 * <p>三项任一不满足就静默丢弃：界面上玩家看不到任何变化，但服务端不该为此刷日志
 * （玩家可能只是关界面的瞬间按了回车）。</p>
 *
 * <p>{@code language} 是客户端当前语言的<i>显示名</i>（如 {@code English (US)}），
 * 直接拼进 prompt 里，因此不能由服务端推断 —— 服务端的 {@code Locale} 与玩家无关。</p>
 */
public final class SendUserChatPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "send_user_chat");

    private SendUserChatPacket() {
    }

    @Environment(EnvType.CLIENT)
    public static void send(int maidId, String message, String language) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeInt(maidId);
        buf.writeUtf(StringUtils.defaultString(message));
        buf.writeUtf(StringUtils.defaultString(language));
        ClientPlayNetworking.send(CHANNEL, buf);
    }

    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    // 缓冲区出回调即失效，三个字段必须在 execute 之前读完
                    int maidId = buf.readInt();
                    String message = buf.readUtf();
                    String language = buf.readUtf();
                    server.execute(() -> handle(player, maidId, message, language));
                });
    }

    private static void handle(ServerPlayer player, int maidId, String message, String language) {
        if (StringUtils.isBlank(message)) {
            return;
        }
        Entity entity = player.level.getEntity(maidId);
        if (!(entity instanceof EntityMaid maid) || !maid.isAlive()) {
            return;
        }
        if (!maid.isOwnedByPlayer(player)) {
            // 不是她的主人：既不该替别人花钱，也不该看到回复
            return;
        }
        // 文本长度上限与界面输入框一致（EditBox#setMaxLength(128)），
        // 这里再夹一次：客户端可以被伪造，不能让一个包把整段小说塞进 prompt。
        String trimmed = message.length() > 128 ? message.substring(0, 128) : message;
        maid.getAiChatManager().chat(trimmed, language);
    }
}

