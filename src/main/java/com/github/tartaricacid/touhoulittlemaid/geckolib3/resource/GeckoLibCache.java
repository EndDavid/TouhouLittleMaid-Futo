package com.github.tartaricacid.touhoulittlemaid.geckolib3.resource;

import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.molang.TLMBinding;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.molang.YSMBinding;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.MolangParser;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.file.AnimationFile;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoModel;
import com.github.tartaricacid.touhoulittlemaid.molang.runtime.binding.ObjectBinding;
import com.google.common.collect.Maps;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 模型 / 动画缓存。
 *
 * <p>上游在资源重载时会把<b>全部</b>模型的几何一次性构建好。1.18.2 的内置模型包共 194 个,
 * 其中 189 个是旧版（1.10.0）模型，一次性构建会把渲染线程阻塞到客户端卡死
 * （实测日志停在贴图集创建之后不再前进）。</p>
 *
 * <p>因此这里改为「按需惰性构建」：重载时只登记加载回调，实体第一次用到某个
 * {@code model_id} 时才构建那一个几何（之后进缓存）。代价是首次渲染某个模型时
 * 有一次几毫秒的抖动，换来的是启动不再卡死、且旧版模型（含默认灵梦）全部可用。</p>
 */
public class GeckoLibCache {
    private static final Map<String, ObjectBinding> EXTRA_BINDING = new HashMap<>();
    private static GeckoLibCache INSTANCE;
    public final MolangParser parser = createMolangParser();
    private final Map<ResourceLocation, AnimationFile> animations = Maps.newHashMap();
    private final Map<ResourceLocation, GeoModel> geoModels = Maps.newHashMap();
    /** 构建失败的 id，避免每帧重复尝试（否则每次渲染都会抛出异常刷屏）。 */
    private final Set<ResourceLocation> failedGeoModels = new HashSet<>();
    private final Set<ResourceLocation> failedAnimations = new HashSet<>();
    @Nullable
    private Function<ResourceLocation, GeoModel> geoModelResolver;
    @Nullable
    private Function<ResourceLocation, AnimationFile> animationResolver;

    public static GeckoLibCache getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new GeckoLibCache();
            return INSTANCE;
        }
        return INSTANCE;
    }

    private static MolangParser createMolangParser() {
        EXTRA_BINDING.put("ysm", YSMBinding.INSTANCE);
        EXTRA_BINDING.put("tlm", TLMBinding.INSTANCE);
        return new MolangParser(EXTRA_BINDING);
    }

    /** 登记惰性构建回调（由 {@code GeckoModelLoader} 在客户端初始化时调用一次）。 */
    public void setGeoModelResolver(@Nullable Function<ResourceLocation, GeoModel> resolver) {
        this.geoModelResolver = resolver;
    }

    public void setAnimationResolver(@Nullable Function<ResourceLocation, AnimationFile> resolver) {
        this.animationResolver = resolver;
    }

    /** 立即构建并登记（重载时预构建少量常用模型时用）。 */
    public void putGeoModel(ResourceLocation id, GeoModel model) {
        geoModels.put(id, model);
        failedGeoModels.remove(id);
    }

    public void putAnimationFile(ResourceLocation id, AnimationFile file) {
        animations.put(id, file);
        failedAnimations.remove(id);
    }

    /**
     * 取模型几何；未构建过则按需构建。
     *
     * @return 模型不存在或构建失败时返回 {@code null}
     */
    @Nullable
    public GeoModel getGeoModel(ResourceLocation id) {
        GeoModel model = geoModels.get(id);
        if (model != null) {
            return model;
        }
        if (geoModelResolver == null || failedGeoModels.contains(id)) {
            return null;
        }
        model = geoModelResolver.apply(id);
        if (model == null) {
            failedGeoModels.add(id);
            return null;
        }
        geoModels.put(id, model);
        return model;
    }

    /**
     * 取模型的动画集合；未构建过则按需构建。
     */
    @Nullable
    public AnimationFile getAnimationFile(ResourceLocation id) {
        AnimationFile file = animations.get(id);
        if (file != null) {
            return file;
        }
        if (animationResolver == null || failedAnimations.contains(id)) {
            return null;
        }
        file = animationResolver.apply(id);
        if (file == null) {
            failedAnimations.add(id);
            return null;
        }
        animations.put(id, file);
        return file;
    }

    /** 是否已经构建过（不触发构建）。 */
    public boolean hasGeoModel(ResourceLocation id) {
        return geoModels.containsKey(id);
    }

    public Map<ResourceLocation, AnimationFile> getAnimations() {
        return animations;
    }

    public Map<ResourceLocation, GeoModel> getGeoModels() {
        return geoModels;
    }

    /** 资源重载：清空全部缓存与失败记录。 */
    public void clearAll() {
        geoModels.clear();
        animations.clear();
        failedGeoModels.clear();
        failedAnimations.clear();
    }
}
