package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.sound.data.MaidAISoundInstance;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * 「女仆说话了，把她这段语音发给附近客户端播放」的 S2C 包，
 * 移植自上游 {@code network/message/TTSAudioToClientMessage}。
 *
 * <p><b>为什么不在服务端解码：</b>服务端没有 OpenAL，也不该为每个玩家合成音频。
 * 上游的做法是把 TTS 返回的 ogg 字节原样扔给客户端，由客户端解成 PCM 后交给
 * 原版的声音引擎播放，这样就自动获得了 3D 音效、音量设置与听者位置。</p>
 *
 * <p><b>与上游一致的两个细节：</b></p>
 * <ul>
 *     <li>只发给 {@code 16} 格内的玩家（上游 {@code NetworkHandler#sendToNearby} 的半径）；</li>
 *     <li>包体是 {@code 女仆实体 id + 音频字节}，客户端靠 id 找实体，而不是靠坐标。</li>
 * </ul>
 */
public final class TTSAudioToClientPacket {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "tts_audio_to_client");

    private static final double NEARBY_RADIUS = 16.0D;

    private TTSAudioToClientPacket() {
    }

    /**
     * 服务端调用：把这段音频发给女仆附近的玩家。
     */
    public static void sendNearby(EntityMaid maid, byte[] data) {
        if (!(maid.level instanceof ServerLevel serverLevel)) {
            return;
        }
        FriendlyByteBuf base = new FriendlyByteBuf(Unpooled.buffer());
        base.writeVarInt(maid.getId());
        base.writeByteArray(data);
        byte[] payload = new byte[base.readableBytes()];
        base.readBytes(payload);
        base.release();
        double radiusSqr = NEARBY_RADIUS * NEARBY_RADIUS;
        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(maid) > radiusSqr) {
                continue;
            }
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeBytes(payload);
            ServerPlayNetworking.send(player, CHANNEL, buf);
        }
    }

    /**
     * 客户端接收：找到那只女仆，交给声音引擎播一段流。
     */
    @Environment(EnvType.CLIENT)
    public static void initClient() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(CHANNEL,
                (client, handler, buf, responseSender) -> {
                    int maidId = buf.readVarInt();
                    byte[] data = buf.readByteArray();
                    client.execute(() -> play(client, maidId, data));
                });
    }

    @Environment(EnvType.CLIENT)
    private static void play(Minecraft client, int maidId, byte[] data) {
        if (client.level == null || data.length == 0) {
            return;
        }
        Entity entity = client.level.getEntity(maidId);
        if (!(entity instanceof EntityMaid maid) || !maid.isAlive()) {
            return;
        }
        client.getSoundManager().play(new MaidAISoundInstance(maid, data));
    }
}

