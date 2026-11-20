package com.github.tartaricacid.touhoulittlemaid.tileentity;

import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 书架的方块实体，对应上游 {@code tileentity/TileEntityBookshelf}。
 *
 * <p>与 {@link TileEntityKeyboard} 完全同构：只把方块实体类型换掉。
 * 三者唯一的区别在方块那一侧（碰撞箱、坐姿、好感度类型）。</p>
 */
public class TileEntityBookshelf extends TileEntityJoy {
    public TileEntityBookshelf(BlockPos pos, BlockState blockState) {
        super(InitBlocks.BOOKSHELF_TE, pos, blockState);
    }
}

