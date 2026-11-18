package com.github.tartaricacid.touhoulittlemaid.inventory.chest;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IChestType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * 原版<b>箱子</b>（含大箱子），移植自上游 {@code inventory/chest/VanillaChestType}。
 *
 * <p>只认 {@link ChestBlockEntity}：潜影盒、漏斗、熔炉虽然也是 {@code Container}，
 * 但上游不把它们当作隙间的目标，这里保持同样的范围。</p>
 */
public class VanillaChestType implements IChestType {
    @Override
    public boolean isChest(BlockEntity chest) {
        return chest instanceof ChestBlockEntity;
    }

    @Override
    public boolean canOpenByPlayer(BlockEntity chest, Player player) {
        if (chest instanceof ChestBlockEntity chestBlockEntity) {
            // 基类 BaseContainerBlockEntity#canOpen 会检查上锁（LockCode）
            return chestBlockEntity.canOpen(player);
        }
        return false;
    }

    @Override
    public int getOpenCount(BlockGetter level, BlockPos pos, BlockEntity chest) {
        if (chest instanceof ChestBlockEntity) {
            // 原版自己的计数：有玩家开着界面时它 != 0
            return ChestBlockEntity.getOpenCount(level, pos);
        }
        return DENY_COUNT;
    }

    /**
     * 大箱子要在这里拼起来 —— 见 {@link IChestType#getContainer} 的说明。
     *
     * <p>最后一个参数 {@code allowBlocked = true}：即使箱子上面压着方块、
     * 正常右键打不开，隙间也应当能读到它的内容（Forge 侧的箱子 Capability 同样传 {@code true}）。</p>
     */
    @Nullable
    @Override
    public Container getContainer(Level level, BlockPos pos, BlockEntity chest) {
        if (!(chest instanceof ChestBlockEntity)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chestBlock) {
            return ChestBlock.getContainer(chestBlock, state, level, pos, true);
        }
        return null;
    }
}

