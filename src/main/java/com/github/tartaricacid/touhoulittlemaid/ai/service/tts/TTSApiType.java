package com.github.tartaricacid.touhoulittlemaid.ai.service.tts;
/**
 * TTS 站点类型，移植自上游 {@code ai/service/tts/TTSApiType}。
 *
 * <p>{@code api_type} 决定用哪个客户端：{@code fish-audio} / {@code gpt-sovits} 走 HTTP 合成，
 * {@code system} 走客户端的系统旁白（不经过网络）。见 {@code ai/service/tts/TTSFactory}。</p>
 *
 * <p>另外 <b>站点解析也必须认识它</b>：{@code available_sites.yml} 里仍有 {@code type: tts}
 * 的条目，旧版配置还缺 {@code api_type} 字段 —— {@code AvailableSites#fixOldVersionConfig}
 * 要按这里补默认值，否则下次写回文件时会丢掉这些站点。</p>
 */
public enum TTSApiType {
    FISH_AUDIO("fish-audio"),
    GPT_SOVITS("gpt-sovits"),
    SYSTEM("system");
    private final String name;
    TTSApiType(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }
}
