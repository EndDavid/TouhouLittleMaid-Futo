package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/**
 * 险情解除后清掉受伤记忆并回到正常日程，移植自上游 {@code MaidClearHurtTask}。
 */
public class MaidClearHurtTask extends Behavior<EntityMaid> {
    public MaidClearHurtTask() {
        super(ImmutableMap.of());
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        boolean hurtOrHostile = MaidPanicTask.isHurt(maid) || MaidPanicTask.hasHostile(maid);
        if (!MaidPanicTask.canPanic(maid) || !hurtOrHostile) {
            maid.getBrain().eraseMemory(MemoryModuleType.HURT_BY);
            maid.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
            MaidUpdateActivityFromSchedule.updateActivityFromSchedule(maid);
        }
    }
}

