package com.github.tartaricacid.touhoulittlemaid.client.resource;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.CustomModelPack;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.condition.ConditionManager;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.Animation;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.MolangParser;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.file.AnimationFile;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.Converter;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.FormatVersion;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.RawGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.tree.RawGeometryTree;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.GeoBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.resource.GeckoLibCache;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.json.JsonAnimationUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonArray;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ChainedJsonException;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 女仆模型 / 动画加载器（上游同名类的最小可用版本）。
 *
 * <p>职责：
 * <ol>
 *     <li>从 {@code assets/<ns>/maid_model.json} 读取模型包索引，登记到 {@link MaidModelRegistry}</li>
 *     <li>把每个基岩版（{@code is_gecko=true}）模型解析进 {@link GeckoLibCache}</li>
 *     <li>把模型包自带的动画文件与默认动画（{@code animation/maid.animation.json} 等）合并后登记</li>
 * </ol>
 *
 * <p>与上游的差异：上游用 {@code CustomPackLoader} 直接读模型包 zip；这里把内置模型包
 * 在构建期展开成普通资源，因此可以直接走原版 {@code ResourceManager}。玩家自放的 zip
 * 模型包需要额外的 zip 读取逻辑，留待后续里程碑。</p>
 */
public final class GeckoModelLoader {
    public static final ResourceLocation DEFAULT_MAID_ANIMATION =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "animation/maid.animation.json");
    public static final ResourceLocation DEFAULT_TAC_ANIMATION =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "animation/tac.animation.json");
    public static final ResourceLocation DEFAULT_CHAIR_ANIMATION =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "animation/chair.animation.json");
    /**
     * 旧版（1.10.0）模型专用的补充动画。
     *
     * <p>旧版骨架只有 {@code legLeft/legRight} 一根腿骨（没有 {@code LeftLowerLeg}、{@code LeftFoot}），
     * 而 {@code maid.animation.json} 的 {@code sit} 是按带膝盖的 Gecko 骨架写的
     * （大腿 -142.5°、小腿 +105°、脚 +37.5°）。直接套用会让整条腿翻到头顶上。
     * 这里的 {@code sit} 按上游 {@code MaidBaseAnimation#sittingPosture} 的数值重写：
     * 大腿 -65°、手臂 -45.7°、裙摆 -32.5°，并把 Root 下移 3.5 单位。</p>
     */
    public static final ResourceLocation LEGACY_SIT_ANIMATION =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "animation/maid_legacy.animation.json");

    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            // 模型包里 model_id / model / texture / animation 都是字符串形式的 ResourceLocation，
            // 需要自定义适配器（上游在 CustomPackLoader.GSON 里做同样的事）。
            .registerTypeAdapter(ResourceLocation.class, new ResourceLocationAdapter())
            .create();

    /**
     * 内置模型包命名空间，顺序与上游 {@code DefaultPackConstant.MAID_SORT} 一致。
     */
    private static final List<String> MAID_PACKS = List.of(
            "touhou_little_maid", "touhou_little_maid_old",
            "geckolib", "authors_and_credits", "minecraft_15th"
    );

    private static AnimationFile defaultMaidAnimation = new AnimationFile();
    private static AnimationFile defaultTacAnimation = new AnimationFile();
    private static AnimationFile defaultChairAnimation = new AnimationFile();
    private static AnimationFile legacySitAnimation = new AnimationFile();

    private GeckoModelLoader() {
    }

    /**
     * 登记惰性构建回调（客户端初始化时调用一次）。
     *
     * <p>模型几何与动画集合都改为「第一次被用到时构建」，见 {@link GeckoLibCache}。</p>
     */
    public static void installResolvers() {
        GeckoLibCache cache = GeckoLibCache.getInstance();
        cache.setGeoModelResolver(id -> {
            MaidModelInfo info = MaidModelRegistry.get(id);
            return info == null ? null : buildGeoModel(info);
        });
        cache.setAnimationResolver(id -> {
            MaidModelInfo info = MaidModelRegistry.get(id);
            return info == null ? null : buildAnimations(info);
        });
    }

    /**
     * 资源重载入口。
     */
    public static void reload() {
        installResolvers();
        // 模型与动画都整体清空：两者都是按需构建的，重载后必须丢弃旧实例，
        // 否则会继续使用旧贴图尺寸 / 旧骨骼。
        GeckoLibCache.getInstance().clearAll();
        MaidModelRegistry.clear();
        ConditionManager.clear();

        defaultMaidAnimation = readAnimationFile(DEFAULT_MAID_ANIMATION);
        defaultTacAnimation = readAnimationFile(DEFAULT_TAC_ANIMATION);
        defaultChairAnimation = readAnimationFile(DEFAULT_CHAIR_ANIMATION);
        legacySitAnimation = readAnimationFile(LEGACY_SIT_ANIMATION);

        for (String namespace : MAID_PACKS) {
            loadMaidPack(namespace);
        }

        TouhouLittleMaid.LOGGER.info("[Futo] Loaded {} maid models ({} gecko prebuilt), {} animation files",
                MaidModelRegistry.all().size(),
                GeckoLibCache.getInstance().getGeoModels().size(),
                GeckoLibCache.getInstance().getAnimations().size());
    }

    // ------------------------------------------------------------------ 模型包

    private static void loadMaidPack(String namespace) {
        ResourceLocation packLocation = new ResourceLocation(namespace, "maid_model.json");
        JsonObject root = readJson(packLocation.toString(), packLocation);
        if (root == null) {
            return;
        }
        MaidModelInfo[] models;
        try {
            models = GSON.fromJson(root.get("model_list"), MaidModelInfo[].class);
        } catch (Exception e) {
            TouhouLittleMaid.LOGGER.error("Failed to read model_list of pack {}", namespace, e);
            return;
        }
        if (models == null) {
            return;
        }
        java.util.List<MaidModelInfo> validModels = new java.util.ArrayList<>();
        for (MaidModelInfo info : models) {
            try {
                info.decorate();
            } catch (Exception e) {
                TouhouLittleMaid.LOGGER.error("Illegal model entry in pack {}", namespace, e);
                continue;
            }
            MaidModelRegistry.put(info);
            // 基岩（Gecko）模型数量很少（4~5 个），重载时直接预构建，避免首帧卡顿。
            // 旧版（1.10.0）模型有 189 个，全部预构建会阻塞渲染线程到客户端卡死，
            // 因此交给 GeckoLibCache 的惰性回调，实体第一次用到时才构建。
            if (info.isGeckoModel()) {
                GeoModel geoModel = buildGeoModel(info);
                if (geoModel != null) {
                    GeckoLibCache.getInstance().putGeoModel(info.getModelId(), geoModel);
                }
                AnimationFile animationFile = buildAnimations(info);
                if (animationFile != null) {
                    GeckoLibCache.getInstance().putAnimationFile(info.getModelId(), animationFile);
                }
            }
            validModels.add(info);
        }

        MaidModelRegistry.putPack(buildPack(namespace, root, validModels));
    }

    /**
     * 从同一个 maid_model.json 里取出包级别的元数据，构造一个 {@link CustomModelPack}。
     *
     * <p>上游把这些字段（pack_name / description / author / version / date / icon）
     * 直接反序列化进 {@code CustomModelPack}；这里手工读，字段名与上游保持一致。</p>
     */
    private static CustomModelPack buildPack(String namespace, JsonObject root,
                                             java.util.List<MaidModelInfo> models) {
        CustomModelPack pack = new CustomModelPack();
        pack.setPackName(getStringOr(root, "pack_name", namespace));
        pack.setDescription(getStringList(root, "description"));
        pack.setAuthor(getStringList(root, "author"));
        pack.setVersion(getStringOrNull(root, "version"));
        pack.setDate(getStringOrNull(root, "date"));
        String icon = getStringOrNull(root, "icon");
        if (icon != null && !icon.isEmpty()) {
            ResourceLocation iconId = ResourceLocation.tryParse(icon);
            if (iconId != null) {
                pack.setIcon(iconId);
            }
        }
        pack.setModelList(models);
        return pack.decorate();
    }

    private static String getStringOr(JsonObject root, String key, String fallback) {
        if (root.has(key) && root.get(key).isJsonPrimitive()) {
            return root.get(key).getAsString();
        }
        return fallback;
    }

    private static String getStringOrNull(JsonObject root, String key) {
        if (root.has(key) && root.get(key).isJsonPrimitive()) {
            return root.get(key).getAsString();
        }
        return null;
    }

    private static java.util.List<String> getStringList(JsonObject root, String key) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (!root.has(key)) {
            return list;
        }
        com.google.gson.JsonElement element = root.get(key);
        if (element.isJsonArray()) {
            for (com.google.gson.JsonElement child : element.getAsJsonArray()) {
                if (child.isJsonPrimitive()) {
                    list.add(child.getAsString());
                }
            }
        } else if (element.isJsonPrimitive()) {
            list.add(element.getAsString());
        }
        return list;
    }


    // ------------------------------------------------------------------ 几何

    /**
     * 解析并登记一个模型。
     *
     * <p>同时支持两种基岩格式：{@code 1.12.0+}（{@code minecraft:geometry} 数组，现代
     * GeckoLib 模型）与 {@code 1.10.0}（{@code geometry.model} 单对象，TLM 旧版模型，
     * 上游默认模型 {@code touhou_little_maid:hakurei_reimu} 灵梦即属此类）。</p>
     *
     * @return 构建失败时返回 {@code null}
     */
    @Nullable
    public static GeoModel buildGeoModel(MaidModelInfo info) {
        ResourceLocation modelLocation = info.getModel();
        if (modelLocation == null) {
            return null;
        }
        try (InputStream stream = open(modelLocation)) {
            if (stream == null) {
                TouhouLittleMaid.LOGGER.warn("Missing geo model file: {}", modelLocation);
                return null;
            }
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject root = GsonHelper.fromJson(GSON, json, JsonObject.class);
            if (root == null) {
                return null;
            }
            String geometryJson;
            if (root.has(LEGACY_GEOMETRY_KEY)) {
                // 1.10.0：字段改写成 1.12 形态
                geometryJson = upgradeLegacyGeometry(root);
            } else {
                // 1.12.0：格式已正确，但骨骼名可能仍是 TLM 旧命名（咲夜、芙兰即属此类），
                // 因此同样需要改名 + 补 Root，否则动画找不到骨骼、模型完全不动。
                JsonElement geometries = root.get("minecraft:geometry");
                if (geometries != null && geometries.isJsonArray()) {
                    normalizeBones(geometries.getAsJsonArray());
                    geometryJson = GSON.toJson(root);
                } else {
                    geometryJson = json;
                }
            }

            RawGeoModel rawModel = Converter.fromJsonString(geometryJson);
            RawGeometryTree rawGeometryTree = RawGeometryTree.parseHierarchy(rawModel);
            return GeoBuilder.getGeoBuilder().constructGeoModel(rawGeometryTree);
        } catch (Throwable e) {
            // 用 Throwable 而不是 Exception：个别超大模型（如芙兰 405 根骨骼）可能触发
            // StackOverflowError，而 Error 不是 Exception，漏掉会让整个资源重载失败。
            TouhouLittleMaid.LOGGER.error("Failed to load geo model {}", modelLocation, e);
            return null;
        }
    }

    /** 旧版（1.10.0）几何的顶层键。 */
    private static final String LEGACY_GEOMETRY_KEY = "geometry.model";

    /**
     * 旧版骨骼名 → 基岩（Gecko）骨骼名。
     *
     * <p>旧版模型由 TLM 自己的格式定义，骨骼名是小写驼峰（{@code armLeft}、{@code legRight}…），
     * 而 {@code animation/maid.animation.json} 里的动画是按 Gecko 命名（{@code LeftArm}、
     * {@code RightLeg}…）写的。改名后这些模型就能直接复用默认动画集，
     * 不需要另外实现上游那套 {@code MaidBaseAnimation} 硬编码动画。</p>
     *
     * <p>这是<b>近似方案</b>：旧版骨架的层级与枢轴和 Gecko 版并不完全相同
     * （例如旧版没有 {@code DownBody}、长发是单根骨），因此个别姿态会与上游略有差异。</p>
     */
    private static final Map<String, String> LEGACY_BONE_ALIASES = Map.ofEntries(
            Map.entry("head", "Head"),
            Map.entry("body", "UpBody"),
            Map.entry("armLeft", "LeftArm"),
            Map.entry("armRight", "RightArm"),
            Map.entry("legLeft", "LeftLeg"),
            Map.entry("legRight", "RightLeg"),
            Map.entry("hairBack", "LongHair"),
            Map.entry("hairLeft", "HairLeft"),
            Map.entry("hairRight", "HairRight"),
            Map.entry("sittingRotationSkirt", "Skirt")
    );

    /** 旧版模型的合成根骨骼名，与默认动画一致。 */
    private static final String LEGACY_ROOT_BONE = "Root";

    /**
     * 把 1.10.0 的 {@code {"geometry.model": {...}}} 改写成 1.12.0 的
     * {@code {"minecraft:geometry": [{"description": {...}, "bones": [...]}]}}。
     *
     * <p>差异只有三处：顶层键名、外层是否包成数组、纹理尺寸字段名
     * （{@code texturewidth} → {@code texture_width}）。骨骼与 cube 的字段
     * （{@code pivot}/{@code cubes}/{@code origin}/{@code size}/{@code uv}/
     * {@code rotation}/{@code mirror}/{@code inflate}）两种格式一致。</p>
     *
     * <p>此外还会做两件事：把骨骼名映射成 Gecko 命名（见 {@link #LEGACY_BONE_ALIASES}），
     * 并补一个 {@code Root} 骨骼作为所有顶层骨骼的父级 —— 旧版模型没有根骨骼，
     * 而默认动画的 {@code sit} 会平移 {@code Root}。</p>
     */
    private static String upgradeLegacyGeometry(JsonObject root) {
        JsonObject source = root.getAsJsonObject(LEGACY_GEOMETRY_KEY);

        JsonObject description = new JsonObject();
        moveIfPresent(source, description, "texturewidth", "texture_width");
        moveIfPresent(source, description, "textureheight", "texture_height");
        moveIfPresent(source, description, "visible_bounds_width", "visible_bounds_width");
        moveIfPresent(source, description, "visible_bounds_height", "visible_bounds_height");
        moveIfPresent(source, description, "visible_bounds_offset", "visible_bounds_offset");

        JsonObject geometry = new JsonObject();
        geometry.add("description", description);
        if (source.has("bones")) {
            geometry.add("bones", source.getAsJsonArray("bones").deepCopy());
        }

        JsonArray geometryArray = new JsonArray();
        geometryArray.add(geometry);

        JsonObject result = new JsonObject();
        result.addProperty("format_version", "1.12.0");
        result.add("minecraft:geometry", geometryArray);

        // 统一在这里做骨骼改名 + 补 Root（1.10 与部分 1.12 模型共用同一套骨架命名）
        normalizeBones(geometryArray);
        return GSON.toJson(result);
    }

    /**
     * 把 TLM 旧版骨骼命名统一成 Gecko 命名，并保证存在 {@code Root} 根骨骼。
     *
     * <p>需要处理的模型有两类，它们的骨骼都是小写命名（{@code head}/{@code body}/{@code armLeft}…）：
     * <ul>
     *     <li>{@code format_version 1.10.0}（{@code geometry.model}）—— 如灵梦、魔理沙、琪露诺</li>
     *     <li>{@code format_version 1.12.0}（{@code minecraft:geometry}）—— 如咲夜、芙兰</li>
     * </ul>
     * 后一类格式本身是合法的，所以之前只按格式判断会漏掉，导致它们「模型能显示但完全无动作」。
     * 对本来就是 Gecko 命名的模型（{@code Root}/{@code Head}/{@code UpBody}…），
     * 这里的别名表不会命中任何名字，因此不会被改动。</p>
     */
    private static void normalizeBones(JsonArray geometryArray) {
        for (JsonElement geometryElement : geometryArray) {
            if (!geometryElement.isJsonObject()) {
                continue;
            }
            JsonObject geometry = geometryElement.getAsJsonObject();
            JsonElement bonesElement = geometry.get("bones");
            if (bonesElement == null || !bonesElement.isJsonArray()) {
                continue;
            }
            JsonArray bones = bonesElement.getAsJsonArray();

            boolean hasRoot = false;
            for (JsonElement boneElement : bones) {
                if (boneElement.isJsonObject() && LEGACY_ROOT_BONE.equals(boneName(boneElement.getAsJsonObject()))) {
                    hasRoot = true;
                    break;
                }
            }
            if (!hasRoot) {
                // 只有「原本没有根骨骼」的旧版模型才插入合成 Root，并把它作为顶层骨骼的父级。
                //
                // 反过来（已有 Root 的 Gecko 模型）绝不能去改无父级骨骼的 parent：
                // 有些模型的层级是 AllBody → Root → …，此时把无父级的 AllBody 挂到 Root 下
                // 就形成 Root ↔ AllBody 的循环父级，几何构建会陷入死循环、资源重载直接卡死。
                JsonArray withRoot = new JsonArray();
                JsonObject rootBone = new JsonObject();
                rootBone.addProperty("name", LEGACY_ROOT_BONE);
                withRoot.add(rootBone);
                for (JsonElement boneElement : bones) {
                    if (boneElement.isJsonObject()) {
                        JsonObject bone = boneElement.getAsJsonObject();
                        if (!bone.has("parent")) {
                            bone.addProperty("parent", LEGACY_ROOT_BONE);
                        }
                    }
                    withRoot.add(boneElement);
                }
                bones = withRoot;
                geometry.add("bones", bones);
            }

            // 骨骼改名（对本来就是 Gecko 命名的模型不会命中，等于无操作）
            for (JsonElement boneElement : bones) {
                if (!boneElement.isJsonObject()) {
                    continue;
                }
                JsonObject bone = boneElement.getAsJsonObject();
                String name = boneName(bone);
                if (LEGACY_ROOT_BONE.equals(name)) {
                    continue;
                }
                String alias = LEGACY_BONE_ALIASES.get(name);
                if (alias != null) {
                    bone.addProperty("name", alias);
                }
                if (bone.has("parent")) {
                    String parentAlias = LEGACY_BONE_ALIASES.get(bone.get("parent").getAsString());
                    if (parentAlias != null) {
                        bone.addProperty("parent", parentAlias);
                    }
                }
            }
        }
    }

    private static String boneName(JsonObject bone) {
        JsonElement name = bone.get("name");
        return name == null ? "" : name.getAsString();
    }

    private static void moveIfPresent(JsonObject from, JsonObject to, String sourceName, String targetName) {
        if (from.has(sourceName)) {
            to.add(targetName, from.get(sourceName));
        }
    }

    // ------------------------------------------------------------------ 动画

    /**
     * 把模型包动画与默认动画合并。
     *
     * <p><b>顺序很重要</b>：模型包自带的同名动画优先，默认动画只补齐缺失的名字。
     * 上游 {@code GeckoModelLoader.registerMaidAnimations} 就是这么做的
     * （{@code if (!animationFile.animations().containsKey(name))}）。
     * 若反过来让默认动画覆盖，模型包专属的 {@code idle} / {@code sit} 会被替换成
     * 标准女仆模型的同名动画，骨骼名不匹配就会表现为「站着不动」「坐下没反应」。</p>
     *
     * @return 构建失败时返回 {@code null}
     */
    @Nullable
    public static AnimationFile buildAnimations(MaidModelInfo info) {
        AnimationFile animationFile = new AnimationFile();
        List<ResourceLocation> animationFiles = info.getAnimation();
        if (animationFiles != null) {
            for (ResourceLocation location : animationFiles) {
                // Only real basemodel animation files are resources. The .js entries
                // are pseudo-paths for InnerAnimations and must not be parsed as JSON.
                if (!location.getPath().endsWith(".animation.json")) {
                    continue;
                }
                merge(animationFile, readAnimationFile(location));
            }
        }
        // 旧版（1.10.0）模型：先放旧版专用动画，覆盖默认的 sit。
        // 旧骨架没有膝盖/脚骨，套用 Gecko 版 sit（大腿 -142.5°）会让腿翻到头顶。
        if (!info.isGeckoModel()) {
            merge(animationFile, legacySitAnimation);
        }
        fillMissing(animationFile, defaultMaidAnimation);
        fillMissing(animationFile, defaultTacAnimation);
        if (animationFile.animations().isEmpty()) {
            return null;
        }
        animationFile.animations().keySet().forEach(name -> ConditionManager.addTest(info.getModelId(), name));
        return animationFile;
    }

    /**
     * 只补充缺失的动画名，不覆盖已有项。
     */
    private static void fillMissing(AnimationFile main, @Nullable AnimationFile defaults) {
        if (defaults == null) {
            return;
        }
        defaults.animations().forEach((name, animation) -> {
            if (!main.animations().containsKey(name)) {
                main.putAnimation(name, animation);
            }
        });
    }

    private static void merge(AnimationFile main, @Nullable AnimationFile other) {
        if (other == null) {
            return;
        }
        other.animations().forEach(main::putAnimation);
    }

    @Nullable
    private static AnimationFile readAnimationFile(ResourceLocation location) {
        JsonObject jsonObject = readJson("animation " + location, location);
        if (jsonObject == null) {
            return null;
        }
        AnimationFile animationFile = new AnimationFile();
        MolangParser parser = GeckoLibCache.getInstance().parser;
        for (Map.Entry<String, JsonElement> entry : JsonAnimationUtils.getAnimations(jsonObject)) {
            String animationName = entry.getKey();
            try {
                Animation animation = JsonAnimationUtils.deserializeJsonToAnimation(
                        JsonAnimationUtils.getAnimation(jsonObject, animationName), parser);
                animationFile.putAnimation(animationName, animation);
            } catch (ChainedJsonException e) {
                TouhouLittleMaid.LOGGER.error("Illegal animation {} in {}", animationName, location, e);
            }
        }
        return animationFile;
    }

    // ------------------------------------------------------------------ 资源读取

    @Nullable
    private static JsonObject readJson(String description, ResourceLocation location) {
        try (InputStream stream = open(location)) {
            if (stream == null) {
                TouhouLittleMaid.LOGGER.debug("Missing {}: {}", description, location);
                return null;
            }
            return GsonHelper.fromJson(GSON, new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
        } catch (Exception e) {
            TouhouLittleMaid.LOGGER.error("Failed to read {}", location, e);
            return null;
        }
    }

    @Nullable
    private static InputStream open(ResourceLocation location) {
        net.minecraft.server.packs.resources.Resource resource;
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

    /**
     * {@code "namespace:path"} ↔ {@link ResourceLocation}。
     */
    private static final class ResourceLocationAdapter
            implements JsonSerializer<ResourceLocation>, JsonDeserializer<ResourceLocation> {
        @Override
        public ResourceLocation deserialize(JsonElement json, java.lang.reflect.Type typeOfT,
                                            JsonDeserializationContext context) {
            return new ResourceLocation(json.getAsString());
        }

        @Override
        public JsonElement serialize(ResourceLocation src, java.lang.reflect.Type typeOfSrc,
                                     JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }
    }
}
