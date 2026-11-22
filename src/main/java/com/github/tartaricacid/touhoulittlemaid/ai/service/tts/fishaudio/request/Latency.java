package com.github.tartaricacid.touhoulittlemaid.ai.service.tts.fishaudio.request;

/**
 * FishAudio 的延迟档位，移植自上游同名枚举。
 */
public enum Latency {
    NORMAL("normal"),
    BALANCED("balanced");

    private final String id;

    Latency(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}

