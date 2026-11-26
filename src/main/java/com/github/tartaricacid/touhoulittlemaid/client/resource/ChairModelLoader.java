package com.github.tartaricacid.touhoulittlemaid.client.resource;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.ChairModelInfo;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.CustomModelPack;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.file.AnimationFile;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.resource.GeckoLibCache;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 坐垫模型加载器，对应上游 {@code CustomPackLoader} 里 chair 那一半。
 *
 * <p>数据源是各模型包根目录的 {@code maid_chair.json}。与女仆模型完全同构：
 * 条目里写 {@code model_id}，几何按 {@code models/entity/<path>.json}、
 * 贴图按 {@code textures/entity/<path>.png} 自动推导，然后统一构建进
 * {@link GeckoLibCache}（旧版 1.10.0 与 Gecko 两种格式共用同一条管线）。</p>
 *
 * <p>包顺序与上游 {@code DefaultPackConstant.CHAIR_SORT} 一致：
 * {@code touhou_little_maid} → {@code geckolib} → {@code next_update_model}。</p>
 */
public final class ChairModelLoader {
    /** 上游 {@code DefaultPackConstant.CHAIR_SORT}。 */
    private static final List<String> CHAIR_PACKS = List.of(
            "touhou_little_maid", "geckolib", "next_update_model"
    );

    private ChairModelLoader() {
    }

    /**
     * 资源重载入口。必须在 {@link GeckoModelLoader#reload()} <b>之后</b>调用 ——
     * 那边会 {@code GeckoLibCache.clearAll()}，先加载坐垫会被清空。
     */
    public static void reload() {
        ChairModelRegistry.clear();
        long startNanos = System.nanoTime();
        int loaded = 0;
        for (String namespace : CHAIR_PACKS) {
            loaded += loadChairPack(namespace);
        }
        // 外部包（tlm_custom_pack）排在**内置包之后**，与女仆模型那边同样的理由：
        // 页签顺序「内置在最前」，重名时内置赢。
        loaded += CustomPackLoader.loadExternalChairPacks();
        long millis = (System.nanoTime() - startNanos) / 1_000_000L;
        TouhouLittleMaid.LOGGER.info("[Futo] Chair model loading time: {} ms ({} chair models)", millis, loaded);
    }

    private static int loadChairPack(String namespace) {
        ResourceLocation packLocation = new ResourceLocation(namespace, "maid_chair.json");
        JsonObject root = GeckoModelLoader.readJson("chair pack " + packLocation, packLocation);
        if (root == null) {
            return 0;
        }
        ChairModelInfo[] models;
        try {
            models = GeckoModelLoader.GSON.fromJson(root.get("model_list"), ChairModelInfo[].class);
        } catch (Exception e) {
            TouhouLittleMaid.LOGGER.error("Failed to read chair model_list of pack {}", namespace, e);
            return 0;
        }
        if (models == null) {
            return 0;
        }

        List<ChairModelInfo> validModels = new ArrayList<>();
        for (ChairModelInfo info : models) {
            try {
                info.decorate();
            } catch (Exception e) {
                TouhouLittleMaid.LOGGER.error("Illegal chair entry in pack {}", namespace, e);
                continue;
            }
            ChairModelRegistry.putInfo(info);
            GeoModel geoModel = GeckoModelLoader.buildGeoModel(info.getModel());
            if (geoModel != null) {
                GeckoLibCache.getInstance().putGeoModel(info.getModelId(), geoModel);
            }
            AnimationFile animationFile = buildAnimations(info);
            if (animationFile != null) {
                GeckoLibCache.getInstance().putAnimationFile(info.getModelId(), animationFile);
            }
            validModels.add(info);
        }
        ChairModelRegistry.putPack(buildPack(namespace, root, validModels));
        return validModels.size();
    }

    /**
     * 从同一个 {@code maid_chair.json} 里取出包级别的元数据。
     *
     * <p>字段名与女仆包的 {@code maid_model.json} 完全一致（pack_name / description /
     * author / version / date / icon / icon_delay），所以直接复用 {@link CustomModelPack}；
     * 上游也是同一个泛型类，只是把 {@code ChairModelInfo} 当元素类型。</p>
     */
    static CustomModelPack<ChairModelInfo> buildPack(String namespace, JsonObject root,
                                                             List<ChairModelInfo> models) {
        CustomModelPack<ChairModelInfo> pack = new CustomModelPack<>();
        pack.setPackName(getStringOr(root, "pack_name", namespace));
        pack.setDescription(getStringList(root, "description"));
        pack.setAuthor(getStringList(root, "author"));
        pack.setVersion(getStringOrNull(root, "version"));
        pack.setDate(getStringOrNull(root, "date"));
        pack.setIcon(parseIcon(root));
        if (root.has("icon_delay") && root.get("icon_delay").isJsonPrimitive()) {
            try {
                pack.setIconDelay(root.get("icon_delay").getAsInt());
            } catch (RuntimeException ignored) {
                // 非法值退回缺省，不影响加载
            }
        }
        pack.setModelList(models);
        return pack.decorate();
    }

    /**
     * 坐垫动画：模型包自带的同名动画优先，缺失的从 {@code animation/chair.animation.json} 补齐。
     *
     * <p>与女仆模型的差别：坐垫<b>不</b>合并 {@code maid.animation.json} —— 两套骨架毫无关系。</p>
     *
     * @return 没有任何动画时返回 {@code null}（旧版坐垫绝大多数是纯静态模型）
     */
    @Nullable
    static AnimationFile buildAnimations(ChairModelInfo info) {
        AnimationFile animationFile = new AnimationFile();
        List<ResourceLocation> animationFiles = info.getAnimation();
        if (animationFiles != null) {
            for (ResourceLocation location : animationFiles) {
                // .js 条目是 JS 动画引擎的伪路径，本移植版没有那个引擎，跳过
                if (!location.getPath().endsWith(".animation.json")) {
                    continue;
                }
                GeckoModelLoader.mergeAnimationFiles(animationFile,
                        GeckoModelLoader.readAnimationFile(location));
            }
        }
        GeckoModelLoader.fillMissing(animationFile, GeckoModelLoader.getDefaultChairAnimation());
        return animationFile.animations().isEmpty() ? null : animationFile;
    }

    private static String getStringOr(JsonObject root, String key, String fallback) {
        if (root.has(key) && root.get(key).isJsonPrimitive()) {
            return root.get(key).getAsString();
        }
        return fallback;
    }

    private static List<String> getStringList(JsonObject root, String key) {
        List<String> list = new ArrayList<>();
        if (!root.has(key)) {
            return list;
        }
        JsonElement element = root.get(key);
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (child.isJsonPrimitive()) {
                    list.add(child.getAsString());
                }
            }
        } else if (element.isJsonPrimitive()) {
            list.add(element.getAsString());
        }
        return list;
    }

    @Nullable
    private static ResourceLocation parseIcon(JsonObject root) {
        if (root.has("icon") && root.get("icon").isJsonPrimitive()) {
            return ResourceLocation.tryParse(root.get("icon").getAsString());
        }
        return null;
    }

    @Nullable
    private static String getStringOrNull(JsonObject root, String key) {
        if (root.has(key) && root.get(key).isJsonPrimitive()) {
            return root.get(key).getAsString();
        }
        return null;
    }
}