package com.github.tartaricacid.touhoulittlemaid.client.resource;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.GeoBlockRenderer;
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
 * 静态（无动画）基岩方块模型的加载、缓存与包围盒。
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
            AnimatedGeoModel model = new AnimatedGeoModel(geo);

            float[] b = GeoBlockRenderer.measureBounds(model);
            CACHE.put(name, model);
            BOUNDS.put(name, b);
            // 注意占位符数量必须和参数个数严格一致：SLF4J 不会因为参数不足报错，
            // 而是把多余的 "{}" 原样打进日志（上一版就漏了一个参数，日志里出现 "model minY={}"）。
            // 这里的 offset 与 floorOffset 用的是同一条规则（只压不抬）。
            TouhouLittleMaid.LOGGER.debug(
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

    /*
     * 这里<b>不做</b>任何 uv 翻转 —— 这段注释是给下一次想加的人看的。
     *
     * 曾经加过一个 flipHorizontalFaces，理由是「GeoCube 的 box-UV 与
     * simplebedrockmodel 差一个 v 方向」。那个结论是错的，而且只做单轴镜像
     * 恰好会把字变成镜像（汉字左右翻转），比原来的问题更难看出病因。
     *
     * 真实情况：两套库的 box-UV 完全一样（GeoQuad 与 BedrockCubeBox#compile 都是
     * 「第一个顶点拿 (u2,v1)」，up 面取的是同一个 uv 矩形）。差别只在渲染姿态：
     * 上游在 ZN(180) 之外还对 NORTH / SOUTH 多转 180°，本移植版少了这一句，
     * 于是这两个朝向下整张贴图转了 180° —— 格线偏一格、重开区跑到另一侧、
     * 字上下颠倒。修在 TileEntityCChessRenderer 的姿态里，不在贴图坐标里。
     */

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
