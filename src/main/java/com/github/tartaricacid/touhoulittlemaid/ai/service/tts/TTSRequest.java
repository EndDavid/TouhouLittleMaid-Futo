package com.github.tartaricacid.touhoulittlemaid.ai.service.tts;

/**
 * TTS 请求体的标记接口，移植自上游 {@code ai/service/tts/TTSRequest}。
 *
 * <p>各家的请求体字段完全不同（FishAudio 是 {@code format} / {@code reference_id}，
 * GPT-SoVITS 是 {@code ref_audio_path} / {@code text_lang}），但 {@link TTSClient}
 * 只关心「把它序列化成 JSON 发出去」，所以这里只需要一个空的标记接口。</p>
 */
public interface TTSRequest {
}

