package com.github.tartaricacid.touhoulittlemaid.client.sound.data;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.sound.OggReader;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.mojang.blaze3d.audio.OggAudioStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.sounds.SoundSource;

import java.io.ByteArrayInputStream;
import java.util.concurrent.CompletableFuture;

/**
 * 「跟着女仆走」的一段 AI 语音，移植自上游 {@code client/sound/data/MaidAISoundInstance}。
 *
 * <p>继承 {@link EntityBoundSoundInstance} 是为了免费拿到三件事：位置随实体更新、
 * 距离衰减、实体消失时自动停止。</p>
 *
 * <p><b>与上游的差异（1.18.2 Fabric 必需）：</b>上游直接覆写
 * {@code SoundInstance#getStream(SoundBufferLibrary, Sound, boolean)}，那是 Forge 给
 * 1.18.2 打的补丁；Fabric 的原版 {@code SoundInstance} 没有这个方法。
 * 本移植版把这段逻辑放在 {@link #createStream()} 里，由
 * {@code mixin/client/SoundEngineMixin} 在 {@code SoundEngine#play} 取流的位置换回来 ——
 * 效果与上游完全一致：仍然占用原版声音引擎的通道与音量设置。</p>
 *
 * <p><b>为什么保留「占位音效」：</b>注册的 {@code maid.ai_chat} 指向
 * {@code sounds/maid/empty.ogg}（一个极短的静音文件，且 {@code stream: true}），
 * 只为让 {@code SoundEngine} 走「流式播放」分支；真正的音频由 {@link #createStream()} 提供。
 * 一旦解码失败，会回退到这段静音，而不是让通道空转。</p>
 */
@Environment(EnvType.CLIENT)
public class MaidAISoundInstance extends EntityBoundSoundInstance {
    private final byte[] data;

    public MaidAISoundInstance(EntityMaid maid, byte[] data) {
        super(InitSounds.MAID_AI_CHAT, SoundSource.NEUTRAL, 1f, 1f, maid);
        this.data = data;
    }

    /**
     * 在后台线程解码这段音频，返回给声音引擎的流。
     *
     * <p>返回的 future 若以 {@code null} 完成，调用方（mixin）会退回原版静音流，
 * 也就是「这段语音放不出来」而不是崩溃。</p>
     */
    public CompletableFuture<AudioStream> createStream() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                OggReader.Type type = OggReader.getOggType(this.data);
                if (type == OggReader.Type.OPUS) {
                    return new OpusAudioStream(this.data);
                }
                if (type == OggReader.Type.VORBIS) {
                    // GPT-SoVITS 默认 media_type 就是 ogg（Vorbis），原版解码器直接能读
                    return new OggAudioStream(new ByteArrayInputStream(this.data));
                }
                TouhouLittleMaid.LOGGER.warn("[Futo] unknown TTS audio container, {} bytes", this.data.length);
            } catch (Exception e) {
                TouhouLittleMaid.LOGGER.error("[Futo] failed to decode TTS audio", e);
            }
            return null;
        }, Util.backgroundExecutor());
    }
}

