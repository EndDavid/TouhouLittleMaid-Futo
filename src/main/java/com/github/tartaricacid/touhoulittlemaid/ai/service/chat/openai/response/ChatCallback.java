package com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.response;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.ai.service.ResponseCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.Service;
import com.google.gson.JsonSyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Consumer;
/**
 * 聊天响应的解包器，移植自上游 {@code ai/service/chat/openai/response/ChatCallback}。
 *
 * <p>成功的 2xx 直接把响应体丢给 Gson；非 2xx 时把<b>整个响应体</b>塞进异常信息
 * （玩家最终在聊天栏看到的就是它），这样「key 错了 / 模型名不对 / 额度用尽」
 * 都能看懂，而不是只有一个 401。</p>
 *
 * <p>注意这里运行在 HTTP 客户端线程上：{@code consumer} 最终会通过
 * {@code MinecraftServer#submit} 切回主线程，本类自身不碰世界状态。</p>
 */
public class ChatCallback implements ResponseCallback<String> {
    private final Consumer<ChatCompletionResponse> consumer;
    public ChatCallback(Consumer<ChatCompletionResponse> consumer) {
        this.consumer = consumer;
    }
    @Override
    public void onFailure(HttpRequest request, Throwable e) {
        TouhouLittleMaid.LOGGER.error("[Futo] AI chat request failed: {}", request, e);
    }
    @Override
    public void onResponse(HttpResponse<String> response, Consumer<Throwable> failConsumer) {
        try {
            String string = response.body();
            if (isSuccessful(response)) {
                ChatCompletionResponse chatCompletionResponse = Service.GSON.fromJson(string, ChatCompletionResponse.class);
                consumer.accept(chatCompletionResponse);
            } else {
                TouhouLittleMaid.LOGGER.error("[Futo] AI chat request failed: {}", string);
                String message = String.format("HTTP Error Code: %d, Response %s", response.statusCode(), string);
                failConsumer.accept(new Throwable(message));
            }
        } catch (JsonSyntaxException e) {
            TouhouLittleMaid.LOGGER.error("[Futo] AI chat JSON syntax error: ", e);
            failConsumer.accept(e);
        }
    }
}
