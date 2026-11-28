package com.github.tartaricacid.touhoulittlemaid.client.resource;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.Converter;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.RawGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.tree.RawGeometryTree;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.GeoBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoModel;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 静态（无动画）基岩<b>实体</b>模型的加载与缓存。
 */
public final class BedrockEntityModels {
    /** 名称 → 模型；值可能是 {@code null}（文件缺失 / 解析失败）。 */
    private static final Map<String, AnimatedGeoModel> CACHE = new HashMap<>();

    private BedrockEntityModels() {
    }

    /**
     * 取一个实体模型；加载失败返回 {@code null}。
     *
     * @param name {@code models/bedrock/entity/<name>.json} 里的文件名（不含扩展名）
     */
    @Nullable
    public static AnimatedGeoModel get(String name) {
        if (!CACHE.containsKey(name)) {
            load(name);
        }
        return CACHE.get(name);
    }

    /** 资源包重载后必须调用：几何与贴图是一起换的，缓存旧模型会画错贴图。 */
    public static void clear() {
        CACHE.clear();
    }

    private static void load(String name) {
        // 先占位，避免解析过程中被递归调用时无限递归
        CACHE.put(name, null);

        ResourceLocation location = new ResourceLocation(TouhouLittleMaid.MOD_ID,
                "models/bedrock/entity/" + name + ".json");
        try (InputStream stream = open(location)) {
            if (stream == null) {
                TouhouLittleMaid.LOGGER.warn("Missing bedrock entity model: {}", location);
                return;
            }
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            RawGeoModel rawModel = Converter.fromJsonString(json);
            RawGeometryTree tree = RawGeometryTree.parseHierarchy(rawModel);
            GeoModel geo = GeoBuilder.getGeoBuilder().constructGeoModel(tree);
            AnimatedGeoModel model = new AnimatedGeoModel(geo);
            CACHE.put(name, model);
            TouhouLittleMaid.LOGGER.debug("[Futo] Entity bedrock model {} loaded: {} bones",
                    name, model.bones().size());
        } catch (Throwable e) {
            // 与 BedrockBlockModels 一致：几何层级异常时可能抛 StackOverflowError，不是 Exception
            TouhouLittleMaid.LOGGER.error("Failed to load bedrock entity model {}", location, e);
        }
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

