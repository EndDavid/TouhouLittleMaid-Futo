package com.github.tartaricacid.touhoulittlemaid.ai.service.tts.gptsovits;

import com.github.tartaricacid.touhoulittlemaid.ai.service.Service;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.TTSClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.gptsovits.request.TTSGptSovitsRequest;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.gptsovits.response.TTSGptSovitsCallback;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * GPT-SoVITS 的 TTS 客户端，移植自上游同名类。
 *
 * <p>注意它<b>不拼 {@code /tts} 后缀</b>：GPT-SoVITS 的地址本身就带路径
 * （例如 {@code http://127.0.0.1:9880/tts}），站点里填什么就打什么。</p>
 */
public class TTSGptSovitsClient implements TTSClient<TTSGptSovitsRequest> {
    private final HttpClient httpClient;
    private String baseUrl = "";
    private String apiKey = "";
    private TTSGptSovitsRequest request;

    public static TTSGptSovitsClient create(final HttpClient httpClient) {
        return new TTSGptSovitsClient(httpClient);
    }

    private TTSGptSovitsClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public TTSGptSovitsClient baseUrl(final String baseUrl) {
        if (baseUrl.endsWith("/")) {
            this.baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        } else {
            this.baseUrl = baseUrl;
        }
        return this;
    }

    public TTSGptSovitsClient apiKey(final String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    @Override
    public TTSGptSovitsClient request(TTSGptSovitsRequest request) {
        this.request = request;
        return this;
    }

    @Override
    public void handle(Consumer<byte[]> consumer, Consumer<Throwable> failConsumer) {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + this.apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(Service.GSON.toJson(request)))
                .timeout(Duration.ofSeconds(20))
                .uri(URI.create(baseUrl))
                .build();
        httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray())
                .whenComplete((response, throwable) -> {
                    TTSGptSovitsCallback callback = new TTSGptSovitsCallback(consumer);
                    if (throwable != null) {
                        callback.onFailure(httpRequest, throwable);
                        failConsumer.accept(throwable);
                    } else {
                        callback.onResponse(response, failConsumer);
                    }
                });
    }
}

