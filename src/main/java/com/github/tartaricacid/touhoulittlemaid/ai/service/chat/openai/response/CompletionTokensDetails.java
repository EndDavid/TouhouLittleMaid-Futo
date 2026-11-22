package com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.response;
import com.google.gson.annotations.SerializedName;
/**
 * 推理模型的 token 明细，移植自上游 {@code ai/service/chat/openai/response/CompletionTokensDetails}。
 */
public class CompletionTokensDetails {
    @SerializedName("reasoning_tokens")
    private int reasoningTokens;
    @SerializedName("accepted_prediction_tokens")
    private int acceptedPredictionTokens;
    @SerializedName("rejected_prediction_tokens")
    private int rejectedPredictionTokens;
    public int getReasoningTokens() {
        return reasoningTokens;
    }
    public int getAcceptedPredictionTokens() {
        return acceptedPredictionTokens;
    }
    public int getRejectedPredictionTokens() {
        return rejectedPredictionTokens;
    }
}
