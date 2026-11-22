package com.github.tartaricacid.touhoulittlemaid.ai.service.tts;

import java.util.function.Consumer;

/**
 * TTS 客户端的通用契约，移植自上游 {@code ai/service/tts/TTSClient}。
 *
 * <p>与 {@code ChatClient} 的最大区别是<b>响应体是二进制</b>：
 * 成功时回调 {@code byte[]}（ogg/opus 音频流，原样发给客户端去解码），
 * 失败时回调 {@link Throwable}。音频解码必须放在客户端做，
 * 服务端只负责转发字节。</p>
 *
 * @param <T> 该客户端认识的请求体类型
 */
public interface TTSClient<T extends TTSRequest> {
    TTSClient<T> request(T request);

    void handle(Consumer<byte[]> consumer, Consumer<Throwable> failConsumer);
}

