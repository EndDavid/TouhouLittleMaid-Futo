package com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.ai.service.Service;
import com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.request.ChatCompletion;
import com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.response.ChatCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.response.ChatCompletionResponse;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;
/**
 * OpenAI 兼容协议的聊天客户端，移植自上游 {@code ai/service/chat/openai/ChatClient}。
 *
 * <p>纯 {@code java.net.http}，与平台无关：地址是 {@code baseUrl + /chat/completions}，
 * 鉴权头是 {@code Authorization: Bearer <api_key>}，超时 20 秒。
 * 请求是<b>异步</b>的（{@code sendAsync}），回调里再切回服务端主线程 ——
 * 所以绝不能在这里碰世界状态。</p>
 *
 * <p>为什么用 {@link HttpHeaders} / {@link MediaType}（Guava）：上游就是这么写的，
 * Guava 随 Minecraft 一起提供，不必自己拼字符串常量。</p>
 */
public final class ChatClient {
    private final HttpClient httpClient;
    private String baseUrl = "";
    private String apiKey = "";
    private ChatCompletion chatCompletion;
    public static ChatClient create(final HttpClient httpClient) {
        return new ChatClient(httpClient);
    }
    private ChatClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }
    public ChatClient baseUrl(final String baseUrl) {
        if (baseUrl.endsWith("/")) {
            this.baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        } else {
            this.baseUrl = baseUrl;
        }
        return this;
    }
    public ChatClient apiKey(final String apiKey) {
        this.apiKey = apiKey;
        return this;
    }
    public ChatClient chat(final ChatCompletion chatCompletion) {
        this.chatCompletion = chatCompletion;
        return this;
    }
    /**
     * 拼出最终请求地址。
     *
     * <p>yml 里的 {@code url} 有两种写法，两种都得认：</p>
     * <ul>
     *     <li>1.18.2 风格：base 地址（{@code https://api.deepseek.com}、
     *         {@code https://api.openai.com/v1}），要补 {@code /chat/completions}；</li>
     *     <li>1.20.1 风格：完整端点（{@code https://api.deepseek.com/chat/completions}），
     *         再补就会变成 {@code …/chat/completions/chat/completions}。</li>
     * </ul>
     */
    private String resolveUrl() {
        String suffix = ChatCompletion.getUrl();
        if (this.baseUrl.endsWith(suffix)) {
            return this.baseUrl;
        }
        return this.baseUrl + suffix;
    }

    public void handle(Consumer<ChatCompletionResponse> consumer, Consumer<Throwable> failConsumer) {
        String url = resolveUrl();
        // 每次请求都留一行：曾经有过「明明填了 key，请求却在打 127.0.0.1」的困惑，
        // 而当时只有失败才打日志，看不出到底选了哪个站点。
        TouhouLittleMaid.LOGGER.info("[Futo] AI chat POST {}", url);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + this.apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(Service.GSON.toJson(chatCompletion)))
                .timeout(Duration.ofSeconds(20))
                .uri(URI.create(url))
                .build();
        httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, throwable) -> {
                    ChatCallback callback = new ChatCallback(consumer);
                    if (throwable != null) {
                        callback.onFailure(httpRequest, throwable);
                        failConsumer.accept(throwable);
                    } else {
                        callback.onResponse(response, failConsumer);
                    }
                });
    }
}