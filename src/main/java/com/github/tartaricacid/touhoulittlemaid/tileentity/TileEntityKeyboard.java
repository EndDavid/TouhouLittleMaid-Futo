package com.github.tartaricacid.touhoulittlemaid.tileentity;

import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 键盘的方块实体，对应上游 {@code tileentity/TileEntityKeyboard}。
 *
 * <p>它一行逻辑都没有：所有「谁坐在这儿」都由基类 {@link TileEntityJoy} 记住。
 * 之所以还要单独立一个类，是因为方块实体类型是按类区分的 ——
 * 键盘 / 书架 / 电脑的掉落物与渲染器各不相同，
 * 共用一个类型的话 {@code BlockEntityRenderer} 就没法知道该画哪个模型。</p>
 *
 * <p><b>与上游的差异：</b>上游把 {@code TYPE} 写成类里的静态字段，并在构造器里引用它
 * （{@code super(TYPE, ...)}）。这里改成引用 {@code InitBlocks.KEYBOARD_TE}，
 * 与五子棋 / 祭坛 / 神社保持一致。两者在加载顺序上是等价的：字段初始化先于任何实体构造。</p>
 */
public class TileEntityKeyboard extends TileEntityJoy {
    public TileEntityKeyboard(BlockPos pos, BlockState blockState) {
        super(InitBlocks.KEYBOARD_TE, pos, blockState);
    }
}
