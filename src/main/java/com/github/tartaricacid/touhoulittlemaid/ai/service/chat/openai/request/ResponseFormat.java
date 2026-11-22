package com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.request;
import com.google.gson.annotations.SerializedName;
/**
 * {@code response_format} 字段，移植自上游 {@code ai/service/chat/openai/request/ResponseFormat}。
 */
public class ResponseFormat {
    @SerializedName("type")
    private String type;
    public static ResponseFormat text() {
        return new ResponseFormat("text");
    }
    public static ResponseFormat json() {
        return new ResponseFormat("json_object");
    }
    private ResponseFormat(String type) {
        this.type = type;
    }
    public String getType() {
        return type;
    }
}
