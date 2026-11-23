package com.github.tartaricacid.touhoulittlemaid.entity.info;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ParseI18n;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「模型 id → 模型译名」的查询表，客户端与服务端共用。
 *
 * <p><b>它存在的理由：</b>女仆没被命名时，上游显示的不是类型名（{@code Maid}），
 * 而是<b>她当前模型的译名</b>——{@code 博丽灵梦}、{@code Cirno}……
 * 1.18.2 上游是在 {@code EntityMaid#getTypeName()} 里从
 * {@code ServerCustomPackLoader.SERVER_MAID_MODELS} 取
 * {@code MaidModelInfo#getName()} 实现的。本移植版没有那个服务端模型池
 * （原因见 {@link ServerModelPool} 的注释），所以这里改成<b>直接读模组 jar 里的
 * {@code assets/<命名空间>/maid_model.json}</b>：Fabric 的 {@code ModContainer#findPath}
 * 在开发环境与生产环境都指向真实路径，专用服务端与单人游戏的结果一致。</p>
 *
 * <h2>为什么返回 {@link Component} 而不是字符串</h2>
 * <p>模型名在 JSON 里天然是「纯文本 或 {@code {翻译键}}」两种写法之一
 * （见 {@link ParseI18n}），未填 {@code name} 字段时上游还会补成
 * {@code {model.<命名空间>.<路径>.name}}。保留成 {@code Component} 之后：</p>
 * <ul>
 *     <li>客户端渲染时按玩家语言解析 —— 中文看到「博丽灵梦」，英文看到「Reimu Hakurei」；</li>
 *     <li>服务端把它塞进聊天栏消息 / 名牌时不必自己翻译，
 *         {@link Component} 会随包同步到客户端再解析。</li>
 * </ul>
 *
 * <h2>限制（与上游相同）</h2>
 * <p>只能查到「模组 jar 自带」的模型。玩家放进 {@code resourcepacks} 的第三方模型包，
 * 服务端本来就看不见（服务端不加载 {@code assets}），所以这里也不处理；
 * 查不到时退回 {@code {model.<命名空间>.<路径>.name}}，与 {@code MaidModelInfo}
 * 的默认值一致，玩家若装了对应语言文件仍然能显示正确名字。</p>
 */
public final class ModelDisplayNames {
    /**
     * 模型 id → JSON 里的原始 name 字符串（可能是纯文本，也可能是 {@code {翻译键}}）。
     *
     * <p>用并发容器：{@link com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid#getTypeName()}
     * 会被客户端渲染线程与服务端线程同时调用（单人游戏里两者是不同线程）。</p>
     */
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private ModelDisplayNames() {
    }

    /**
     * 按模型 id 取名字。只要 id 合法就一定非 {@code null}
     * （查不到时是 {@code {model.<命名空间>.<路径>.name}} 翻译键）；id 为空 / 非法时返回 {@code null}。
     *
     * @param modelId 女仆身上同步的模型 id，允许省略命名空间（按 {@code touhou_little_maid:} 补）
     */
    public static Component getName(String modelId) {
        String raw = getRawName(modelId);
        return raw == null ? null : ParseI18n.parse(raw);
    }

    /**
     * 取 JSON 里的原始 name 字符串（调试 / 需要 key 本身时用）。
     *
     * @return id 合法时非空；id 为空 / 非法时 {@code null}
     */
    public static String getRawName(String modelId) {
        String id = normalize(modelId);
        if (id == null) {
            return null;
        }
        return CACHE.computeIfAbsent(id, ModelDisplayNames::findRawName);
    }

    /**
     * 清空缓存（资源重载时由调用方决定是否需要）。
     */
    public static void clear() {
        CACHE.clear();
    }

    /**
     * 把玩家可能写成 {@code cirno} / {@code touhou_little_maid:cirno} 的 id 归一化。
     *
     * @return 合法 id 的 {@code toString()}；非法时 {@code null}
     */
    private static String normalize(String modelId) {
        String id = StringUtils.trimToNull(modelId);
        if (id == null) {
            return null;
        }
        if (!id.contains(":")) {
            id = TouhouLittleMaid.MOD_ID + ":" + id;
        }
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? null : location.toString();
    }

    /**
     * 真正去 jar 里翻 {@code maid_model.json}。同一个 id 只会走一次（调用方有缓存）。
     */
    private static String findRawName(String id) {
        ResourceLocation location = new ResourceLocation(id);
        String fallback = defaultNameKey(location);
        ModContainer mod = FabricLoader.getInstance().getModContainer(location.getNamespace()).orElse(null);
        if (mod == null) {
            return fallback;
        }
        Path json = mod.findPath("assets/" + location.getNamespace() + "/maid_model.json").orElse(null);
        if (json == null || !Files.isRegularFile(json)) {
            return fallback;
        }
        try (Reader reader = new InputStreamReader(Files.newInputStream(json), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return fallback;
            }
            JsonElement list = root.getAsJsonObject().get("model_list");
            if (list == null || !list.isJsonArray()) {
                return fallback;
            }
            JsonArray models = list.getAsJsonArray();
            for (JsonElement element : models) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject model = element.getAsJsonObject();
                if (!idMatches(model, id, location)) {
                    continue;
                }
                JsonElement name = model.get("name");
                if (name != null && name.isJsonPrimitive() && StringUtils.isNotBlank(name.getAsString())) {
                    return name.getAsString();
                }
                // 该模型没写 name：与 MaidModelInfo 的反序列化默认值保持一致
                return fallback;
            }
        } catch (Exception e) {
            TouhouLittleMaid.LOGGER.warn("[Futo] failed to read model name from {}", json, e);
        }
        return fallback;
    }

    /**
     * {@code model_id} 可能写全名，也可能省略命名空间（玩家手写的模型包常见）。
     */
    private static boolean idMatches(JsonObject model, String id, ResourceLocation location) {
        JsonElement element = model.get("model_id");
        if (element == null || !element.isJsonPrimitive()) {
            return false;
        }
        String jsonId = element.getAsString();
        if (id.equals(jsonId)) {
            return true;
        }
        return !jsonId.contains(":") && location.getPath().equals(jsonId);
    }

    /**
     * 未填 {@code name} 时的默认翻译键，与上游 {@code MaidModelInfo} 的反序列化逻辑一致。
     */
    private static String defaultNameKey(ResourceLocation location) {
        return String.format("{model.%s.%s.name}", location.getNamespace(), location.getPath());
    }
}
