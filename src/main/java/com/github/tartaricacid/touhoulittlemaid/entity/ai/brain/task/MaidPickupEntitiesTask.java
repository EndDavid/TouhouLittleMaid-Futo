package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

import java.util.List;
import java.util.function.Predicate;

/**
 * 捡起范围内的掉落物 / 可拾取实体，移植自上游 {@code MaidPickupEntitiesTask}。
 *
 * <p>「可拾取」的判定由 {@code EntityMaid#canPickup} 负责（主人 UUID、归属者、拾取开关等），
 * 这里只负责挑一个够得着、不在水里、在范围内的目标走过去。</p>
 */
public class MaidPickupEntitiesTask extends Behavior<EntityMaid> {
    private final Predicate<EntityMaid> predicate;
    private final float speedModifier;

    public MaidPickupEntitiesTask(float speedModifier) {
        this(maid -> true, speedModifier);
    }

    public MaidPickupEntitiesTask(Predicate<EntityMaid> predicate, float speedModifier) {
        super(ImmutableMap.of(InitEntities.VISIBLE_PICKUP_ENTITIES, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
        this.predicate = predicate;
        this.speedModifier = speedModifier;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel worldIn, EntityMaid owner) {
        return owner.isTame() && owner.canBrainMoving() && this.predicate.test(owner);
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        this.getItems(maid).stream()
                .filter(e -> maid.isWithinRestriction(e.blockPosition()) && e.isAlive() && !e.isInWater())
                .filter(maid::canPathReach).findFirst()
                .ifPresent(e -> BehaviorUtils.setWalkAndLookTargetMemories(maid, e, this.speedModifier, 0));
    }

    private List<Entity> getItems(EntityMaid maid) {
        return maid.getBrain().getMemory(InitEntities.VISIBLE_PICKUP_ENTITIES).orElse(Lists.newArrayList());
    }
}

