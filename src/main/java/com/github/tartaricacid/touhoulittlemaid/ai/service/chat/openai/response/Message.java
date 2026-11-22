package com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.response;
import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;
/**
 * 响应里的消息体，移植自上游 {@code ai/service/chat/openai/response/Message}。
 *
 * <p>{@code content} 允许为 null：模型被内容过滤拦下时返回的就是 null。</p>
 */
public class Message {
    @SerializedName("role")
    private String role;
    @SerializedName("content")
    private String content;
    public String getRole() {
        return role;
    }
    @Nullable
    public String getContent() {
        return content;
    }
}
