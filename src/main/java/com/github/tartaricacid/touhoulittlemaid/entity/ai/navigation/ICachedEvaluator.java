package com.github.tartaricacid.touhoulittlemaid.entity.ai.navigation;

import net.minecraft.core.BlockPos;

/**
 * 「带缓存的节点评估器」标记接口，移植自上游 {@code entity/ai/navigation/ICachedEvaluator}。
 *
 * <p>本移植版尚未移植上游那套自定义 {@code MaidNavigation} / {@code MaidNodeEvaluator}，
 * 因此目前没有实现者。接口先留着，等导航层移植完成后
 * {@code MaidPathFindingBFS} 会自动通过 {@code instanceof} 判断并复用它的缓存。</p>
 */
public interface ICachedEvaluator {
    void init(int x, int y, int z, int cx, int cy, int cz);

    void markVis(BlockPos pos);
}

