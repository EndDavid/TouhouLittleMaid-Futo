package com.github.tartaricacid.touhoulittlemaid.ai.service;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Consumer;
/**
 * HTTP 回调的最小型接口，移植自上游 {@code ai/service/ResponseCallback}。
 *
 * <p>与平台无关，逐字沿用：{@code isSuccessful} 只认 2xx，剩下的状态码一律交给
 * {@code onResponse} 里的失败分支去解包响应体（上游会把整段响应体塞进异常信息，
 * 这样玩家看到的是服务端真正的报错，而不是干巴巴一个 400）。</p>
 */
public interface ResponseCallback<T> {
    void onFailure(HttpRequest request, Throwable e);
    void onResponse(HttpResponse<T> response, Consumer<Throwable> failConsumer);
    default boolean isSuccessful(HttpResponse<T> response) {
        int statusCode = response.statusCode();
        return 200 <= statusCode && statusCode < 300;
    }
}
