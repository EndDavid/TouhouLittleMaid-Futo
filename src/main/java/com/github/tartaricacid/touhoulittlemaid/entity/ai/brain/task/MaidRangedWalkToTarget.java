package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;

import java.util.function.Function;

/**
 * 走向攻击目标的 AI，移植自上游 {@code MaidRangedWalkToTarget}。
 *
 * <p>与版本自带 {@code SetWalkTargetFromAttackTargetIfTargetOutOfReach} 的差别：
 * 这里用 {@code maid.canSee(...)}（可被任务改写射程）配合「工作范围半径的 65%」判断
 * 是否还需要靠近，因此能实现超视距打击；近战请继续用版本自带那个。</p>
 */
public class MaidRangedWalkToTarget extends Behavior<EntityMaid> {
    private final Function<LivingEntity, Float> speedModifier;

    public MaidRangedWalkToTarget(float speedModifier) {
        this(entity -> speedModifier);
    }

    public MaidRangedWalkToTarget(Function<LivingEntity, Float> speedModifier) {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.REGISTERED));
        this.speedModifier = speedModifier;
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        LivingEntity target = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (target == null) {
            return;
        }
        if (maid.canSee(target) && isWithinRestriction(maid, target)) {
            this.clearWalkTarget(maid);
        } else {
            this.setWalkAndLookTarget(maid, target);
        }
    }

    private void setWalkAndLookTarget(EntityMaid maid, LivingEntity target) {
        Brain<?> brain = maid.getBrain();
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
        WalkTarget walkTarget = new WalkTarget(new EntityTracker(target, false),
                this.speedModifier.apply(maid), 0);
        brain.setMemory(MemoryModuleType.WALK_TARGET, walkTarget);
    }

    private void clearWalkTarget(EntityMaid maid) {
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    private static boolean isWithinRestriction(EntityMaid maid, LivingEntity target) {
        float restrictRadius = maid.getRestrictRadius() * 0.65f;
        return target.distanceTo(maid) < restrictRadius;
    }
}

