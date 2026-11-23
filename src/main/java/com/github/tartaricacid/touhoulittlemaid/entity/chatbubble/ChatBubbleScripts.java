package com.github.tartaricacid.touhoulittlemaid.entity.chatbubble;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 「模型 id → 聊天气泡台本」的加载器，客户端与服务端共用。
 *
 * <h2>为什么要自己从 jar 里读</h2>
 * <p>1.18.2 上游把台本挂在 {@code ServerCustomPackLoader.SERVER_MAID_MODELS} 上，
 * 由它扫描游戏目录的 {@code tlm_custom_pack/}。本移植版没有那一层服务端模型池
 * （原因见 {@code ServerModelPool} 的注释），所以这里改成<b>直接读模组 jar 里的
 * {@code assets/<命名空间>/maid_model.json}</b> —— Fabric 的
 * {@code ModContainer#findPath} 在开发环境与生产环境都指向真实路径，
 * 专用服务端与单人游戏的结果一致。</p>
 *
 * <h2>两级台本</h2>
 * <ol>
 *     <li>默认台本：{@code assets/touhou_little_maid/tlm_custom_pack/default_chat_bubble.json}
 *         —— 所有女仆共用的那几句（含表情图标与颜文字）；</li>
 *     <li>模型台本：{@code maid_model.json} 里该模型的 {@code chat_bubble} 字段。
 *         上游的类型是<b>内联对象</b>，这里同时兼容「内联对象」与
 *         「{@code 命名空间:路径} 字符串（指向另一个 json）」两种写法，
 *         因为第三方模型包两种都能见到。</li>
 * </ol>
 * <p>合并规则由 {@link ChatBubbleInfo#decorate(ChatBubbleInfo)} 负责。</p>
 *
 * <h2>缓存</h2>
 * <p>按模型 id 缓存合并后的结果。资源重载（{@code /reload} 或换资源包）时
 * 调 {@link #clear()} 即可，服务端与客户端各自清一次。</p>
 */
public final class ChatBubbleScripts {
    /** 默认台本在 jar 内的路径（与上游常量一致）。 */
    public static final String DEFAULT_CHAT_BUBBLE_PATH =
            "assets/" + TouhouLittleMaid.MOD_ID + "/tlm_custom_pack/default_chat_bubble.json";
    /**
     * 台本专用 Gson。
     *
     * <p>必须注册 {@link ChatText.Serializer}：台本里的每一条台词是<b>字符串</b>，
     * 解析成 ChatText 时要按「{@code %贴图%} 还是文本」分流，默认的 Gson
     * 没有这个能力（会直接按字段名反射，字段全空）。上游在
     * {@code ServerCustomPackLoader} 里注册同一个适配器。</p>
     */
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ChatText.class, new ChatText.Serializer())
            // ResourceLocation 没有默认的 JSON 适配器：台本里的 "bg" 是普通字符串，
            // 不注册就抛 Expected BEGIN_OBJECT but was STRING（实测踩到过一次）。
            // 上游在 ServerCustomPackLoader 的 GSON 里注册了同一个适配器。
            .registerTypeAdapter(ResourceLocation.class, new ResourceLocationAdapter())
            .create();

    private static final Map<String, ChatBubbleInfo> CACHE = Maps.newConcurrentMap();
    private static ChatBubbleInfo defaultInfo;

    private ChatBubbleScripts() {
    }

    /**
     * 清空缓存（资源重载时调用）。
     */
    public static void clear() {
        CACHE.clear();
        defaultInfo = null;
    }

    /**
     * 默认台本；读不到时返回一个空的 {@link ChatBubbleInfo}（不是 {@code null}，
     * 免得调用方到处判空）。
     */
    public static ChatBubbleInfo getDefault() {
        if (defaultInfo == null) {
            defaultInfo = loadDefault();
        }
        return defaultInfo;
    }

    /**
     * 取某个模型的台本（已与默认台本合并）。
     *
     * @param modelId 女仆的模型 id，允许省略命名空间
     * @return 永远非 {@code null}
     */
    public static ChatBubbleInfo get(String modelId) {
        String id = normalize(modelId);
        if (id == null) {
            return getDefault();
        }
        return CACHE.computeIfAbsent(id, ChatBubbleScripts::loadModel);
    }

    /**
     * 这个模型用来画气泡底图的贴图。
     *
     * <p>1.18.2 的默认值是 {@code default1.png}（3x2 的小方格），
     * 而本移植版的气泡渲染已经换成 1.20.1 的九宫格风格。两者格式不兼容，
     * 所以这里的规则是：</p>
     * <ul>
     *     <li>台本里写的仍是那三张旧网格图（{@code default1/2/3.png}）时，
     *         返回 {@link #MODERN_BACKGROUND}，让渲染侧走九宫格；</li>
     *     <li>其它贴图原样返回（视为现代九宫格格式，第三方模型包若自带就照用）。</li>
     * </ul>
     * <p>这条映射写在 README 的「与上游差异」里，不是猜的 —— 旧网格图 60×40、
     * 主体是 20×20 的 3×2 拼块，用九宫格九切会得到糊成一团的边框。</p>
     */
    public static ResourceLocation getBackground(String modelId) {
        ResourceLocation bg = get(modelId).getBg();
        if (bg == null || isLegacyGridTexture(bg)) {
            return MODERN_BACKGROUND;
        }
        return bg;
    }

    /**
     * 1.20.1 的正文气泡底图（白底 + 淡蓝边），与 {@code ChatBubbleRenderer#TYPE_2} 同一个文件。
     */
    public static final ResourceLocation MODERN_BACKGROUND =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/entity/chat_bubble/type2.png");

    /**
     * 是否是 1.18.2 那三张 60×40 的旧网格图。
     */
    public static boolean isLegacyGridTexture(ResourceLocation location) {
        return TouhouLittleMaid.MOD_ID.equals(location.getNamespace())
                && (location.getPath().equals("textures/chat_bubble/bg/default1.png")
                || location.getPath().equals("textures/chat_bubble/bg/default2.png")
                || location.getPath().equals("textures/chat_bubble/bg/default3.png"));
    }

    // ---------------------------------------------------------------- 读文件

    private static ChatBubbleInfo loadDefault() {
        ChatBubbleInfo info = readJson(DEFAULT_CHAT_BUBBLE_PATH, ChatBubbleInfo.class);
        if (info == null) {
            TouhouLittleMaid.LOGGER.warn("[Futo] default chat bubble script is missing: {}",
                    DEFAULT_CHAT_BUBBLE_PATH);
            return new ChatBubbleInfo();
        }
        return info;
    }

    private static ChatBubbleInfo loadModel(String id) {
        ResourceLocation location = new ResourceLocation(id);
        ChatBubbleInfo own = readModelChatBubble(location);
        if (own == null) {
            // 没有模型专属台本：直接用默认那份（不再 decorate，省一次深拷贝）
            return getDefault();
        }
        return own.decorate(getDefault());
    }

    /**
     * 从 {@code maid_model.json} 里找到这个模型，取出它的 {@code chat_bubble}。
     *
     * @return 没有 / 解析失败时 {@code null}
     */
    private static ChatBubbleInfo readModelChatBubble(ResourceLocation location) {
        JsonObject model = findModelJson(location);
        if (model == null) {
            return null;
        }
        JsonElement chatBubble = model.get("chat_bubble");
        if (chatBubble == null || chatBubble.isJsonNull()) {
            return null;
        }
        if (chatBubble.isJsonObject()) {
            return GSON.fromJson(chatBubble, ChatBubbleInfo.class);
        }
        if (chatBubble.isJsonPrimitive() && chatBubble.getAsJsonPrimitive().isString()) {
            // 「命名空间:路径」指向另一个 json
            ResourceLocation path = ResourceLocation.tryParse(chatBubble.getAsString());
            if (path == null) {
                return null;
            }
            String json = "assets/" + path.getNamespace() + "/" + path.getPath();
            if (!json.endsWith(".json")) {
                json = json + ".json";
            }
            return readJson(json, ChatBubbleInfo.class);
        }
        return null;
    }

    /**
     * 在 {@code maid_model.json} 的 {@code model_list} 里找这个模型的条目。
     */
    private static JsonObject findModelJson(ResourceLocation location) {
        Path json = resolveInMod(location.getNamespace(),
                "assets/" + location.getNamespace() + "/maid_model.json");
        if (json == null) {
            return null;
        }
        try (Reader reader = new InputStreamReader(Files.newInputStream(json), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return null;
            }
            JsonElement list = root.getAsJsonObject().get("model_list");
            if (list == null || !list.isJsonArray()) {
                return null;
            }
            JsonArray models = list.getAsJsonArray();
            for (JsonElement element : models) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject model = element.getAsJsonObject();
                JsonElement modelId = model.get("model_id");
                if (modelId == null || !modelId.isJsonPrimitive()) {
                    continue;
                }
                String value = modelId.getAsString();
                if (location.toString().equals(value)
                        || (!value.contains(":") && location.getPath().equals(value))) {
                    return model;
                }
            }
        } catch (Exception e) {
            TouhouLittleMaid.LOGGER.warn("[Futo] failed to read model json {}", json, e);
        }
        return null;
    }

    private static <T> T readJson(String path, Class<T> type) {
        Path json = resolveInMod(TouhouLittleMaid.MOD_ID, path);
        if (json == null) {
            return null;
        }
        try (Reader reader = new InputStreamReader(Files.newInputStream(json), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        } catch (Exception e) {
            TouhouLittleMaid.LOGGER.warn("[Futo] failed to read chat bubble script {}", path, e);
            return null;
        }
    }

    private static Path resolveInMod(String namespace, String path) {
        ModContainer mod = FabricLoader.getInstance().getModContainer(namespace).orElse(null);
        if (mod == null) {
            return null;
        }
        Path resolved = mod.findPath(path).orElse(null);
        if (resolved == null || !Files.isRegularFile(resolved)) {
            return null;
        }
        return resolved;
    }

    /**
     * 把 {@code cirno} / {@code touhou_little_maid:cirno} 归一化。
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
     * 台本里某一段（main / special / other）的某一种台词，已拷贝成可安全修改的列表。
     */
    public static List<ChatText> getTexts(ChatBubbleInfo info, Section section, String key) {
        ChatBubbleInfo.Text text = info.getText();
        if (text == null) {
            return Lists.newArrayList();
        }
        Map<String, List<ChatText>> map = switch (section) {
            case MAIN -> text.getMain();
            case SPECIAL -> text.getSpecial();
            case OTHER -> text.getOther();
        };
        List<ChatText> list = map == null ? null : map.get(key);
        if (list == null || list.isEmpty()) {
            return Lists.newArrayList();
        }
        return Lists.newArrayList(list);
    }

    /**
     * 台本的三段。
     */
    public enum Section {
        MAIN, SPECIAL, OTHER
    }
    /**
     * {@link ResourceLocation} 的 JSON 适配器：读写成普通字符串。
     *
     * <p>只用得到「读」，但 {@code JsonSerializer} 一并实现，
     * 免得将来写台本时又要补一次。</p>
     */
    private static final class ResourceLocationAdapter
            implements com.google.gson.JsonDeserializer<ResourceLocation>,
            com.google.gson.JsonSerializer<ResourceLocation> {
        @Override
        public ResourceLocation deserialize(com.google.gson.JsonElement json, java.lang.reflect.Type type,
                                           com.google.gson.JsonDeserializationContext context) {
            return new ResourceLocation(json.getAsString());
        }
        @Override
        public com.google.gson.JsonElement serialize(ResourceLocation location, java.lang.reflect.Type type,
                                                     com.google.gson.JsonSerializationContext context) {
            return new com.google.gson.JsonPrimitive(location.toString());
        }
    }
}

