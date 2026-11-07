package com.github.tartaricacid.touhoulittlemaid.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 放置位置检查（上游 {@code util/PlaceHelper} 的相关部分）。
 *
 * <p>上游这个类还带一个 {@code notSuitableForPlaceTombstone}（墓碑用）。
 * 墓碑（{@code EntityTombstone}）尚未移植，这里只保留女仆用的那一个，
 * 需要时按同样的写法补上即可。</p>
 */
public final class PlaceHelper {
    private PlaceHelper() {
    }

    /**
     * 该坐标是否<b>不</b>适合释放女仆。
     *
     * <p>女仆身高 1.5 格，所以从被点击的方块往上数两格都必须没有碰撞体积。
     * 注意入参是<b>被点击方块</b>的坐标：女仆会站在它上方，
     * 因此检查的是 {@code pos.above()} 与 {@code pos.above(2)}。</p>
     *
     * @param level 放置的世界
     * @param pos   被点击的方块坐标
     * @return {@code true} 表示放不下
     */
    public static boolean notSuitableForPlaceMaid(Level level, BlockPos pos) {
        BlockPos firstPos = pos.above();
        BlockPos secondPos = pos.above(2);
        BlockState firstBlock = level.getBlockState(firstPos);
        BlockState secondBlock = level.getBlockState(secondPos);
        return hasCollisionBoundingBox(level, firstBlock, firstPos)
                || hasCollisionBoundingBox(level, secondBlock, secondPos);
    }

    /**
     * 该方块是否有碰撞体积。
     *
     * <p>用碰撞形状是否为空来判断，而不是 {@code isAir()}：高草、火把这类
     * 无碰撞方块允许女仆站进去，而半砖、台阶虽然「可替换」也有碰撞。</p>
     */
    private static boolean hasCollisionBoundingBox(Level level, BlockState blockState, BlockPos pos) {
        return !blockState.getCollisionShape(level, pos).isEmpty();
    }
}

