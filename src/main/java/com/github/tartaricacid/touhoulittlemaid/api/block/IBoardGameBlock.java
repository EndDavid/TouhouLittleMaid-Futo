package com.github.tartaricacid.touhoulittlemaid.api.block;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 棋类方块：女仆走到旁边之后坐下的入口。
 *
 * <p>对应上游 {@code api/block/IBoardGameBlock}。{@code MaidBoardGameTask} 只认这个接口，
 * 所以将来再加新的娱乐方块（五子棋 / 国际象棋 / 键盘）时不需要动 AI ——
 * 方块自己决定女仆该坐在哪个位置、朝向哪边。</p>
 */
public interface IBoardGameBlock {
    /**
     * 女仆尝试游玩，坐到棋盘旁边。
     *
     * @param maid  要坐下的女仆
     * @param state 被点击的方块状态（多方块棋盘的任意一格都可能传进来，实现方自己换算中心）
     * @param level 服务端世界
     * @param pos   被点击的位置
     */
    void startMaidSit(EntityMaid maid, BlockState state, Level level, BlockPos pos);
}

