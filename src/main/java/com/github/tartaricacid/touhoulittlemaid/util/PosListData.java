package com.github.tartaricacid.touhoulittlemaid.util;

import com.google.common.collect.Lists;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;

import java.util.List;

/**
 * 一组方块坐标的 NBT 序列化容器，逐字移植自上游 {@code util/PosListData}。
 *
 * <p>祭坛多方块把这个结构存进每个方块实体：{@code blockPosList} 是「整座祭坛占用的全部坐标」，
 * {@code canPlaceItemPosList} 是「顶部四根原木（能放贡品的 4 个位置）」。
 * 拆掉任意一个祭坛方块时，靠前者把其余方块还原成建坛之前的方块。</p>
 */
public final class PosListData {
    private final List<BlockPos> data = Lists.newArrayList();

    public ListTag serialize() {
        ListTag nbt = new ListTag();
        for (BlockPos pos : data) {
            nbt.add(NbtUtils.writeBlockPos(pos));
        }
        return nbt;
    }

    public void deserialize(ListTag nbt) {
        data.clear();
        for (int i = 0; i < nbt.size(); i++) {
            data.add(NbtUtils.readBlockPos(nbt.getCompound(i)));
        }
    }

    public List<BlockPos> getData() {
        return data;
    }

    public void add(BlockPos pos) {
        this.data.add(pos);
    }
}

