package com.github.tartaricacid.touhoulittlemaid.client.resource;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.Converter;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.RawGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.tree.RawGeometryTree;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.GeoBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 静态（无动画）基岩方块模型的加载与缓存。
 *
 * <p>上游 1.20.1 把这批方块从「原版 JSON 模型」换成了「GeckoLib 基岩模型」，
 * 只有这样才能做出那种高精度外形：{@code models/bedrock/block/*.json} 里是
 * {@code format_version 1.12.0} 的 {@code minecraft:geometry}，纹理动辄 512×512，
 * 一个祭坛就有上千个 cube —— 原版 JSON 模型做不出来。</p>
 *
 * <h2>为什么可以复用实体那条管线</h2>
 * 本移植的 {@code geckolib3} 是完整的一套，几何解析（{@code Converter} →
 * {@code RawGeometryTree#parseHierarchy} → {@code GeoBuilder}）与渲染
 * （{@code IGeoRenderer}）都不依赖动画；{@link AnimatedGeoModel} 也只是给
 * {@code GeoModel} 包了一层可变骨骼，静止时旋转/位移全为 0，画出来就是静态模型。
 * 所以方块模型只要拿到一个 {@link AnimatedGeoModel} 就够，不需要另写渲染器。
 *
 * <h2>与实体模型的区别</h2>
 * <b>不做骨骼改名、也不补 {@code Root}。</b>{@link GeckoModelLoader} 那两步是为了让
 * 旧版女仆模型能复用 {@code maid.animation.json}；方块模型没有动画，而且它们的骨骼名
 * 是 {@code new} / {@code A} / {@code B} 这种，改名表根本不会命中，插入的
 * 合成 {@code Root} 也只会多一层无意义的变换。
 *
 * <h2>缓存与重载</h2>
 * 第一次请求时加载，之后直接用；{@link #clear()} 由资源重载监听器调用，
 * 这样资源包替换模型后不需要重启游戏。
 */
public final class BedrockBlockModels {
    /** 值可能是 {@code null}（文件缺失 / 解析失败），用 {@code containsKey} 区分「没加载过」。 */
    private static final Map<String, AnimatedGeoModel> CACHE = new HashMap<>();

    private BedrockBlockModels() {
    }

    /**
     * 取一个方块模型；加载失败返回 {@code null}（调用方负责跳过渲染，不能抛异常，
     * 否则一格坏模型会让整个世界的方块实体渲染全挂）。
     */
    @Nullable
    public static AnimatedGeoModel get(String name) {
        if (CACHE.containsKey(name)) {
            return CACHE.get(name);
        }
        AnimatedGeoModel model = load(name);
        CACHE.put(name, model);
        return model;
    }

    public static void clear() {
        CACHE.clear();
    }

    @Nullable
    private static AnimatedGeoModel load(String name) {
        ResourceLocation location = new ResourceLocation(TouhouLittleMaid.MOD_ID,
                "models/bedrock/block/" + name + ".json");
        try (InputStream stream = open(location)) {
            if (stream == null) {
                TouhouLittleMaid.LOGGER.warn("Missing bedrock block model: {}", location);
                return null;
            }
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            RawGeoModel rawModel = Converter.fromJsonString(json);
            RawGeometryTree tree = RawGeometryTree.parseHierarchy(rawModel);
            AnimatedGeoModel model = new AnimatedGeoModel(GeoBuilder.getGeoBuilder().constructGeoModel(tree));
            TouhouLittleMaid.LOGGER.info("[Futo] Block model {} loaded: {} bones, {} cubes", name,
                    model.bones().size(), countCubes(model));
            return model;
        } catch (Throwable e) {
            // 与 GeckoModelLoader 一致：几何层级异常时可能抛 StackOverflowError，不是 Exception
            TouhouLittleMaid.LOGGER.error("Failed to load bedrock block model {}", location, e);
            return null;
        }
    }

    /** 只用于日志：统计整棵骨骼树里的方块面数，确认模型不是空的。 */
    private static int countCubes(AnimatedGeoModel model) {
        int total = 0;
        for (com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone bone : model.bones().values()) {
            total += bone.geoBone().cubes().size();
        }
        return total;
    }

    @Nullable
    private static InputStream open(ResourceLocation location) {
        Resource resource;
        try {
            resource = Minecraft.getInstance().getResourceManager().getResource(location);
        } catch (Exception e) {
            TouhouLittleMaid.LOGGER.debug("Missing {}", location);
            return null;
        }
        try {
            return resource.getInputStream();
        } catch (Exception e) {
            TouhouLittleMaid.LOGGER.error("Failed to open {}", location, e);
            return null;
        }
    }
}

