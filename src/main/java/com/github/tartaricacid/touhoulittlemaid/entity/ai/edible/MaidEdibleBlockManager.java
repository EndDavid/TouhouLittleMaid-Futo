package com.github.tartaricacid.touhoulittlemaid.entity.ai.edible;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.block.IMaidEdibleBlock;
import com.github.tartaricacid.touhoulittlemaid.compat.farmersdelight.FarmersDelightCompat;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

/**
 * 「什么算方块类食物」的登记表，移植自上游 {@code entity/ai/edible/MaidEdibleBlockManager}。
 *
 * <h2>与上游的差异</h2>
 * <ul>
 *     <li>上游还会遍历 {@code ILittleMaid.EXTENSIONS} 让附属模组注册，本移植版没有扩展体系，
 *         所以只有内置的那份（{@link CakeEdible}）与各 compat 贡献的；
 *         将来要做扩展，在 {@link #init()} 末尾加一个循环即可。</li>
 *     <li>上游还注册了森罗物语 / jmc 两份，那两个模组在 Fabric 1.18.2 上没有对应版本，
 *         所以只留农夫乐事（见 {@link FarmersDelightCompat}）。</li>
 * </ul>
 *
 * <p>列表在 {@link #init()} 之后是不可变的 —— 上游同样用 {@code ImmutableList} 冻结，
 * 免得有人在内层循环里顺手 add 导致 {@code ConcurrentModificationException}。</p>
 */
public final class MaidEdibleBlockManager {
    /**
     * 初始为可变列表，{@link #init()} 结束时冻结。
     */
    private static List<IMaidEdibleBlock> edibleBlocks = new ArrayList<>();

    private MaidEdibleBlockManager() {
    }

    public static void init() {
        add(new CakeEdible());
        FarmersDelightCompat.addFarmersDelightEdible();

        for (IMaidEdibleBlock block : edibleBlocks) {
            TouhouLittleMaid.LOGGER.debug("[Futo] edible block registered: {}",
                    block.getClass().getSimpleName());
        }
        edibleBlocks = ImmutableList.copyOf(edibleBlocks);
    }

        /**
     * 往登记表里加一份实现。<b>只允许在 {@link #init()} 期间调用</b> ——
     * 那之后列表已经被冻结，再调用会抛 {@code UnsupportedOperationException}。
     */
    public static void add(IMaidEdibleBlock edibleBlock) {
        edibleBlocks.add(edibleBlock);
    }

public static List<IMaidEdibleBlock> getEdibleBlocks() {
        return edibleBlocks;
    }
}

