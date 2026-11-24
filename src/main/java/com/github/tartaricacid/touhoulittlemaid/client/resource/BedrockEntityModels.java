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
 *
 * <p>为什么需要它：上游 1.20.1 把「史莱姆 → 灵梦油库里」「岩浆怪 → 魔理沙油库里」这一对替换
 * 换成了 GeckoLib 基岩模型（{@code models/bedrock/entity/*.json}）。其中</p>
 *
 * <ul>
 *     <li>{@code reimu_yukkuri} 与 1.18.2 的手写网格逐盒等价，所以那一半直接照抄
 *         {@code EntityYukkuriModel} 的手写 {@code LayerDefinition}，不需要这个类；</li>
 *     <li>{@code marisa_yukkuri} <b>不等价</b>：贴图从 64×64 换成 128×128，UV 全是新的
 *         （{@code (45,16)} / {@code (0,42)} / {@code (64,33)} …），骨骼也从 9 根变成 14 根。
 *         手写网格等于把上游的 JSON 手工重排一遍，还容易抄错，所以这里直接读 JSON。</li>
 * </ul>
 *
 * <h2>为什么复用方块那条加载管线</h2>
 * {@link BedrockBlockModels} 已经证明这套管线与「方块 / 实体」无关：
 * {@code Converter → RawGeometryTree#parseHierarchy → GeoBuilder#constructGeoModel}
 * 只处理几何，不读 {@code geometry.<identifier>} 的名字，也不要求有动画。
 * 与方块版唯一的区别是目录（{@code models/bedrock/entity/} 而不是 {@code .../block/}），
 * 所以这里只重复那 30 行加载代码，不共享缓存 —— 两边的键空间不同，混用容易撞名。
 *
 * <h2>坐标是否要额外换算</h2>
 * <b>不要。</b>{@code GeoCube} 构造时已经把 texel 除以 16（{@code size.multiply(0.0625)}），
 * 并把 x 轴翻成 Minecraft 的方向（{@code origin = -(origin.x + size.x) / 16}）；
 * 而 y 轴两边本来就同向（向上），枢轴约定也一样。<b>pivot 不是 cube 原点的父坐标系</b> ——
 * {@code GeoCube} 里的 {@code origin} 是绝对模型坐标，pivot 只用于绕点旋转。
 * 所以模型画出来的位置就是 JSON 里写的那个绝对位置，不需要按 pivot 叠加。
 *
 * <p>实测该模型的绝对包围盒（未计骨骼旋转）：y ∈ [−0.13, 1.09]、x ∈ [−0.44, 0.44]、
 * z ∈ [−0.43, 0.45]（单位：格），底面基本就在脚底平面上，因此渲染时不需要竖直平移。</p>
 *
 * <p>失败时返回 {@code null} 而不是抛异常：一格坏模型不该让整个实体渲染循环崩掉
 * （与 {@link BedrockBlockModels#get(String)} 同一原则）。</p>
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
            TouhouLittleMaid.LOGGER.info("[Futo] Entity bedrock model {} loaded: {} bones",
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

