package com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * 基岩版模型格式版本。
 * <p>
 * 上游使用 Maven 的 {@code VersionRange} 做区间判断，那是 Forge 环境顺带提供的依赖;
 * Fabric 侧不引入该库，这里用等价的语义实现:
 * <ul>
 *     <li>{@link #LEGACY} —— 恰好 1.10.0</li>
 *     <li>{@link #NEW} —— 大于等于 1.12.0（1.14.0 / 1.16.0 / 1.21.0 全部归入此类）</li>
 * </ul>
 */
@JsonAdapter(FormatVersion.Serializer.class)
public enum FormatVersion {
    /**
     * 旧版本基岩版模型，仅限 1.10.0
     */
    LEGACY("1.10.0"),
    /**
     * 新版本基岩版模型，往后的 1.14.0，1.16.0 1.21.0 通通用此版本读取
     */
    NEW("1.12.0");

    private final int[] version;

    FormatVersion(String version) {
        this.version = parse(version);
    }

    public static FormatVersion forValue(String value) throws IOException {
        if (value == null || value.isEmpty()) {
            throw new IOException("Empty bedrock model format_version");
        }
        int[] input = parse(value);
        if (compare(input, NEW.version) >= 0) {
            return NEW;
        }
        return LEGACY;
    }

    /**
     * 按 {@code major.minor.patch} 拆分，忽略 {@code -beta} 之类后缀。
     */
    private static int[] parse(String value) {
        String cleaned = value.trim();
        int cut = cleaned.indexOf('-');
        if (cut > 0) {
            cleaned = cleaned.substring(0, cut);
        }
        String[] parts = cleaned.split("\\.");
        int[] result = new int[3];
        for (int i = 0; i < 3 && i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                TouhouLittleMaid.LOGGER.warn("Illegal bedrock model format_version segment: {}", parts[i]);
                result[i] = 0;
            }
        }
        return result;
    }

    private static int compare(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) {
                return Integer.compare(a[i], b[i]);
            }
        }
        return 0;
    }

    public String toValue() {
        return this.version[0] + "." + this.version[1] + "." + this.version[2];
    }

    protected static class Serializer implements JsonSerializer<FormatVersion>, JsonDeserializer<FormatVersion> {
        @Override
        public FormatVersion deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            try {
                return FormatVersion.forValue(json.getAsString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public JsonElement serialize(FormatVersion src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toValue());
        }
    }
}
