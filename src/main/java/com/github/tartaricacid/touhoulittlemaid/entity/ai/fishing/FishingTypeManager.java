package com.github.tartaricacid.touhoulittlemaid.entity.ai.fishing;

import com.github.tartaricacid.touhoulittlemaid.api.entity.fishing.IFishingType;
import com.google.common.collect.Lists;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 钓鱼类型注册表，移植自上游 {@code entity/ai/fishing/FishingTypeManager}。
 *
 * <p>查询顺序是「先注册的特殊类型，最后回退默认类型」。上游在这里注册 Aquaculture，
 * 本移植版暂未接联动模组，因此列表为空 —— 但结构保留，将来直接 {@link #addFishingType} 即可。</p>
 */
public final class FishingTypeManager {
    private static final List<IFishingType> FISHING_TYPES = Lists.newArrayList();
    private static final DefaultFishingType DEFAULT_FISHING_TYPE = new DefaultFishingType();

    private FishingTypeManager() {
    }

    public static void addFishingType(IFishingType fishingType) {
        FISHING_TYPES.add(fishingType);
    }

    public static void init() {
        // 目前只有默认类型；联动模组的类型在各自 compat 里注册
    }

    public static IFishingType getFishingType(ItemStack itemStack) {
        for (IFishingType fishingType : FISHING_TYPES) {
            if (fishingType.isFishingRod(itemStack)) {
                return fishingType;
            }
        }
        return DEFAULT_FISHING_TYPE;
    }
}

