package com.github.tartaricacid.touhoulittlemaid.ai.manager.setting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
/**
 * {@code available_sites.yml} 里的一个站点，移植自上游 {@code ai/manager/setting/Site}。
 *
 * <p>字段名就是 yml 的键名（{@code type} / {@code api_type} / {@code url} /
 * {@code api_key} / {@code models} / {@code extra_args}）。构造器接收 snakeyaml
 * 解出来的 {@code LinkedHashMap}，所有缺失字段都退化为空值而不是抛异常 ——
 * 玩家手改 yml 时少写一个键不该让整个站点表加载失败。</p>
 */
public class Site {
    private static final String CHAT = "chat";
    private static final String TTS = "tts";
    private static final String TYPE = "type";
    private static final String API_TYPE = "api_type";
    private static final String URL = "url";
    private static final String API_KEY = "api_key";
    private static final String MODELS = "models";
    private static final String EXTRA_ARGS = "extra_args";
    private String type;
    private String apiType;
    private String url;
    private String apiKey;
    private List<String> models;
    private Map<String, Object> extraArgs;
    public Site(String type, String apiType, String url, String apiKey, List<String> models, Map<String, Object> extraArgs) {
        this.type = type;
        this.apiType = apiType;
        this.url = url;
        this.apiKey = apiKey;
        this.models = models;
        this.extraArgs = extraArgs;
    }
    @SuppressWarnings("unchecked")
    public Site(LinkedHashMap<String, Object> map) {
        this.type = Objects.requireNonNullElse((String) map.get(TYPE), StringUtils.EMPTY);
        this.apiType = Objects.requireNonNullElse((String) map.get(API_TYPE), StringUtils.EMPTY);
        this.url = Objects.requireNonNullElse((String) map.get(URL), StringUtils.EMPTY);
        this.apiKey = Objects.requireNonNullElse((String) map.get(API_KEY), StringUtils.EMPTY);
        this.models = Objects.requireNonNullElse((List<String>) map.get(MODELS), Lists.newArrayList());
        this.extraArgs = Objects.requireNonNullElse((Map<String, Object>) map.get(EXTRA_ARGS), Maps.newHashMap());
    }
    public String getType() {
        return type;
    }
    public String getApiType() {
        return apiType;
    }
    public String getUrl() {
        return url;
    }
    public String getApiKey() {
        return apiKey;
    }
    public List<String> getModels() {
        return models;
    }
    public void setType(String type) {
        this.type = type;
    }
    public void setUrl(String url) {
        this.url = url;
    }
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    public void setModels(List<String> models) {
        this.models = models;
    }
    public boolean isChat() {
        return CHAT.equals(type);
    }
    public boolean isTts() {
        return TTS.equals(type);
    }
    public Map<String, Object> getExtraArgs() {
        return extraArgs;
    }
}
