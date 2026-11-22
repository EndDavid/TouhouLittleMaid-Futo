package com.github.tartaricacid.touhoulittlemaid.util;

import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.apache.commons.lang3.tuple.Pair;
import org.gagravarr.ogg.OggFile;
import org.gagravarr.opus.OpusAudioData;
import org.gagravarr.opus.OpusFile;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 把 ogg/opus 音频整段解成 PCM，移植自上游 {@code util/OpusDecoderUtil}。
 *
 * <p>原版的声音引擎只认 Vorbis（{@code OggAudioStream}），而 FishAudio 在
 * {@code format: opus} 下返回的是 Opus 编码的 ogg，所以必须自己解：
 * <b>vorbis-java</b>（{@code org.gagravarr}）负责拆 ogg 容器，
 * <b>concentus</b>（纯 Java 的 Opus 解码器）负责把每个音频包解成 PCM。</p>
 *
 * <p>为什么整段解码而不是流式解：女仆的一句话通常只有几秒，
 * 一次性解完可以避免「边播边解」时线程与通道生命周期的纠缠，代价只是几十毫秒的启动延迟。</p>
 *
 * <p><b>只应在客户端调用</b>（服务端没有 {@code javax.sound} 的音频格式需求，
 * 也不需要为每个玩家解码）。</p>
 */
@Environment(EnvType.CLIENT)
public final class OpusDecoderUtil {
    /**
     * Opus 支持的帧长有：2.5ms、5ms、10ms、20ms、40ms、60ms。
     * 因为对延迟不敏感，所以选 60ms —— 帧越大，解码调用次数越少。
     */
    private static final int MAX_FRAME_SIZE = 60;

    /**
     * 返回 (PCM 格式, PCM 字节)。
     */
    public static Pair<AudioFormat, byte[]> decode(byte[] data) throws IOException, OpusException {
        try (OpusFile opusFile = new OpusFile(new OggFile(new ByteArrayInputStream(data)));
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            int sampleRate = opusFile.getInfo().getSampleRate();
            int channels = opusFile.getInfo().getNumChannels();
            int frameSize = sampleRate * MAX_FRAME_SIZE / 1000;

            OpusDecoder decoder = new OpusDecoder(sampleRate, channels);
            byte[] pcmBytes = new byte[sampleRate * channels * 2];
            OpusAudioData packet;
            while ((packet = opusFile.getNextAudioPacket()) != null) {
                byte[] packetBytes = packet.getData();
                int samplesDecoded = decoder.decode(packetBytes, 0, packetBytes.length, pcmBytes, 0, frameSize, false);
                outputStream.write(pcmBytes, 0, samplesDecoded * channels * 2);
            }
            byte[] outputData = outputStream.toByteArray();
            AudioFormat audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16,
                    channels, channels * 2, sampleRate, false);
            return Pair.of(audioFormat, outputData);
        }
    }

    private OpusDecoderUtil() {
    }
}

