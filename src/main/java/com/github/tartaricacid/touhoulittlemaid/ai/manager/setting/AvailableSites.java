package com.github.tartaricacid.touhoulittlemaid.ai.manager.setting;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.ai.service.chat.ChatApiType;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.TTSApiType;
import com.github.tartaricacid.touhoulittlemaid.util.GetJarResources;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
/**
 * AI 站点表，移植自上游 {@code ai/manager/setting/AvailableSites}。
 *
 * <p>数据来源两层，后读到的覆盖先读到的：</p>
 * <ol>
 *     <li>jar 内置的 {@code /assets/touhou_little_maid/config/available_sites.yml}；</li>
 *     <li>玩家配置 {@code config/touhou_little_maid/available_sites.yml}。</li>
 * </ol>
 * <p>读完后<b>立刻把合并结果写回玩家配置文件</b>（上游同样如此）：这样玩家第一次进游戏
 * 就能看到一份带注释字段的完整模板，照着填 {@code api_key} 即可。</p>
 *
 * <p><b>只有 {@code enabled: true} 的站点才会进 {@code CHAT_SITES} / {@code TTS_SITES}</b>。
 * 老配置没有 {@code enabled} 键时按「有没有真 {@code api_key}」推一个，并写回文件
 * （1.18.2 用 {@code empty} / {@code empty_key} 表示「没填」，这些占位串不算已配置 ——
 * 否则本地站点 {@code player2} 会顶替成默认站点，请求全打到 {@code 127.0.0.1}）。</p>
 *
 * <p>{@code CLIENT_*} 两张表是发给客户端的副本（只有 站点名 → 模型列表），
 * 不含密钥，见 {@code SyncAiSettingPacket}。</p>
 */
public final class AvailableSites {
    public static final String FILE_NAME = "available_sites.yml";
    // 服务端缓存的站点信息，包含秘钥等敏感信息
    private static final Map<String, Site> CHAT_SITES = Maps.newLinkedHashMap();
    private static final Map<String, Site> TTS_SITES = Maps.newLinkedHashMap();
    // 用于向客户端发送的站点信息，用来供玩家选择不同的站点，不包含敏感信息
    private static final Map<String, List<String>> CLIENT_CHAT_SITES = Maps.newLinkedHashMap();
    private static final Map<String, List<String>> CLIENT_TTS_SITES = Maps.newLinkedHashMap();
    private static final Path SITES_FILES = Paths.get("config", TouhouLittleMaid.MOD_ID, FILE_NAME);
    private static final String JAR_SITES_FILES = String.format("/assets/%s/config/%s", TouhouLittleMaid.MOD_ID, FILE_NAME);
    /**
     * 原始 yml 内容（站点名 → 原始键值），包含没填 api_key 的站点。
     * <p>GUI 编辑要用它：{@code CHAT_SITES} 只收「能用的」站点，
     * 而设置界面还要能看见并编辑那些没填 key 的。</p>
     */
    private static final Map<String, LinkedHashMap<String, Object>> RAW_SITES = Maps.newLinkedHashMap();

    public static Map<String, LinkedHashMap<String, Object>> getRawSites() {
        return RAW_SITES;
    }

