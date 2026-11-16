package com.github.tartaricacid.touhoulittlemaid.util;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToIntFunction;

/**
 * 权重抽取工具，移植自上游 1.20.1 的 {@code util/WeightedPicker}。
 *
 * <p>残局数据里每条都带一个 {@code weight}，抽哪一条按权重随机。
 * 这里只保留上游用到的那个重载：上游另外两个 {@code pickDeterministic}
 * 与「带 seed」的版本在本移植版里没有任何调用者。</p>
 */
public final class WeightedPicker {
    private WeightedPicker() {
    }

    /**
     * 按权重从 {@code items} 里抽一个；空表返回 {@code null}。
     *
     * <p>权重 <= 0 的项被当作 0（跳过）；如果全部为 0，退化成均匀抽样 ——
     * 这与上游一致，避免「数据包作者漏写 weight 导致永远抽不到」。</p>
     */
    @Nullable
    public static <T> T pickRandom(@Nullable List<T> items, ToIntFunction<T> weightFunc) {
        if (items == null || items.isEmpty()) {
            return null;
        }

        int total = 0;
        for (T item : items) {
            total += Math.max(0, weightFunc.applyAsInt(item));
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (total <= 0) {
            return items.get(random.nextInt(items.size()));
        }

        int pick = random.nextInt(total);
        int accumulated = 0;
        for (T item : items) {
            accumulated += Math.max(0, weightFunc.applyAsInt(item));
            if (pick < accumulated) {
                return item;
            }
        }
        // 理论上到不了这里（累加值一定超过 total），保险返回最后一个
        return items.get(items.size() - 1);
    }
}

