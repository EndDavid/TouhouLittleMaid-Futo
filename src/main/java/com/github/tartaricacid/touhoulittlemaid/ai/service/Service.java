package com.github.tartaricacid.touhoulittlemaid.ai.service;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.HistoryChat;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.Site;
import com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.ChatClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.request.ChatCompletion;
import com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.request.ResponseFormat;
import com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.request.Role;
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
 * {@code getTtsClient} / {@code getTtsRequest} / {@code getSttClient}。本轮只移植聊天链路，
 * 因此只保留 {@code CHAT_HTTP_CLIENT} 与 {@code getChatClient} / {@code getChatCompletion}。
 * TTS / STT 客户端属于独立的语音子系统（需要音频流解码与麦克风采集），另行一轮。</p>
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
            // 最后强调一下语言类型
            chatCompletion.userChat(String.format("请用%s语言回复 chat_text 部分！并用%s语言回复 tts_text 部分！", language, chatManager.getTtsLanguage()));
            return chatCompletion;
        }).orElse(null);
    }
    private Service() {
    }
}
