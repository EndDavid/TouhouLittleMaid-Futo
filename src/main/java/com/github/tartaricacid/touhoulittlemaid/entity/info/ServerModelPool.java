package com.github.tartaricacid.touhoulittlemaid.entity.info;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
/**
 * 服务端的「女仆模型 id 池」，用来给新生成的女仆随机挑一个外观。
 *
 * <p>上游把这件事放在 {@code entity/info/ServerCustomPackLoader} +
 * {@code ServerMaidModels} 里：扫描游戏目录下的 {@code tlm_custom_pack/}，
 * 把每个模型包的 {@code assets/<domain>/maid_model.json} 解析出来，收集 {@code model_id}。
 * 那样做的前提是「模型包都在游戏目录里」—— 而上游的内置模型其实是<b>资源包</b>
 * （jar 里的 {@code assets/touhou_little_maid/tlm_custom_pack/touhou_little_maid-1.0.0.zip}），
 * 服务端资源管理器根本不加载 {@code assets}，所以上游在干净的专用服务端上
 * 这个池子会是空的（它自己在 {@code finalizeSpawn} 里留了「这里居然可能为 0」的注释）。</p>
 *
 * <p>本移植版换了个更可靠的数据来源：直接读<b>模组 jar 自己</b>的
 * {@code assets/<domain>/maid_model.json}。Fabric Loader 的
 * {@code ModContainer#findPath} 在开发环境与生产环境都指向真实的 NIO 路径，
 * 于是「有哪些域」可以直接遍历出来，专用服务端与单人游戏的行为完全一致，
 * 自定义模型包（资源包形式）也照样能被客户端选中 —— 只是服务端不知道它们的 id，
 * 与上游的限制相同（上游也只认自己扫到的那些）。</p>
 *
 * <h2>为什么只保留 id，不解析整个 {@code MaidModelInfo}</h2>
 * <p>随机只需要 id；而 {@code MaidModelInfo} 住在 {@code client.resource.pojo} 里，
 * 依赖一堆客户端字段。直接读 JSON 里的两个键（{@code model_id} 与 {@code easter_egg}）
 * 就够了，也不会把客户端类拖进服务端。</p>
 *
 * <p><b>彩蛋模型要排除</b>：上游 {@code ServerCustomPackLoader} 里那句
 * {@code if (maidModelInfo.getEasterEgg() == null)} 就是干这个的。</p>
 */
public final class ServerModelPool {
    /**
     * 池子未加载时的兜底：仍是「最初那几个」可用模型。
     *
     * <p>只有在 {@code assets} 一个都读不到（例如资源被裁剪过）时才会用到，
     * 保证 {@code finalizeSpawn} 永远有东西可选。</p>
     */
    private static final String[] FALLBACK = {
            "touhou_little_maid:hakurei_reimu",
            "touhou_little_maid:kirisame_marisa",
            "touhou_little_maid:cirno",
            "touhou_little_maid:izayoi_sakuya",
            "touhou_little_maid:flandre_scarlet",
            "geckolib:winefox"
    };
    private static List<String> ids;
    private ServerModelPool() {
    }
    /**
     * 全部可用模型 id（不含彩蛋）。第一次调用时扫描，之后直接返回缓存。
     */
    public static List<String> getIds() {
        if (ids == null) {
            ids = load();
        }
        return ids;
    }
    /**
     * 随机取一个模型 id；池子为空时返回 {@code null}（调用方应当保持原样）。
     *
     * <p>参数类型是 {@link java.util.Random} 而不是 1.19+ 的 {@code RandomSource}：
     * 1.18.2 的 {@code Entity#random} 就是前者。</p>
     */
    public static String randomId(java.util.Random random) {
        List<String> all = getIds();
        if (all.isEmpty()) {
            return null;
        }
        return all.get(random.nextInt(all.size()));
    }
    public static void clear() {
        ids = null;
    }
    private static List<String> load() {
        List<String> out = new ArrayList<>();
        ModContainer mod = FabricLoader.getInstance().getModContainer(TouhouLittleMaid.MOD_ID).orElse(null);
        if (mod == null) {
            Collections.addAll(out, FALLBACK);
            return out;
        }
        Path assets = mod.findPath("assets").orElse(null);
        if (assets == null || !Files.isDirectory(assets)) {
            Collections.addAll(out, FALLBACK);
            return out;
        }
        try (Stream<Path> domains = Files.list(assets)) {
            domains.filter(Files::isDirectory).sorted().forEach(domain -> readPack(out, domain));
        } catch (IOException e) {
            TouhouLittleMaid.LOGGER.warn("[Futo] failed to scan model packs for the random model pool", e);
        }
        if (out.isEmpty()) {
            Collections.addAll(out, FALLBACK);
        }
        TouhouLittleMaid.LOGGER.debug("[Futo] random model pool: {} models", out.size());
        return out;
    }
    private static void readPack(List<String> out, Path domainDir) {
        Path json = domainDir.resolve("maid_model.json");
        if (!Files.isRegularFile(json)) {
            return;
        }
        try (Reader reader = new InputStreamReader(Files.newInputStream(json), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return;
            }
            JsonObject obj = root.getAsJsonObject();
            JsonElement list = obj.get("model_list");
            if (list == null || !list.isJsonArray()) {
                return;
            }
            JsonArray models = list.getAsJsonArray();
            for (JsonElement element : models) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject model = element.getAsJsonObject();
                // 彩蛋模型不参与随机（上游同样跳过）
                if (model.has("easter_egg") && !model.get("easter_egg").isJsonNull()) {
                    continue;
                }
                JsonElement id = model.get("model_id");
                if (id == null || !id.isJsonPrimitive()) {
                    continue;
                }
                String modelId = id.getAsString();
                if (!modelId.isEmpty() && !out.contains(modelId)) {
                    out.add(modelId);
                }
            }
        } catch (Exception e) {
            TouhouLittleMaid.LOGGER.warn("[Futo] failed to read model pack {}", json, e);
        }
    }
}
