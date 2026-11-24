package com.github.tartaricacid.touhoulittlemaid.inventory.chest;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IChestType;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntitySnackCabinet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 把零食柜登记成「隙间能认的箱子」，移植自上游 1.20.1 的
 * {@code inventory/chest/SnackCabinetType}。
 *
 * <p>它只有三行实质内容：认类型、检查玩家能否打开、返回开启计数。
 * 物品栏本身不需要覆写 {@link IChestType#getContainer} —— 默认实现就是
 * 「方块实体自己是 {@code Container} 就交出来」，而 {@link TileEntitySnackCabinet}
 * 继承的 {@code RandomizableContainerBlockEntity} 正是 {@code Container}。</p>
 *
 * <p>{@code getOpenCount} 恒返回 {@code 0}：零食柜不是原版箱子，没有
 * {@code ChestBlockEntity.getOpenCount} 那套静态计数；而它的开合状态只影响音效，
 * 不影响隙间能否往里塞东西。上游同样返回 0。</p>
 */
public class SnackCabinetType implements IChestType {
    @Override
    public boolean isChest(BlockEntity chest) {
        return chest instanceof TileEntitySnackCabinet;
    }

    @Override
    public boolean canOpenByPlayer(BlockEntity chest, Player player) {
        // 没有锁的容器：能站到它面前就说明能打开（与上游一致）
        return chest instanceof TileEntitySnackCabinet;
    }

    @Override
    public int getOpenCount(BlockGetter level, BlockPos pos, BlockEntity chest) {
        return ALLOW_COUNT;
    }
}

