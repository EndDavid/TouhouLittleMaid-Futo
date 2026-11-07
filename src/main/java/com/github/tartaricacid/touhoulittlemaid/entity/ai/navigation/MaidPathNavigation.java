package com.github.tartaricacid.touhoulittlemaid.entity.ai.navigation;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathFinder;

/**
 * 女仆专用寻路导航，移植自上游 {@code entity/ai/navigation/MaidPathNavigation}。
 *
 * <p>只做两件事：换上 {@link MaidNodeEvaluator}，并把原版默认关闭的两个开关打开 ——
 * 「能否开门」「能否穿门」。真正的「女仆配置」判断在评估器里按当前配置实时读取，
 * 因此玩家一改开关，下一次重新规划路径立刻生效，不需要重建导航对象。</p>
 *
 * <p>顺带把可可果的寻路代价设为 -1（即不可通行）：可可果是贴在原木侧面的方块，
 * 原版会把它当成可站立位置，女仆会去「站」在可可果上。上游同样处理。</p>
 */
public class MaidPathNavigation extends GroundPathNavigation {
    public MaidPathNavigation(Mob mob, Level level) {
        super(mob, level);
        this.mob.setPathfindingMalus(BlockPathTypes.COCOA, -1.0F);
    }

    @Override
    protected PathFinder createPathFinder(int range) {
        this.nodeEvaluator = new MaidNodeEvaluator();
        this.nodeEvaluator.setCanOpenDoors(true);
        this.nodeEvaluator.setCanPassDoors(true);
        this.nodeEvaluator.setCanFloat(true);
        return new PathFinder(this.nodeEvaluator, range);
    }
}

