package com.github.tartaricacid.touhoulittlemaid.tileentity;

import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 电脑的方块实体，对应上游 {@code tileentity/TileEntityComputer}。
 *
 * <p>同上。注意电脑的座位在方块<b>顶上</b>（{@code sitPosition()} 的 y=1），
 * 而它的碰撞箱也是几个方块拼起来的（显示器 + 键盘托），见 {@code BlockComputer}。</p>
 */
public class TileEntityComputer extends TileEntityJoy {
    public TileEntityComputer(BlockPos pos, BlockState blockState) {
        super(InitBlocks.COMPUTER_TE, pos, blockState);
    }
}

