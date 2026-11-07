package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.function.Function;

/**
 * 「远离某个记忆目标」的行为，移植自上游同名类。
 *
 * <p><b>为什么不能用 vanilla 的 {@code SetWalkTargetAwayFrom}：</b>vanilla 那个类需要
 * 额外的「期望距离」参数，且内部使用 {@code LandRandomPos.getPosAway} 的固定垂直范围；
 * 上游为了配合女仆的「工作范围」（{@code getRestrictRadius()}）重写了一份，
 * 语义差别直接导致女仆不会逃跑。因此这里完整保留上游实现。</p>
 */
public class SetWalkTargetAwayFrom<T> extends Behavior<PathfinderMob> {
    private final MemoryModuleType<T> walkAwayFromMemory;
    private final float speedModifier;
    private final Function<T, Vec3> toPosition;

    public SetWalkTargetAwayFrom(MemoryModuleType<T> walkAwayFromMemory, float speedModifier,
                                 boolean ignoreOtherWalkTarget, Function<T, Vec3> toPosition) {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, ignoreOtherWalkTarget ? MemoryStatus.REGISTERED : MemoryStatus.VALUE_ABSENT,
                walkAwayFromMemory, MemoryStatus.VALUE_PRESENT));
        this.walkAwayFromMemory = walkAwayFromMemory;
        this.speedModifier = speedModifier;
        this.toPosition = toPosition;
    }

    public static SetWalkTargetAwayFrom<BlockPos> pos(MemoryModuleType<BlockPos> memory, float speedModifier,
                                                      boolean ignoreOtherWalkTarget) {
        return new SetWalkTargetAwayFrom<>(memory, speedModifier, ignoreOtherWalkTarget, Vec3::atBottomCenterOf);
    }

    public static SetWalkTargetAwayFrom<? extends Entity> entity(MemoryModuleType<? extends Entity> memory,
                                                                 float speedModifier, boolean ignoreOtherWalkTarget) {
        return new SetWalkTargetAwayFrom<>(memory, speedModifier, ignoreOtherWalkTarget, Entity::position);
    }

    private static void moveAwayFrom(PathfinderMob mob, Vec3 avoidPos, float speed) {
        int radius = (int) mob.getRestrictRadius();
        for (int i = 0; i < 10; ++i) {
            Vec3 pos = LandRandomPos.getPosAway(mob, radius, 7, avoidPos);
            if (pos != null) {
                mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, speed, 0));
                return;
            }
        }
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, PathfinderMob owner) {
        int radius = (int) owner.getRestrictRadius();
        return !this.alreadyWalkingAwayFromPosWithSameSpeed(owner)
                && owner.position().closerThan(this.getPosToAvoid(owner), radius);
    }

    private Vec3 getPosToAvoid(PathfinderMob mob) {
        return this.toPosition.apply(mob.getBrain().getMemory(this.walkAwayFromMemory).orElseThrow());
    }

    private boolean alreadyWalkingAwayFromPosWithSameSpeed(PathfinderMob mob) {
        if (!mob.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            return false;
        }
        WalkTarget walkTarget = mob.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
        if (walkTarget.getSpeedModifier() != this.speedModifier) {
            return false;
        }
        Vec3 current = walkTarget.getTarget().currentPosition().subtract(mob.position());
        Vec3 avoid = this.getPosToAvoid(mob).subtract(mob.position());
        return current.dot(avoid) < 0.0D;
    }

    @Override
    protected void start(ServerLevel level, PathfinderMob entity, long gameTime) {
        moveAwayFrom(entity, this.getPosToAvoid(entity), this.speedModifier);
    }
}

