package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.player.Player;

/**
 * 主人手持诱惑物（蛋糕等）时凑上去撒娇，移植自上游 {@code MaidBegTask}。
 *
 * <p>撒娇期间 {@code isBegging()} 为真，动画层会播放 {@code beg}。</p>
 */
public class MaidBegTask extends Behavior<EntityMaid> {
    private static final int BEG_DISTANCE = 6;
    private static final int CLOSED_DISTANCE = 2;

    public MaidBegTask() {
        super(ImmutableMap.of(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel worldIn, EntityMaid owner) {
        // 坐下 / 骑乘 / 睡觉 / 被拴住时不撒娇：撒娇会写入寻路目标，
        // 否则会出现「坐着跟手拿蛋糕的玩家平移」。
        if (!owner.canBrainMoving()) {
            return false;
        }
        return owner.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).map(list ->
                        list.find(owner::isOwnedBy)
                                .filter(LivingEntity::isAlive)
                                .filter(e -> e.closerThan(owner, BEG_DISTANCE))
                                .filter(e -> owner.isWithinRestriction(e.blockPosition()))
                                .anyMatch(e -> holdTemptationItem(owner, e)))
                .orElse(false);
    }

    @Override
    protected boolean canStillUse(ServerLevel worldIn, EntityMaid entityIn, long gameTimeIn) {
        return this.checkExtraStartConditions(worldIn, entityIn);
    }

    @Override
    protected boolean timedOut(long gameTime) {
        return false;
    }

    @Override
    protected void tick(ServerLevel worldIn, EntityMaid maid, long gameTime) {
        LivingEntity owner = maid.getOwner();
        if (owner instanceof Player) {
            if (!maid.closerThan(owner, CLOSED_DISTANCE)) {
                BehaviorUtils.setWalkAndLookTargetMemories(maid, owner, 0.6f, CLOSED_DISTANCE);
            } else {
                BehaviorUtils.lookAtEntity(maid, owner);
            }
            maid.setBegging(true);
        }
    }

    @Override
    protected void stop(ServerLevel worldIn, EntityMaid entityIn, long gameTimeIn) {
        entityIn.setBegging(false);
    }

    private boolean holdTemptationItem(EntityMaid owner, LivingEntity e) {
        return owner.getTemptationItem().test(e.getMainHandItem());
    }
}
