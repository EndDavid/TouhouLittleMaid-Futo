package com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.response;
import com.google.gson.annotations.SerializedName;
/**
 * 响应里的一条候选回复，移植自上游 {@code ai/service/chat/openai/response/Choice}。
 */
public class Choice {
    @SerializedName("index")
    private int index;
    @SerializedName("message")
    private Message message;
    @SerializedName("finish_reason")
    private String finishReason;
    public int getIndex() {
        return index;
    }
    public Message getMessage() {
        return message;
    }
    public String getFinishReason() {
        return finishReason;
    }
}
