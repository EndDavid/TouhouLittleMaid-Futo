package com.github.tartaricacid.touhoulittlemaid.ai.service.tts.fishaudio.request;

/**
 * MP3 比特率，移植自上游同名枚举。
 */
public enum Mp3BitRate {
    LOW(64),
    MEDIUM(128),
    HIGH(192);

    private final int bitRate;

    Mp3BitRate(int bitRate) {
        this.bitRate = bitRate;
    }

    public int getBitRate() {
        return bitRate;
    }
}