    public static void readSites() {
        CHAT_SITES.clear();
        TTS_SITES.clear();
        CLIENT_CHAT_SITES.clear();
        CLIENT_TTS_SITES.clear();
        Yaml yaml = new Yaml();
        Map<String, LinkedHashMap<String, Object>> allSites = Maps.newLinkedHashMap();
        // 先尝试读取 jar 包内的文件
        try (InputStream stream = GetJarResources.readTouhouLittleMaidFile(JAR_SITES_FILES)) {
            if (stream != null) {
                allSites.putAll(yaml.load(stream));
            }
        } catch (Exception e) {
            TouhouLittleMaid.LOGGER.error("[Futo] failed to read built-in available sites", e);
        }
        // 再尝试读取玩家配置文件里的内容
        if (SITES_FILES.toFile().isFile()) {
            try (FileReader reader = new FileReader(SITES_FILES.toFile(), StandardCharsets.UTF_8)) {
                Map<String, LinkedHashMap<String, Object>> custom = yaml.load(reader);
                if (custom != null) {
                    allSites.putAll(custom);
                }
            } catch (Exception e) {
                TouhouLittleMaid.LOGGER.error("[Futo] failed to read available sites file", e);
            }
        }
        // 分类
        allSites.forEach((key, value) -> {
            fixOldVersionConfig(value);
            fillEnabled(value);
            try {
                Site site = new Site(value);
                // 只有 enabled 的站点才能用于聊天 / TTS。
                // 老配置没有 enabled 键时由 Site 推一个（占位密钥不算已配置），
                // 并在下面写回 yml，玩家就能在文件里直接看见并改动。
                if (site.isChat() && site.isEnabled()) {
                    CHAT_SITES.put(key, site);
                    CLIENT_CHAT_SITES.put(key, site.getModels());
                }
                if (site.isTts() && site.isEnabled()) {
                    TTS_SITES.put(key, site);
                    CLIENT_TTS_SITES.put(key, site.getModels());
                }
            } catch (Exception e) {
                TouhouLittleMaid.LOGGER.error("[Futo] failed to load site: {}", key, e);
            }
        });
        // 保存（同时留一份给 GUI 编辑用）
        RAW_SITES.clear();
        RAW_SITES.putAll(allSites);
        saveSites(allSites);
        TouhouLittleMaid.LOGGER.info("[Futo] AI sites in use: {} chat / {} tts (enabled ones only)",
                CHAT_SITES.size(), TTS_SITES.size());
    }
    public static void saveSites(Map<String, LinkedHashMap<String, Object>> allSites) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(options);
        // 保存
        try {
            Path parent = SITES_FILES.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (FileWriter writer = new FileWriter(SITES_FILES.toFile(), StandardCharsets.UTF_8)) {
                yaml.dump(allSites, writer);
            }
        } catch (Exception e) {
            TouhouLittleMaid.LOGGER.error("[Futo] failed to save available sites file", e);
        }
    }
    /**
     * 新增或覆盖一个站点，并落盘、重新加载。
     *
     * <p>字段名与 yml 一致（{@code type} / {@code api_type} / {@code url} / {@code api_key} /
     * {@code models}）。{@code apiType} 传空时按类型补默认值。</p>
     *
     * @return 是否成功写入磁盘
     */
    public static boolean saveSite(String name, String type, String apiType, String url,
                                   String apiKey, java.util.List<String> models) {
        // 未显式指定 enabled 时：填了真 key 就自动启用，否则沿用旧值 / 推导值
        LinkedHashMap<String, Object> old = RAW_SITES.get(name);
        boolean enabled = old != null && old.containsKey("enabled")
                ? Boolean.parseBoolean(Objects.toString(old.get("enabled"), "false"))
                : Site.defaultEnabled(apiType, apiKey);
        if (Site.isUsableApiKey(apiKey) || "system".equalsIgnoreCase(StringUtils.defaultString(apiType))) {
            enabled = true;
        }
        return saveSite(name, type, apiType, url, apiKey, models, enabled);
    }

    /**
     * 同上，但显式指定 {@code enabled}（界面里的开关走这条）。
     */
    public static boolean saveSite(String name, String type, String apiType, String url,
                                   String apiKey, java.util.List<String> models, boolean enabled) {
        if (StringUtils.isBlank(name)) {
            return false;
        }
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("type", type);
        map.put("api_type", StringUtils.isBlank(apiType)
                ? ("tts".equals(type) ? TTSApiType.FISH_AUDIO.getName() : ChatApiType.OPENAI.getName())
                : apiType);
        map.put("url", StringUtils.defaultString(url));
        map.put("api_key", StringUtils.defaultString(apiKey));
        map.put("enabled", enabled);
        map.put("models", models == null ? new java.util.ArrayList<String>() : models);
        RAW_SITES.put(name, map);
        saveSites(RAW_SITES);
        readSites();
        return true;
    }
    /**
     * 删除一个站点并落盘。
     */
    public static boolean removeSite(String name) {
        if (!RAW_SITES.containsKey(name)) {
            return false;
        }
        RAW_SITES.remove(name);
        saveSites(RAW_SITES);
        readSites();
        return true;
    }
    /**
     * 站点是否「已启用」（能被聊天 / TTS 用到）。
     *
     * <p>判定与 {@link #readSites()} 完全一致：看 yml 里的 {@code enabled}，
     * 老配置缺这个键时按是否填了真 api_key 推一个。</p>
     */
    public static boolean isEnabled(String name) {
        LinkedHashMap<String, Object> map = RAW_SITES.get(name);
        if (map == null) {
            return false;
        }
        Object raw = map.get("enabled");
        if (raw != null) {
            return Boolean.parseBoolean(raw.toString());
        }
        return Site.defaultEnabled(Objects.toString(map.get("api_type"), StringUtils.EMPTY),
                Objects.toString(map.get("api_key"), StringUtils.EMPTY));
    }
    public static Site getChatSite(String key) {
        return CHAT_SITES.get(key);
    }
    public static Site getTtsSite(String key) {
        return TTS_SITES.get(key);
    }
    @Nullable
    public static Site getFirstAvailableChatSite() {
        if (CHAT_SITES.isEmpty()) {
            return null;
        }
        return CHAT_SITES.values().stream().findFirst().orElse(null);
    }
    @Nullable
    public static Site getFirstAvailableTtsSite() {
        if (TTS_SITES.isEmpty()) {
            return null;
        }
        return TTS_SITES.values().stream().findFirst().orElse(null);
    }
    public static Map<String, List<String>> getClientChatSites() {
        return CLIENT_CHAT_SITES;
    }
    public static Map<String, List<String>> getClientTtsSites() {
        return CLIENT_TTS_SITES;
    }
    private static void fixOldVersionConfig(LinkedHashMap<String, Object> map) {
        String type = Objects.requireNonNullElse((String) map.get("type"), StringUtils.EMPTY);
        String apiType = Objects.requireNonNullElse((String) map.get("api_type"), StringUtils.EMPTY);
        // 如果缺失 api_type 字段，那么依据类型给其补上默认的
        if (apiType.isBlank()) {
            if ("chat".equals(type)) {
                map.put("api_type", ChatApiType.OPENAI.getName());
            }
            if ("tts".equals(type)) {
                map.put("api_type", TTSApiType.FISH_AUDIO.getName());
            }
        }
    }
    private static void fillEnabled(LinkedHashMap<String, Object> map) {
        if (map.containsKey("enabled")) {
            return;
        }
        // 老配置没有 enabled 键：按「有没有真 api_key」推一个，然后写回文件，
        // 这样玩家能在 yml 里直接看到、也能在界面里勾掉。
        String apiType = Objects.toString(map.get("api_type"), StringUtils.EMPTY);
        String apiKey = Objects.toString(map.get("api_key"), StringUtils.EMPTY);
        map.put("enabled", Site.defaultEnabled(apiType, apiKey));
    }
    private AvailableSites() {
    }
}