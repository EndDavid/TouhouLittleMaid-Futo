package com.github.tartaricacid.touhoulittlemaid.ai.service.tts.fishaudio.request;

import com.google.gson.annotations.SerializedName;

/**
 * 参考音色（音频 + 文本），移植自上游同名类。
 *
 * <p>本移植版没有用到它（站点只填 {@code reference_id}），
 * 但请求体字段要完整，才能与上游的 JSON 契约一致。</p>
 */
public class References {
    @SerializedName("audio")
    private byte[] audio;

    @SerializedName("text")
    private String text;

    public void setAudio(byte[] audio) {
        this.audio = audio;
    }

    public void setText(String text) {
        this.text = text;
    }
}

