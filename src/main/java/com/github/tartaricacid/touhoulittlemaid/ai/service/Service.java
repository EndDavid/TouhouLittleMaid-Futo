package com.github.tartaricacid.touhoulittlemaid.ai.service;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.HistoryChat;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.Site;
import com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.ChatClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.request.ChatCompletion;
import com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.request.ResponseFormat;
import com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.request.Role;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.TTSClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.TTSFactory;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.TTSRequest;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.AIConfig;
import com.github.tartaricacid.touhoulittlemaid.util.CappedQueue;
import com.google.gson.Gson;
import org.jetbrains.annotations.Nullable;
import java.net.http.HttpClient;
import java.time.Duration;
/**
 * HTTP 客户端与请求组装的集中入口，移植自上游 {@code ai/service/Service}。
 *
 * <p><b>与上游的差异：</b>上游这里有三个 {@link HttpClient}（聊天 / TTS / STT）和
 * {@code getTtsClient} / {@code getTtsRequest} / {@code getSttClient}。
 * 本移植版已经接上「聊天」与「TTS」两条链路，因此保留 {@code CHAT_HTTP_CLIENT} /
 * {@code TTS_HTTP_CLIENT} 与对应的工厂方法；STT（麦克风采集 + 语音转文字）
 * 属于独立子系统，仍是后续工作。</p>
 *
 * <p><b>为什么 {@code GSON} 放在这里：</b>聊天、角色设定、语音三条链路都要序列化，
 * 上游用一个共享实例；不同实例对同一份 DTO 的输出可能因字段策略差异而不一致，
 * 没必要冒这个险。</p>
 */
public final class Service {
    public static final Gson GSON = new Gson();
    private static final HttpClient CHAT_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .proxy(new ConfigProxySelector(AIConfig.CHAT_PROXY_ADDRESS))
            .build();
    private static final HttpClient TTS_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .proxy(new ConfigProxySelector(AIConfig.TTS_PROXY_ADDRESS))
            .build();
    public static ChatClient getChatClient(Site site) {
        String chatApiKey = site.getApiKey();
        String chatBaseUrl = site.getUrl();
        return ChatClient.create(CHAT_HTTP_CLIENT)
                .apiKey(chatApiKey)
                .baseUrl(chatBaseUrl);
    }
    /**
     * 组装一次对话请求：系统设定 + 历史对话 + 末尾的语言强调。
     *
     * <p>返回 {@code null} 表示这名女仆的模型没有对应的角色设定文件
     * （{@code config/touhou_little_maid/settings} 或模型包里的 {@code settings/*.yml}），
     * 调用方据此提示 {@code ai.touhou_little_maid.chat.no_setting}。</p>
     */
    @Nullable
    public static ChatCompletion getChatCompletion(MaidAIChatManager chatManager, String language) {
        return chatManager.getSetting().map(s -> {
            String setting = s.getSetting(chatManager.getMaid(), language);
            String model = chatManager.getChatModel();
            double chatTemperature = chatManager.getChatTemperature();
            CappedQueue<HistoryChat> history = chatManager.getHistory();
            // 构建对话
            ChatCompletion chatCompletion = ChatCompletion.create()
                    .model(model)
                    .temperature(chatTemperature)
                    .setResponseFormat(ResponseFormat.json())
                    .systemChat(setting)
                    // 塞入一个参考回应，能让 AI 尽可能遵循参考格式进行回复
                    .assistantChat("{\"chat_text\":\"看到你真开心！要不要一起去挖矿？\",\"tts_text\":\"看到你真开心！要不要一起去挖矿？\"}");
            // 倒序遍历，将历史对话加载进去
            history.getDeque().descendingIterator().forEachRemaining(historyChat -> {
                Role role = historyChat.role();
                String message = historyChat.message();
                if (role.equals(Role.USER)) {
                    chatCompletion.userChat(message);
                } else if (role.equals(Role.ASSISTANT)) {
                    chatCompletion.assistantChat(message);
                }
            });
            // 最后强调一下语言类型与输出格式。
            //
            // 「JSON」这个词是硬性要求：请求里一旦带 response_format: json_object，
            // DeepSeek 之类的服务会检查 prompt 里有没有 json 字样，没有就直接 400
            // （Prompt must contain the word 'json' in some form ...）。
            // 上游靠角色设定模板里的「输出格式为 JSON 格式：${output_json_format}」满足它，
            // 但玩家自己写的人设、以及 auto-gen 生成的人设不一定带那句话，所以这里兜一句。
            chatCompletion.userChat(String.format(
                    "请用%s语言回复 chat_text 部分！并用%s语言回复 tts_text 部分！"
                            + "只返回一个 json 对象（json_object），"
                            + "形如 {\"chat_text\":\"…\",\"tts_text\":\"…\"}，不要输出其它内容。",
                    language, chatManager.getTtsLanguage()));
            return chatCompletion;
        }).orElse(null);
    }
    /**
     * 造出对应站点的 TTS 客户端；站点类型不认识时返回 {@code null}。
     */
    @Nullable
    public static TTSClient<?> getTtsClient(Site site) {
        return TTSFactory.getTtsClient(TTS_HTTP_CLIENT, site);
    }
    /**
     * 造出对应站点的 TTS 请求体；站点类型不认识时返回 {@code null}。
     */
    @Nullable
    public static TTSRequest getTtsRequest(Site site, String ttsText, String ttsLang, String model) {
        return TTSFactory.getTtsRequest(site, ttsText, ttsLang, model);
    }
    private Service() {
    }
}
