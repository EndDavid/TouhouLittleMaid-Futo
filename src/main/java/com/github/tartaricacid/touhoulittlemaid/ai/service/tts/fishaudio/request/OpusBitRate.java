package com.github.tartaricacid.touhoulittlemaid.ai.service.tts.fishaudio.request;

/**
 * Opus 比特率，移植自上游同名枚举。
 *
 * <p>{@code AUTO} 用 -1000 这个魔法值表示「交给服务端决定」，
 * 这是 FishAudio 接口自身的约定，不要当成笔误改掉。</p>
 */
public enum OpusBitRate {
    AUTO(-1000),
    LOWEST(24),
    LOW(32),
    MEDIUM(48),
    HIGH(64);

    private final int bitRate;

    OpusBitRate(int bitRate) {
        this.bitRate = bitRate;
    }

    public int getBitRate() {
        return bitRate;
    }
}

