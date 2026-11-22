package com.github.tartaricacid.touhoulittlemaid.ai.manager.response;
import com.google.gson.annotations.SerializedName;
/**
 * 模型必须遵守的输出契约，移植自上游 {@code ai/manager/response/ResponseChat}。
 *
 * <p>{@code chat_text} 是头顶气泡 / 聊天栏里显示的那句话，
 * {@code tts_text} 是交给语音合成的那份文本（允许与前者不同，比如去掉颜文字）。
 * 本轮不播放 TTS，但 <b>字段不能删</b>：系统设定里的
 * {@code ${output_json_format}} 就是用本类的空实例序列化出来的，
 * 少一个字段模型就不会返回它，将来的 TTS 子系统还得再改一次 prompt。</p>
 */
public class ResponseChat {
    @SerializedName("chat_text")
    public String chatText = "";
    @SerializedName("tts_text")
    public String ttsText = "";
    public String getChatText() {
        return chatText;
    }
    public String getTtsText() {
        return ttsText;
    }
}
