package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.schedule.Activity;

/**
 * 不在休息活动时强制醒来，移植自上游 {@code MaidClearSleepTask}。
 *
 * <p>避免女仆因为日程切换（例如白天到了）而卡在睡觉状态里。</p>
 */
public class MaidClearSleepTask extends Behavior<EntityMaid> {
    public MaidClearSleepTask() {
        super(ImmutableMap.of());
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid entityIn, long gameTimeIn) {
        if (entityIn.isSleeping() && !entityIn.getBrain().isActive(Activity.REST)) {
            entityIn.stopSleeping();
        }
    }
}

