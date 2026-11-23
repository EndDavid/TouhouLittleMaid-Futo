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
 * <p>载荷：{@code maidId} + 文本 + 语言 + 女仆名字。上游 1.20.1 是把它打包成
 * {@code ChatClientInfo(language, name, description)} 一起发上来（见
 * {@code ai/manager/entity/ChatClientInfo#fromMaid}），这里只取 AI 人设真正用得到的两项。
 * 名字与语言一样必须由客户端提供：服务端没有玩家的语言文件，
 * 未命名女仆的名字（模型译名 {@code {model.xxx.name}}）在服务端解析出来只是键名。</p>
 */
public final class SendUserChatPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "send_user_chat");

    private SendUserChatPacket() {
    }

    /**
     * @param name 女仆名字的<i>已翻译</i>文本。
     *             未命名时是模型译名（如「博丽灵梦」），由客户端算好后带上来 ——
     *             服务端没有玩家的语言文件，自己解析 {@code {model.xxx.name}} 只会拿到键名。
     */
    @Environment(EnvType.CLIENT)
    public static void send(int maidId, String message, String language, String name) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeInt(maidId);
        buf.writeUtf(StringUtils.defaultString(message));
        buf.writeUtf(StringUtils.defaultString(language));
        buf.writeUtf(StringUtils.defaultString(name));
        ClientPlayNetworking.send(CHANNEL, buf);
    }

    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    // 缓冲区出回调即失效，四个字段必须在 execute 之前读完
                    int maidId = buf.readInt();
                    String message = buf.readUtf();
                    String language = buf.readUtf();
                    String name = buf.readUtf();
                    server.execute(() -> handle(player, maidId, message, language, name));
                });
    }

    private static void handle(ServerPlayer player, int maidId, String message, String language, String name) {
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
        maid.getAiChatManager().chat(trimmed, language, name);
    }
}