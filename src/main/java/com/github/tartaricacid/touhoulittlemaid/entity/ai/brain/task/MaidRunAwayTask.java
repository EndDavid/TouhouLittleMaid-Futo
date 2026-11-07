package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;

import java.util.function.Function;

/**
 * 逃离某个记忆中的目标，移植自上游 {@code MaidRunAwayTask}。
 *
 * <p>在原来的「远离」行为之前加了一步：先站起来、先下车，否则坐着的女仆会原地不动。</p>
 *
 * <p>注意父类是<b>本模组自己的</b> {@link SetWalkTargetAwayFrom}（上游同名类），
 * 不是 vanilla 的那个 —— vanilla 版本多一个「期望距离」参数且不按工作范围取点，
 * 直接导致逃跑行为不触发。</p>
 */
public class MaidRunAwayTask<T> extends SetWalkTargetAwayFrom<T> {
    public MaidRunAwayTask(MemoryModuleType<T> walkAwayFromMemory, float speedModifier, boolean ignoreOtherWalkTarget, Function<T, Vec3> toPosition) {
        super(walkAwayFromMemory, speedModifier, ignoreOtherWalkTarget, toPosition);
    }

    public static MaidRunAwayTask<BlockPos> pos(MemoryModuleType<BlockPos> walkAwayFromMemory, float speedModifier, boolean ignoreOtherWalkTarget) {
        return new MaidRunAwayTask<>(walkAwayFromMemory, speedModifier, ignoreOtherWalkTarget, Vec3::atBottomCenterOf);
    }

    public static MaidRunAwayTask<? extends Entity> entity(MemoryModuleType<? extends Entity> walkAwayFromMemory, float speedModifier, boolean ignoreOtherWalkTarget) {
        return new MaidRunAwayTask<>(walkAwayFromMemory, speedModifier, ignoreOtherWalkTarget, Entity::position);
    }

    @Override
    protected void start(ServerLevel worldIn, PathfinderMob entityIn, long gameTimeIn) {
        if (entityIn instanceof EntityMaid maid) {
            if (maid.isMaidInSittingPose()) {
                maid.setInSittingPose(false);
            }
            if (maid.isPassenger() && !maid.isCarriedByOwner()) {
                maid.stopRiding();
            }
        }
        super.start(worldIn, entityIn, gameTimeIn);
    }
}
