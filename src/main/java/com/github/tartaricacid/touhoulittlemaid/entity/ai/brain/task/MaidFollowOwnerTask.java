package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import org.jetbrains.annotations.Nullable;

/**
 * 跟随主人，移植自上游 {@code MaidFollowOwnerTask}。
 *
 * <p>与上游一致：距离超过「工作范围 - 2」才起身跟随，超过「工作范围 + 2」直接传送；
 * 居家模式或正在坐下 / 骑乘时不跟随。</p>
 *
 * <p>与上游的差异：上游会先判断 {@code maid.getSwimManager().isGoingToBreath()}
 * （换气中不跟随）；游泳管理器尚未移植，因此该判断暂缺。</p>
 */
public class MaidFollowOwnerTask extends Behavior<EntityMaid> {
    private final float speedModifier;
    private final int stopDistance;

    public MaidFollowOwnerTask(float speedModifier, int stopDistance) {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED));
        this.speedModifier = speedModifier;
        this.stopDistance = stopDistance;
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        LivingEntity owner = maid.getOwner();
        int startDistance = (int) maid.getRestrictRadius() - 2;
        int minTeleportDistance = startDistance + 4;
        if (ownerStateConditions(owner) && maidStateConditions(maid) && !maid.closerThan(owner, startDistance)) {
            if (!maid.closerThan(owner, minTeleportDistance)) {
                maid.teleportToOwner(owner);
            } else if (!ownerIsWalkTarget(maid, owner)) {
                BehaviorUtils.setWalkAndLookTargetMemories(maid, owner, speedModifier, stopDistance);
            }
        }
    }

    private boolean maidStateConditions(EntityMaid maid) {
        return !maid.isHomeModeEnable() && maid.canBrainMoving();
    }

    private boolean ownerStateConditions(@Nullable LivingEntity owner) {
        return owner != null && !owner.isSpectator() && !owner.isDeadOrDying();
    }

    private boolean ownerIsWalkTarget(EntityMaid maid, LivingEntity owner) {
        return maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(target -> {
            if (target.getTarget() instanceof EntityTracker tracker) {
                return tracker.getEntity().equals(owner);
            }
            return false;
        }).orElse(false);
    }
}

