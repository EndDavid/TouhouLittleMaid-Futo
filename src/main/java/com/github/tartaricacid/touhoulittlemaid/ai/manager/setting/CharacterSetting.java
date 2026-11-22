package com.github.tartaricacid.touhoulittlemaid.ai.manager.setting;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.Yaml;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
/**
 * 一份角色设定（system prompt），移植自上游 {@code ai/manager/setting/CharacterSetting}。
 *
 * <p>文件结构固定为：</p>
 * <pre>
 * meta:
 *   author: 作者
 *   model_id: [touhou_little_maid:hakurei_reimu]
 * setting: |
 *   你是博丽灵梦……
 *   ${game_time} ${weather} …
 * </pre>
 * <p>{@code setting} 里的 {@code #} 开头行会被丢掉、空行也丢掉，其余按行拼接 ——
 * 这样作者可以在 yml 里写注释而不影响发送给模型的内容。</p>
 */
public class CharacterSetting {
    private static final String COMMENTS = "#";
    private static final String META = "meta";
    private static final String SETTING = "setting";
    private static final String AUTHOR = "author";
    private static final String MODEL_ID = "model_id";
    private static final Yaml YAML = new Yaml();
    private final String author;
    private final List<String> modelId;
    private final String rawSetting;
    @SuppressWarnings("unchecked")
    public CharacterSetting(File settingFile) throws IOException {
        Parsed parsed;
        try (FileReader reader = new FileReader(settingFile, StandardCharsets.UTF_8)) {
            parsed = parse(YAML.load(reader));
        }
        this.author = parsed.author;
        this.modelId = parsed.modelId;
        this.rawSetting = parsed.rawSetting;
    }
    @SuppressWarnings("unchecked")
    public CharacterSetting(InputStream stream) throws IOException {
        Parsed parsed;
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            parsed = parse(YAML.load(reader));
        }
        this.author = parsed.author;
        this.modelId = parsed.modelId;
        this.rawSetting = parsed.rawSetting;
    }
    /**
     * 解析 yml 内容。
     *
     * <p><b>为什么返回值而不是直接写字段：</b>三个字段是 final，
     * 只能在构造器里赋值，所以解析逻辑必须把结果交回去。
     * 上游是两个构造器各写一遍同样的 30 行（因为字段 final 且没法抽方法），
     * 这里用一个内部记录类就能共用一份。</p>
     */
    @SuppressWarnings("unchecked")
    private static Parsed parse(Object loaded) throws IOException {
        Map<String, Object> result = (Map<String, Object>) loaded;
        if (result == null) {
            throw new IOException("Failed to load setting file");
        }
        if (!result.containsKey(META) || !result.containsKey(SETTING)) {
            throw new IOException("Setting file must contain meta and setting key");
        }
        Map<String, Object> meta = (Map<String, Object>) result.get(META);
        if (meta == null || !meta.containsKey(AUTHOR) || !meta.containsKey(MODEL_ID)) {
            throw new IOException("Meta must contain author and model_id key");
        }
        StringBuilder builder = new StringBuilder();
        processText((String) result.get(SETTING)).forEach(value -> builder.append(value).append("\n"));
        return new Parsed((String) meta.get(AUTHOR), (List<String>) meta.get(MODEL_ID), builder.toString());
    }
    /** 解析结果三元组，仅用于在构造器之间传递。 */
    private static final class Parsed {
        private final String author;
        private final List<String> modelId;
        private final String rawSetting;
        private Parsed(String author, List<String> modelId, String rawSetting) {
            this.author = author;
            this.modelId = modelId;
            this.rawSetting = rawSetting;
        }
    }
    private static List<String> processText(String text) {
        return Arrays.stream(StringUtils.split(StringUtils.defaultString(text), "\n"))
                .map(StringUtils::trim)
                .filter(s -> !s.startsWith(COMMENTS))
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toList());
    }
    public String getSetting(EntityMaid maid, String language) {
        return PapiReplacer.replace(rawSetting, maid, language);
    }
    public String getAuthor() {
        return author;
    }
    public List<String> getModelId() {
        return modelId;
    }
}
