package com.github.tartaricacid.touhoulittlemaid.client.resource;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.GeoBlockRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.Converter;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.RawGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.tree.RawGeometryTree;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.GeoBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoCube;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoQuad;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoVertex;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 静态（无动画）基岩方块模型的加载、缓存与包围盒。
 *
 * <p>上游 1.20.1 把这批方块从「原版 JSON 模型」换成了「GeckoLib 基岩模型」，
 * 只有这样才能做出那种高精度外形：{@code models/bedrock/block/*.json} 里是
 * {@code minecraft:geometry}，纹理动辄 512×512，一个祭坛就有上千个 cube。</p>
 *
 * <h2>为什么可以复用实体那条管线</h2>
 * 本移植的 {@code geckolib3} 是完整的一套，几何解析（{@code Converter} →
 * {@code RawGeometryTree#parseHierarchy} → {@code GeoBuilder}）与渲染
 * （{@code IGeoRenderer}）都不依赖动画；{@link AnimatedGeoModel} 也只是给
 * {@code GeoModel} 包了一层可变骨骼，静止时旋转/位移全为 0，画出来就是静态模型。
 *
 * <h2>与实体模型的区别</h2>
 * <b>不做骨骼改名、也不补 {@code Root}。</b>{@link GeckoModelLoader} 那两步是为了让
 * 旧版女仆模型能复用 {@code maid.animation.json}；方块模型没有动画，而且它们的骨骼名
 * 是 {@code new} / {@code A} / {@code B} 这种，改名表根本不会命中。
 *
 * <h2>{@link #floorOffset} 与「上下颠倒 + 沉进地里」</h2>
 *
 * <p>换用基岩模型之前，这两个渲染器画的是原版 {@code EntityModel}，其顶点空间是
 * <b>Y 向下</b>的，所以代码里有一句 {@code ZN(180)} 把它翻上来，另外还有手调的
 * {@code +1.5} / {@code -1.5} 竖直偏移去凑各自模型的坐标原点。</p>
 *
 * <p>基岩几何（见 {@code GeoCube}）本来就是 <b>Y 向上</b>的 —— 女仆就是这么画的 ——
 * 于是那句 {@code ZN(180)} 反而把方块模型翻了个底朝天，手调偏移也不再对应新的原点
 * （祭坛的几何有相当一部分压在原点下方，神社的原点却正好在底面）。
 * 硬编码任何数字都注定只对一个模型正确。</p>
 *
 * <p>所以改成按模型自身量出来的包围盒计算：{@link #floorOffset} 返回
 * {@code -max(0, minY)}（只压不抬，因为 y = 0 本身就是方块底面），
 * 绝不硬编码任何数字。神社量出来的 minY 正好 0，祭坛是 −0.125（装饰件故意埋进地面），两者都不会被抬高。</p>
 */
public final class BedrockBlockModels {
    /** 名称 → 模型；值可能是 {@code null}（文件缺失 / 解析失败）。 */
    private static final Map<String, AnimatedGeoModel> CACHE = new HashMap<>();
    /** 名称 → 包围盒 {@code [minX, minY, minZ, maxX, maxY, maxZ]}（单位：格）。 */
    private static final Map<String, float[]> BOUNDS = new HashMap<>();

    private BedrockBlockModels() {
    }

    /**
     * 取一个方块模型；加载失败返回 {@code null}（调用方负责跳过渲染，不能抛异常，
     * 否则一格坏模型会让整个世界的方块实体渲染全挂）。
     */
    @Nullable
    public static AnimatedGeoModel get(String name) {
        if (!CACHE.containsKey(name)) {
            load(name);
        }
        return CACHE.get(name);
    }

    /**
     * 把模型摆到方块底面所需的竖直位移（单位：格）。
     *
     * <p>规则：<b>只往下压，绝不往上抬</b> —— {@code -max(0, minY)}。</p>
     *
     * <p>为什么不能无条件用 {@code -minY}：这些基岩方块模型的坐标约定就是
     * <b>y = 0 是方块底面</b>。祭坛模型里有 17 个未旋转的立方体底部正好在 0
     * （6 根 {@code pillar*} 柱子、{@code bone56} 的 10 个、{@code bone62} 的 8 个），
     * 那就是落地的部分；另有 42 个立方体在 0 以下、最低到 −0.125，
     * 是带旋转的装饰件，本来就要稍微埋进地面。</p>
     *
     * <p>所以按包围盒下限抬升会把整台抬高 0.125 格 —— 肉眼就是「差一点点悬空、
     * 但说不清差多少」。反过来，如果某个模型整体浮在 0 以上（{@code minY > 0}，
     * 说明它的原点比几何更低），那才需要往下压。</p>
     *
     * <p>神社的 {@code minY} 实测正好 0.000，两种规则都是 0，因此不受影响。</p>
     */
    public static float floorOffset(String name) {
        get(name);
        float[] b = BOUNDS.get(name);
        if (b == null) {
            return 0;
        }
        // push down, never lift: y = 0 is the block floor, geometry below 0 is meant to be sunk
        return -Math.max(0, b[1]);
    }

    /** 模型包围盒；缺失时返回 {@code null}。 */
    @Nullable
    public static float[] bounds(String name) {
        get(name);
        return BOUNDS.get(name);
    }

    public static void clear() {
        CACHE.clear();
        BOUNDS.clear();
    }

    private static void load(String name) {
        CACHE.put(name, null);
        BOUNDS.put(name, null);

        ResourceLocation location = new ResourceLocation(TouhouLittleMaid.MOD_ID,
                "models/bedrock/block/" + name + ".json");
        try (InputStream stream = open(location)) {
            if (stream == null) {
                TouhouLittleMaid.LOGGER.warn("Missing bedrock block model: {}", location);
                return;
            }
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            RawGeoModel rawModel = Converter.fromJsonString(json);
            RawGeometryTree tree = RawGeometryTree.parseHierarchy(rawModel);
            GeoModel geo = GeoBuilder.getGeoBuilder().constructGeoModel(tree);
            flipHorizontalFaces(geo);
            AnimatedGeoModel model = new AnimatedGeoModel(geo);

            float[] b = GeoBlockRenderer.measureBounds(model);
            CACHE.put(name, model);
            BOUNDS.put(name, b);
            // 注意占位符数量必须和参数个数严格一致：SLF4J 不会因为参数不足报错，
            // 而是把多余的 "{}" 原样打进日志（上一版就漏了一个参数，日志里出现 "model minY={}"）。
            // 这里的 offset 与 floorOffset 用的是同一条规则（只压不抬）。
            TouhouLittleMaid.LOGGER.info(
                    "[Futo] Block model {} loaded: {} bones, bbox x[{}..{}] y[{}..{}] z[{}..{}] floorOffset={} (model minY={})",
                    name, model.bones().size(),
                    fmt(b[0]), fmt(b[3]), fmt(b[1]), fmt(b[4]), fmt(b[2]), fmt(b[5]),
                    fmt(-Math.max(0, b[1])), fmt(b[1]));
        } catch (Throwable e) {
            // 与 GeckoModelLoader 一致：几何层级异常时可能抛 StackOverflowError，不是 Exception
            TouhouLittleMaid.LOGGER.error("Failed to load bedrock block model {}", location, e);
        }
    }

    private static String fmt(float v) {
        return String.format("%.3f", v);
    }

    /**
     * 把「水平面」（UP / DOWN）的贴图 <b>v 方向</b>翻过来。
     *
     * <h2>为什么必须翻</h2>
     * <p>这批基岩方块模型（棋盘、棋子、祭坛、神社……）是给上游自己的
     * {@code simplebedrockmodel} 画的，那套管线的顶点空间是原版 {@code ModelPart} 的
     * <b>Y 向下</b>；本移植用的是 {@code GeoCube} 的 <b>Y 向上</b>空间。
     * 两者差一个 {@code ZN(180)} 翻转，几何上本移植已经不需要它（见类注释），
     * 但<b>贴图方向没有跟着换</b>：{@code GeoCube} 把 box-UV 的 up 面接成
     * 「贴图 <i>上</i>边 ↔ 世界 +z」，而原版约定是「贴图上边 ↔ 世界 −z」。</p>
     *
     * <p>棋盘是唯一把这个差异暴露出来的地方：它的木纹格线左右对称，看不出问题，
     * 但中间「楚河 漢界」两个字是斜着写的，一照面就是上下颠倒的。
     * 棋子顶面的汉字同理。</p>
     *
     * <h2>为什么这样改是安全的</h2>
     * <p><b>只动贴图坐标，不动任何一个顶点位置</b> —— 所以包围盒、落地位移、
     * 棋子摆位、{@code isClickResetArea} 全部不受影响，只会让贴图换个方向。
     * 具体做法是在每张水平面自己的 uv 矩形内做 {@code v -> min+max-v}：
     * 因为棋盘格线与两块「重开」竖板都关于 v 中轴对称（实测格线 v 5..122、
     * 竖板 v 17..45 与 81..109），翻过来之后位置一模一样。</p>
     *
     * <p>这条路只给 {@code BedrockBlockModels}（方块）用，不碰女仆 —— 女仆模型是
     * GeckoLib 原生 Y 向上画的，本来就是对的。</p>
     */
    private static void flipHorizontalFaces(GeoModel model) {
        for (GeoBone bone : model.topLevelBones()) {
            flipBone(bone);
        }
    }

    private static void flipBone(GeoBone bone) {
        for (GeoCube cube : bone.cubes()) {
            for (GeoQuad quad : cube.quads) {
                if (quad.direction != Direction.UP && quad.direction != Direction.DOWN) {
                    continue;
                }
                float min = Float.MAX_VALUE;
                float max = -Float.MAX_VALUE;
                for (GeoVertex vertex : quad.vertices) {
                    min = Math.min(min, vertex.textureV);
                    max = Math.max(max, vertex.textureV);
                }
                if (max - min < 1.0E-7F) {
                    // 压成一条线的面（零厚度 cube 的塌陷结果）没有可翻的方向
                    continue;
                }
                for (int i = 0; i < quad.vertices.length; i++) {
                    GeoVertex vertex = quad.vertices[i];
                    quad.vertices[i] = vertex.setTextureUV(vertex.textureU, min + max - vertex.textureV);
                }
            }
        }
        for (GeoBone child : bone.children()) {
            flipBone(child);
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
