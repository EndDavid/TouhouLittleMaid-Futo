package com.github.tartaricacid.touhoulittlemaid.geckolib3.resource;

import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.molang.TLMBinding;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.molang.YSMBinding;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.MolangParser;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.file.AnimationFile;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoModel;
import com.github.tartaricacid.touhoulittlemaid.molang.runtime.binding.ObjectBinding;
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
 * <h2>加载时机与上游一致：资源重载时一次性建好</h2>
 *
 * <p>上游 {@code CustomPackLoader#loadModelElement} 对模型包里的<b>每个</b>条目都调用一次
 * {@code loadMaidModel(...)}，也就是在加载阶段就把全部模型（1.18.2 的默认包共 194 个）
 * 构建成内存对象；{@code ReloadResourceEvent#asyncReloadAllPack()} 把这一整轮工作交给
 * {@code Util.backgroundExecutor()}，结束时打印 {@code "Model loading time: {} ms"}。
 * 因此换肤界面里切换 {@code ModelId}、画 55 个缩略图都没有任何构建开销。</p>
 *
 * <h2>为什么这里不用后台线程</h2>
 *
 * <p>上游可以放到后台，是因为它<b>直接读磁盘</b>上的模型包（{@code Path} / {@code ZipFile}）。
 * 本移植版走原版 {@code ResourceManager#getResource}：它内部有自己的锁，并且只在资源重载
 * 的那个线程上安全。把这份工作丢到别的线程会和它互相等待 —— 之前的实现正是如此，
 * 表现为卡在 {@code Loading terrain...}。所以这里保持普通 {@link HashMap}，
 * 全部内容在 {@code GeckoModelLoader#reload()} 里同步填好，运行期只读。</p>
 */
public class GeckoLibCache {
    private static final Map<String, ObjectBinding> EXTRA_BINDING = new HashMap<>();
    private static GeckoLibCache INSTANCE;
    public final MolangParser parser = createMolangParser();
    private final Map<ResourceLocation, AnimationFile> animations = new HashMap<>();
    private final Map<ResourceLocation, GeoModel> geoModels = new HashMap<>();
    /** 构建失败的 id，避免每帧重复尝试（否则每次渲染都会抛出异常刷屏）。 */
    private final Set<ResourceLocation> failedGeoModels = new HashSet<>();
    private final Set<ResourceLocation> failedAnimations = new HashSet<>();
    /**
     * 兜底用的按需构建回调。
     *
     * <p>重载时已经把所有模型都构建好了，正常情况下不会走到这里；保留它是为了
     * 「某个 id 在重载之后才出现」这种意外情况不至于直接渲染失败。</p>
     */
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

    /** 登记兜底构建回调（由 {@code GeckoModelLoader} 在客户端初始化时调用一次）。 */
    public void setGeoModelResolver(@Nullable Function<ResourceLocation, GeoModel> resolver) {
        this.geoModelResolver = resolver;
    }

    public void setAnimationResolver(@Nullable Function<ResourceLocation, AnimationFile> resolver) {
        this.animationResolver = resolver;
    }

    /** 是否已经有构建好的几何（只读探测，不触发构建）。 */
    public boolean hasGeoModel(ResourceLocation id) {
        return geoModels.containsKey(id);
    }

    /** 立即登记一个已经构建好的几何。 */
    public void putGeoModel(ResourceLocation id, GeoModel model) {
        geoModels.put(id, model);
        failedGeoModels.remove(id);
    }

    public void putAnimationFile(ResourceLocation id, AnimationFile file) {
        animations.put(id, file);
        failedAnimations.remove(id);
    }

    /**
     * 取模型几何。
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
     * 取模型的动画集合。
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

