package com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.request;
import com.google.common.collect.Lists;
import com.google.gson.annotations.SerializedName;
import java.util.List;
/**
 * 聊天请求体，移植自上游 {@code ai/service/chat/openai/request/ChatCompletion}。
 *
 * <p>字段名就是 OpenAI 的 JSON 键名（{@code response_format}、{@code max_tokens} …），
 * 靠 {@link SerializedName} 而不是改字段名，这样 Java 侧仍是驼峰。
 * {@code response_format: json_object} 是让模型只吐 {@code {"chat_text":…,"tts_text":…}}
 * 的前提 —— 少了它，模型经常在 JSON 外面套一段寒暄，解析就会失败。</p>
 */
public class ChatCompletion {
    @SerializedName("model")
    private String model = "";
    @SerializedName("messages")
    private List<ChatMessage> messages = Lists.newArrayList();
    @SerializedName("response_format")
    private ResponseFormat responseFormat = ResponseFormat.text();
    @SerializedName("max_tokens")
    private int maxTokens = 256;
    @SerializedName("temperature")
    private double temperature = 0.5;
    public static ChatCompletion create() {
        return new ChatCompletion();
    }
    public static String getUrl() {
        return "/chat/completions";
    }
    public ChatCompletion model(String model) {
        this.model = model;
        return this;
    }
    public ChatCompletion systemChat(String message) {
        this.messages.add(ChatMessage.systemChat(message));
        return this;
    }
    public ChatCompletion userChat(String message) {
        this.messages.add(ChatMessage.userChat(message));
        return this;
    }
    public ChatCompletion assistantChat(String message) {
        this.messages.add(ChatMessage.assistantChat(message));
        return this;
    }
    public ChatCompletion maxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
        return this;
    }
    public ChatCompletion temperature(double temperature) {
        // 温度的范围是 [0,2)
        this.temperature = Math.min(temperature, 1.99);
        return this;
    }
    public ChatCompletion setResponseFormat(ResponseFormat responseFormat) {
        this.responseFormat = responseFormat;
        return this;
    }
}
