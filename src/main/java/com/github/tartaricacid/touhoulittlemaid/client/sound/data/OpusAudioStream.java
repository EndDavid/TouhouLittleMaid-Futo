package com.github.tartaricacid.touhoulittlemaid.client.sound.data;

import com.github.tartaricacid.touhoulittlemaid.util.OpusDecoderUtil;
import io.github.jaredmdobson.concentus.OpusException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sounds.AudioStream;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 把 Opus 音频包装成原版声音引擎认识的 {@link AudioStream}，
 * 移植自上游 {@code client/sound/data/OpusAudioStream}。
 *
 * <p>解码在构造时一次性完成（见 {@link OpusDecoderUtil}），之后 {@link #read(int)}
 * 只是从内存里按调用方要求的长度搬数据。</p>
 *
 * <p><b>与上游的两处修正：</b></p>
 * <ol>
 *     <li>上游按「帧」读取，但无论实际读到多少字节都把整个 frame 数组塞进缓冲区，
 *         句子结尾会混进上一帧的残留字节，听起来是有一下杂音；</li>
 *     <li>上游在 {@code size < frameSize} 时会返回空缓冲区，引擎可能误判为播放结束。
 *         这里改用「整段 PCM + 读指针」，任意长度都能正确返回。</li>
 * </ol>
 */
@Environment(EnvType.CLIENT)
public class OpusAudioStream implements AudioStream {
    private final AudioFormat format;
    private final byte[] pcm;
    private int offset;

    public OpusAudioStream(byte[] data) throws IOException, OpusException {
        Pair<AudioFormat, byte[]> decoded = OpusDecoderUtil.decode(data);
        this.format = decoded.getLeft();
        this.pcm = decoded.getRight();
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    /**
     * 返回从当前读指针开始、最多 {@code size} 字节的 PCM 数据。
     * 返回空缓冲区表示音频已经播完。
     */
    @Override
    public ByteBuffer read(int size) {
        int remain = this.pcm.length - this.offset;
        int count = Math.min(Math.max(size, 0), Math.max(remain, 0));
        ByteBuffer byteBuffer = BufferUtils.createByteBuffer(Math.max(count, 0));
        if (count > 0) {
            byteBuffer.put(this.pcm, this.offset, count);
            this.offset += count;
        }
        byteBuffer.flip();
        return byteBuffer;
    }

    @Override
    public void close() {
        // 全部数据已在内存里，没有底层资源需要释放
        this.offset = this.pcm.length;
    }
}
