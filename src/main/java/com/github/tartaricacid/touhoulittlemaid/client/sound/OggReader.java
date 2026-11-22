package com.github.tartaricacid.touhoulittlemaid.client.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.gagravarr.ogg.OggFile;
import org.gagravarr.ogg.OggStreamIdentifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 判断一段 ogg 数据里装的是 Vorbis 还是 Opus，移植自上游 {@code client/sound/OggReader}。
 *
 * <p><b>与上游的差异：</b>上游这个类还带「从文件 / zip 里读整个音色包」的方法
 * （{@code readSoundDataFromFile} 等，那是女仆自定义音色包用的）。本轮只做 TTS，
 * 那些方法依赖 {@code SoundData} / {@code CustomSoundLoader} 一整套音色包子系统，
 * 属于「女仆音色包」那一轮的范围，所以这里只保留 TTS 真正需要的「识别容器类型」。</p>
 */
@Environment(EnvType.CLIENT)
public final class OggReader {
    /**
     * 读第一个 ogg packet 的魔数，判断编码格式。
     *
     * <p>识别不出（例如服务端返回了 mp3，或干脆是 JSON 报错正文）时返回
     * {@link Type#UNKNOWN}，调用方应当直接放弃播放，而不是硬塞给解码器。</p>
     */
    public static Type getOggType(byte[] data) throws IOException {
        return getOggType(new ByteArrayInputStream(data));
    }

    private static Type getOggType(InputStream stream) throws IOException {
        try (OggFile oggFile = new OggFile(stream)) {
            OggStreamIdentifier.OggStreamType streamType =
                    OggStreamIdentifier.identifyType(oggFile.getPacketReader().getNextPacket());
            if (streamType == OggStreamIdentifier.OGG_VORBIS) {
                return Type.VORBIS;
            }
            if (streamType == OggStreamIdentifier.OPUS_AUDIO || streamType == OggStreamIdentifier.OPUS_AUDIO_ALT) {
                return Type.OPUS;
            }
            return Type.UNKNOWN;
        } catch (Exception e) {
            // 不是合法的 ogg（服务端报错正文等）：当作未知处理
            return Type.UNKNOWN;
        }
    }

    public enum Type {
        VORBIS,
        OPUS,
        UNKNOWN
    }

    private OggReader() {
    }
}

