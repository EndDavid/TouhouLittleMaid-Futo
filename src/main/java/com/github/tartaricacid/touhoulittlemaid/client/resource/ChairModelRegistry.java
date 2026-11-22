package com.github.tartaricacid.touhoulittlemaid.client.resource;

import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.ChairModelInfo;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.CustomModelPack;
import com.github.tartaricacid.touhoulittlemaid.item.ItemChair;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 已加载的坐垫模型索引（{@code model_id} → {@link ChairModelInfo}）。
 *
 * <p>上游把这份数据放在 {@code CustomPackLoader.CHAIR_MODELS}（以及服务端的
 * {@code ServerCustomPackLoader.SERVER_CHAIR_MODELS}）里。本移植版按
 * {@link MaidModelRegistry} 的既有做法拆成独立小类。</p>
 *
 * <p>只保留真正会用到的四个查询：模型 id 集合（创造模式物品栏要按它铺满）、坐高、
 * 是否可骑、是否无重力。渲染用的几何与动画在 {@link com.github.tartaricacid.touhoulittlemaid.geckolib3.resource.GeckoLibCache}
 * 里按同一个 {@code model_id} 索引。</p>
 */
public final class ChairModelRegistry {
    /**
     * 兜底坐垫，与上游 {@code ItemChair#DEFAULT_MODEL_ID} 一致。
     */
    public static final String DEFAULT_CHAIR_ID = "touhou_little_maid:cushion";

    private static final Map<ResourceLocation, ChairModelInfo> INFOS = new LinkedHashMap<>();
    private static final List<CustomModelPack<ChairModelInfo>> PACKS = new ArrayList<>();

    private ChairModelRegistry() {
    }

    public static void clear() {
        INFOS.clear();
        PACKS.clear();
    }

    public static void putInfo(ChairModelInfo info) {
        INFOS.put(info.getModelId(), info);
    }

    public static void putPack(CustomModelPack<ChairModelInfo> pack) {
        PACKS.add(pack);
    }

    public static List<CustomModelPack<ChairModelInfo>> allPacks() {
        return PACKS;
    }

    public static Set<ResourceLocation> getModelIdSet() {
        return INFOS.keySet();
    }

    /**
     * 按字符串形式的 model_id 取模型。容错规则与 {@link MaidModelRegistry#get(String)} 一致：
     * 缺命名空间时先试 {@code touhou_little_maid:}，再按 path 全局搜索。
     */
    public static Optional<ChairModelInfo> getInfo(@Nullable String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(modelId);
        if (id == null) {
            return getByPath(modelId);
        }
        ChairModelInfo info = INFOS.get(id);
        if (info != null) {
            return Optional.of(info);
        }
        if (!modelId.contains(":")) {
            info = INFOS.get(new ResourceLocation("touhou_little_maid", modelId));
            if (info != null) {
                return Optional.of(info);
            }
        }
        return getByPath(id.getPath());
    }

    private static Optional<ChairModelInfo> getByPath(String path) {
        for (Map.Entry<ResourceLocation, ChairModelInfo> entry : INFOS.entrySet()) {
            if (entry.getKey().getPath().equals(path)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    public static float getModelMountedYOffset(String modelId) {
        return getInfo(modelId).map(ChairModelInfo::getMountedYOffset).orElse(0.0F);
    }

    public static float getModelRenderItemScale(String modelId) {
        return getInfo(modelId).map(ChairModelInfo::getRenderItemScale).orElse(1.0F);
    }

    public static boolean getModelTameableCanRide(String modelId) {
        return getInfo(modelId).map(ChairModelInfo::isTameableCanRide).orElse(true);
    }

    public static boolean getModelNoGravity(String modelId) {
        return getInfo(modelId).map(ChairModelInfo::isNoGravity).orElse(false);
    }

    /**
     * 把每种坐垫模型都塞一份带 NBT 的物品进创造模式物品栏（对应上游
     * {@code ItemChair#fillItemCategory}）。
     */
    public static void appendCreativeItems(NonNullList<ItemStack> items, Item chairItem) {
        for (Map.Entry<ResourceLocation, ChairModelInfo> entry : INFOS.entrySet()) {
            ChairModelInfo info = entry.getValue();
            items.add(ItemChair.setData(new ItemStack(chairItem), new ItemChair.Data(
                    entry.getKey().toString(),
                    info.getMountedYOffset(),
                    info.isTameableCanRide(),
                    info.isNoGravity())));
        }
    }
}