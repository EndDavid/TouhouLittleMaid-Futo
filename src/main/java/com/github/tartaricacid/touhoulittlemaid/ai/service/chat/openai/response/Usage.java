package com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.response;
import com.google.gson.annotations.SerializedName;
/**
 * token 用量，移植自上游 {@code ai/service/chat/openai/response/Usage}。
 */
public class Usage {
    @SerializedName("prompt_tokens")
    private int promptTokens;
    @SerializedName("completion_tokens")
    private int completionTokens;
    @SerializedName("total_tokens")
    private int totalTokens;
    @SerializedName("completion_tokens_details")
    private CompletionTokensDetails completionTokensDetails;
    public int getPromptTokens() {
        return promptTokens;
    }
    public int getCompletionTokens() {
        return completionTokens;
    }
    public int getTotalTokens() {
        return totalTokens;
    }
    public CompletionTokensDetails getCompletionTokensDetails() {
        return completionTokensDetails;
    }
}
