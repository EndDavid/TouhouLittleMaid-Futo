package com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.response;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.StringUtils;
/**
 * 聊天响应体，移植自上游 {@code ai/service/chat/openai/response/ChatCompletionResponse}。
 *
 * <p>只用到 {@code choices[0].message.content}（那里面才是
 * {@code {"chat_text":…,"tts_text":…}} 字符串），其余字段保留是为了与上游 DTO 一致，
 * 也便于将来做 token 统计。</p>
 *
 * <p><b>防御：</b>部分自建网关在出错时返回 {@code {"choices":null}} 或空数组，
 * 所以两处都判空，不在 {@code getFirstChoiceMessage()} 里抛 NPE。</p>
 */
public class ChatCompletionResponse {
    @SerializedName("id")
    private String id;
    @SerializedName("object")
    private String object;
    @SerializedName("created")
    private long created;
    @SerializedName("model")
    private String model;
    @SerializedName("system_fingerprint")
    private String systemFingerprint;
    @SerializedName("choices")
    private Choice[] choices;
    @SerializedName("service_tier")
    private String serviceTier;
    @SerializedName("usage")
    private Usage usage;
    public String getFirstChoiceMessage() {
        if (this.choices != null && this.choices.length > 0 && this.choices[0] != null) {
            Choice choice = this.choices[0];
            Message message = choice.getMessage();
            String content = message == null ? null : message.getContent();
            return content == null ? StringUtils.EMPTY : content;
        }
        return StringUtils.EMPTY;
    }
    public String getId() {
        return id;
    }
    public String getObject() {
        return object;
    }
    public long getCreated() {
        return created;
    }
    public String getModel() {
        return model;
    }
    public String getSystemFingerprint() {
        return systemFingerprint;
    }
    public Choice[] getChoices() {
        return choices;
    }
    public String getServiceTier() {
        return serviceTier;
    }
    public Usage getUsage() {
        return usage;
    }
}
