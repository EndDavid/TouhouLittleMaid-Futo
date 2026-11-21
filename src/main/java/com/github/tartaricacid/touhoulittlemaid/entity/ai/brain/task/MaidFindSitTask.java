package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.vehicle.Boat;

import java.util.Comparator;
import java.util.Optional;

/**
 * 找一个可以坐下的对象并骑上去，移植自上游 {@code MaidFindSitTask}。
 *
 * <p><b>与上游的差异（这是 bug 修复，不是取舍）：</b>上游是用
 * {@code NEAREST_VISIBLE_LIVING_ENTITIES} 这个记忆去筛 {@code EntityChair} / {@code Boat}，
 * 但这两个类都<b>不是</b> {@code LivingEntity}，那个记忆里永远不可能出现它们 ——
 * 于是「女仆对水边的船毫无反应」。这里改成直接按类型在附近查询实体，并按距离取最近的。</p>
 *
 * <p>上游同时接受「女仆坐垫」与「船」，这里两者都认。</p>
 */
public class MaidFindSitTask extends MaidCheckRateTask {
    private static final int MAX_DELAY_TIME = 12;
    /**
     * 搜索可坐对象的半径（格）。
     */
    private static final double SIT_SEARCH_RADIUS = 16.0;

    private final float speedModifier;
    private Entity sitEntity = null;

    public MaidFindSitTask(float speedModifier) {
        super(ImmutableMap.of(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.REGISTERED,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
        this.speedModifier = speedModifier;
        this.setMaxCheckRate(MAX_DELAY_TIME);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel worldIn, EntityMaid owner) {
        return super.checkExtraStartConditions(worldIn, owner) && owner.getVehicle() == null;
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        this.sitEntity = null;
        Optional<Entity> found = this.findSitEntity(maid);
        if (found.isEmpty()) {
            // 诊断：找不到坐具时是完全静默的，玩家只会看到「女仆站在水边不动」。
            // 这里只在「状态变化」时打一条，避免这个行为每 60 tick 刷屏。
            MaidFishingDiagnostics.reportSit(maid, null, "no boat or cushion within " + SIT_SEARCH_RADIUS);
        }
        found.ifPresent(entity -> {
            this.sitEntity = entity;
            BehaviorUtils.setWalkAndLookTargetMemories(maid, this.sitEntity, this.speedModifier, 0);
        });

        if (this.sitEntity != null && this.sitEntity.isAlive() && this.sitEntity.closerThan(maid, 2)) {
            if (this.sitEntity.getPassengers().isEmpty()) {
                boolean riding = maid.startRiding(this.sitEntity, true);
                // 诊断：走到坐具旁边却骑不上（例如 showVehicle 为 false、或被别的实体占位）
                // 会表现为「她就站在船边上站着」。startRiding 的返回值是唯一的判据。
                MaidFishingDiagnostics.reportSit(maid, this.sitEntity, riding ? "mounted" : "startRiding refused");
            } else {
                MaidFishingDiagnostics.reportSit(maid, this.sitEntity, "seat already occupied");
            }
            this.sitEntity = null;
        }
    }

    private Optional<Entity> findSitEntity(EntityMaid maid) {
        return maid.level.getEntitiesOfClass(Entity.class,
                        maid.getBoundingBox().inflate(SIT_SEARCH_RADIUS),
                        e -> this.filterEntity(maid, e))
                .stream()
                .min(Comparator.comparingDouble(maid::distanceToSqr));
    }

    private boolean filterEntity(EntityMaid maid, Entity entity) {
        if (!entity.isAlive()) {
            return false;
        }
        if (!maid.isWithinRestriction(entity.blockPosition())) {
            return false;
        }
        if (!entity.getPassengers().isEmpty()) {
            return false;
        }
        return entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair
                || entity instanceof Boat;
    }
}