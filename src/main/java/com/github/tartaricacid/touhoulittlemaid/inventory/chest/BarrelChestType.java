package com.github.tartaricacid.touhoulittlemaid.inventory.chest;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IChestType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 原版<b>木桶</b>，移植自上游 {@code inventory/chest/BarrelChestType}。
 *
 * <p>桶没有「双联」形态，也没有开箱动画，所以开启计数恒为 {@link #ALLOW_COUNT}
 * —— 上游就是这么写的：箱子要防「有人开着时往里塞」，桶不需要。</p>
 */
public class BarrelChestType implements IChestType {
    @Override
    public boolean isChest(BlockEntity chest) {
        return chest instanceof BarrelBlockEntity;
    }

    @Override
    public boolean canOpenByPlayer(BlockEntity chest, Player player) {
        if (chest instanceof BarrelBlockEntity barrelBlockEntity) {
            return barrelBlockEntity.canOpen(player);
        }
        return false;
    }

    @Override
    public int getOpenCount(BlockGetter level, BlockPos pos, BlockEntity chest) {
        return ALLOW_COUNT;
    }
}

