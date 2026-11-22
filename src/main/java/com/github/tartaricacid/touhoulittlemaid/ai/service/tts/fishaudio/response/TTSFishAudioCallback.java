package com.github.tartaricacid.touhoulittlemaid.ai.service.tts.fishaudio.response;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.ai.service.ResponseCallback;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * FishAudio 的响应处理，移植自上游同名类。
 *
 * <p>成功时把响应体（ogg 字节）原样交给上层；失败时把服务端返回的正文塞进异常信息，
 * 这样玩家在聊天栏看到的是真正的报错，而不是一个孤零零的状态码。</p>
 */
public class TTSFishAudioCallback implements ResponseCallback<byte[]> {
    private final Consumer<byte[]> consumer;

    public TTSFishAudioCallback(Consumer<byte[]> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void onFailure(HttpRequest request, Throwable e) {
        TouhouLittleMaid.LOGGER.error("[Futo] TTS request failed: {}", request, e);
    }

    @Override
    public void onResponse(HttpResponse<byte[]> response, Consumer<Throwable> failConsumer) {
        if (isSuccessful(response)) {
            consumer.accept(response.body());
        } else {
            TouhouLittleMaid.LOGGER.error("[Futo] TTS request failed: {}", response.statusCode());
            String message = String.format("HTTP Error Code: %d, Response %s", response.statusCode(),
                    new String(response.body(), StandardCharsets.UTF_8));
            failConsumer.accept(new Throwable(message));
        }
    }
}

