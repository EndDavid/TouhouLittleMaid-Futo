package com.github.tartaricacid.touhoulittlemaid.block.multiblock;

import com.github.tartaricacid.touhoulittlemaid.api.block.IMultiBlock;
import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * 多方块注册表，移植自上游 {@code block/multiblock/MultiBlockManager}。
 *
 * <p>目前只有祭坛。上游还会遍历 {@code TouhouLittleMaid.EXTENSIONS} 让附属模组
 * 追加多方块 —— 本移植版没有那套扩展 API，需要时往 {@link #MULTI_BLOCK_LIST}
 * 里加即可。</p>
 *
 * <p>用静态初始化块而不是 {@code init()} 里的赋值：{@code ItemHakureiGohei#useOn}
 * 在<b>第一次右键时</b>才读这张表，早于它就不可能为 null；而静态块在任何时刻都成立，
 * 少一个「必须先调 init」的隐性约束。</p>
 */
public final class MultiBlockManager {
    private static final List<IMultiBlock> MULTI_BLOCK_LIST =
            ImmutableList.of(new MultiBlockAltar());

    private MultiBlockManager() {
    }

    public static List<IMultiBlock> getMultiBlockList() {
        return MULTI_BLOCK_LIST;
    }
}

