package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.sound.SystemNarrator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 「用系统旁白朗读这句话」的 S2C 包，移植自上游 {@code network/message/TTSSystemAudioToClientMessage}。
 *
 * <p>站点 {@code api_type: system} 不经过任何 HTTP：服务端只把要朗读的文本发给主人，
 * 客户端调用 {@code com.mojang.text2speech}（系统 TTS）念出来。</p>
 *
 * <p>只发给主人本人，而不是附近玩家 —— 系统旁白是「玩家自己的耳朵」，
 * 不该替别人朗读；这一点与 {@link TTSAudioToClientPacket} 的「16 格广播」不同。</p>
 */
public final class TTSSystemAudioToClientPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "tts_system_audio_to_client");

    private TTSSystemAudioToClientPacket() {
    }

    public static void sendTo(ServerPlayer player, String text) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(text);
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void initClient() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(CHANNEL,
                (client, handler, buf, responseSender) -> {
                    String text = buf.readUtf();
                    client.execute(() -> SystemNarrator.say(text));
                });
    }
}

