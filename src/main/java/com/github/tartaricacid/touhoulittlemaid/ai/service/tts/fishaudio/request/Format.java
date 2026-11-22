package com.github.tartaricacid.touhoulittlemaid.ai.service.tts.fishaudio.request;

/**
 * FishAudio 的返回音频格式，移植自上游同名枚举。
 */
public enum Format {
    WAV("wav"),
    PCM("pcm"),
    MP3("mp3"),
    OPUS("opus");

    private final String id;

    Format(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}

