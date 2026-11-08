package com.github.tartaricacid.touhoulittlemaid.client.resource;

import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.CustomModelPack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 已加载的女仆模型索引（{@code model_id} → {@link MaidModelInfo}）。
 *
 * <p>上游把这份数据放在 {@code CustomPackLoader} 的静态字段里，本移植版拆成独立的小类，
 * 便于渲染层按 {@code model_id} 直接取模型描述。</p>
 */
public final class MaidModelRegistry {
    /**
     * 兜底模型 = 上游默认模型 {@code touhou_little_maid:hakurei_reimu}（灵梦）。
     *
     * <p>它是旧版（1.10.0）模型，几何由 {@code GeckoModelLoader} 在首次使用时惰性构建，
     * 加载时会把骨骼名映射成 Gecko 命名（{@code head→Head}、{@code body→UpBody}…）并补一个
     * 合成 {@code Root}，因此可以直接复用基岩动画管线（{@code animation/maid.animation.json}）。</p>
     */
    public static final ResourceLocation FALLBACK_MODEL_ID = new ResourceLocation("touhou_little_maid", "hakurei_reimu");

    private static final Map<ResourceLocation, MaidModelInfo> MODELS = new LinkedHashMap<>();

    /** 妯″瀷鍖呭垪琛紝椤哄簭鍗崇晫闈㈤《閮ㄩ〉绛剧殑椤哄簭銆?*/
    private static final List<CustomModelPack> PACKS = new ArrayList<>();

    private MaidModelRegistry() {
    }

    public static void clear() {
        MODELS.clear();
        PACKS.clear();
    }

    public static void put(MaidModelInfo info) {
        MODELS.put(info.getModelId(), info);
    }

    public static void putPack(CustomModelPack pack) {
        PACKS.add(pack);
    }

    /** 妯″瀷鍖呭垪琛紙鎹㈣偆鐣岄潰鐢級銆傜┖鍒楄〃琛ㄧず杩樻病鍔犺浇杩囪祫婧愩€?*/
    public static List<CustomModelPack> allPacks() {
        return PACKS;
    }

    public static Collection<MaidModelInfo> all() {
        return MODELS.values();
    }

    public static boolean contains(ResourceLocation id) {
        return MODELS.containsKey(id);
    }

    @Nullable
    public static MaidModelInfo get(@Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        return MODELS.get(id);
    }

    /**
     * 按字符串形式的 model_id 取模型（女仆实体同步数据是 String）。
     *
     * <p>容错规则（玩家经常在 {@code /data merge} 里漏写命名空间）：
     * <ol>
     *     <li>完整 id 命中 → 直接用</li>
     *     <li>没有命名空间（如 {@code winefox}）时，前缀 {@code touhou_little_maid:} 与
     *         {@code geckolib:} 各试一次，再按 path 全局搜索</li>
     *     <li>{@code minecraft:xxx} 找不到时按 path 全局搜索</li>
     * </ol>
     * 找不到返回 {@code null}（调用方会回落到默认模型）。</p>
     */
    @Nullable
    public static MaidModelInfo get(@Nullable String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(modelId);
        if (id == null) {
            return getByPath(modelId);
        }
        MaidModelInfo info = MODELS.get(id);
        if (info != null) {
            return info;
        }
        if (!modelId.contains(":")) {
            info = MODELS.get(new ResourceLocation("touhou_little_maid", modelId));
            if (info == null) {
                info = MODELS.get(new ResourceLocation("geckolib", modelId));
            }
            if (info != null) {
                return info;
            }
        }
        // 命名空间写错 / 漏写：退化为按 path 匹配
        return getByPath(id.getPath());
    }

    @Nullable
    private static MaidModelInfo getByPath(String path) {
        for (Map.Entry<ResourceLocation, MaidModelInfo> entry : MODELS.entrySet()) {
            if (entry.getKey().getPath().equals(path)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 取兜底模型；如果兜底模型也没加载出来就返回 {@code null}。
     */
    @Nullable
    public static MaidModelInfo fallback() {
        return MODELS.get(FALLBACK_MODEL_ID);
    }
}
