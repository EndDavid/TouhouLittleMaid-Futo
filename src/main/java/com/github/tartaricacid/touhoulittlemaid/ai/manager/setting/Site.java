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
    private static final String ENABLED = "enabled";
    private static final String URL = "url";
    private static final String API_KEY = "api_key";
    private static final String MODELS = "models";
    private static final String EXTRA_ARGS = "extra_args";
    /**
     * 1.18.2 的站点表用这些字符串表示「没有密钥」。
     * 它们非空，但显然不是真密钥 —— 若按 {@code isNotBlank} 判定，本地站点
     * （{@code player2} 的 {@code empty}、{@code system} 的 {@code empty_key}）
     * 都会被当成「已配置」，于是没填 key 的玩家会莫名其妙连到 127.0.0.1。
     */
    private static final java.util.Set<String> PLACEHOLDER_API_KEYS = java.util.Set.of(
            "empty", "empty_key", "no_key", "none", "null", "no_url");
    private String type;
    private String apiType;
    private String url;
    private String apiKey;
    private boolean enabled;
    private List<String> models;
    private Map<String, Object> extraArgs;

    public Site(String type, String apiType, String url, String apiKey, boolean enabled,
                List<String> models, Map<String, Object> extraArgs) {
        this.type = type;
        this.apiType = apiType;
        this.url = url;
        this.apiKey = apiKey;
        this.enabled = enabled;
        this.models = models;
        this.extraArgs = extraArgs;
    }

    @SuppressWarnings("unchecked")
    public Site(LinkedHashMap<String, Object> map) {
        this.type = Objects.requireNonNullElse((String) map.get(TYPE), StringUtils.EMPTY);
        this.apiType = Objects.requireNonNullElse((String) map.get(API_TYPE), StringUtils.EMPTY);
        this.url = Objects.requireNonNullElse((String) map.get(URL), StringUtils.EMPTY);
        this.apiKey = Objects.requireNonNullElse((String) map.get(API_KEY), StringUtils.EMPTY);
        this.enabled = readEnabled(map, this.apiType, this.apiKey);
        this.models = Objects.requireNonNullElse((List<String>) map.get(MODELS), Lists.newArrayList());
        this.extraArgs = Objects.requireNonNullElse((Map<String, Object>) map.get(EXTRA_ARGS), Maps.newHashMap());
    }

    /**
     * 读 {@code enabled}：写了就用写的，没写就按上游的既有语义推一个。
     */
    private static boolean readEnabled(LinkedHashMap<String, Object> map, String apiType, String apiKey) {
        Object raw = map.get(ENABLED);
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw != null) {
            return Boolean.parseBoolean(raw.toString());
        }
        return defaultEnabled(apiType, apiKey);
    }

    /**
     * 旧配置（没有 {@code enabled} 键）里一个站点是否默认可用。
     *
     * <p>与被删掉的「{@code isNotBlank(api_key)}」的差别只有两处：
     * 占位密钥（{@code empty} / {@code empty_key} …）不算已配置；
     * 系统 TTS（{@code api_type: system}）本来就不需要密钥，默认开着。</p>
     */
    public static boolean defaultEnabled(String apiType, String apiKey) {
        return isUsableApiKey(apiKey) || "system".equalsIgnoreCase(StringUtils.defaultString(apiType).trim());
    }

    /**
     * 这个 {@code api_key} 是否像一把真密钥（非空、且不是 1.18.2 的占位串）。
     */
    public static boolean isUsableApiKey(String apiKey) {
        if (StringUtils.isBlank(apiKey)) {
            return false;
        }
        return !PLACEHOLDER_API_KEYS.contains(apiKey.trim().toLowerCase(java.util.Locale.ROOT));
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
    public boolean isEnabled() {
        return enabled;
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
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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