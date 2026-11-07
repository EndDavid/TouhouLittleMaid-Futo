package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 随机漫步，移植自上游 {@code MaidRandomStroll}。
 *
 * <p>比原版 {@code RandomStroll} 多两点：带检查间隔（{@link MaidCheckRateTask}），
 * 以及「目标是工作点」时不生效（避免覆盖寻路目标）。</p>
 */
public class MaidRandomStroll extends MaidCheckRateTask {
    protected final int maxHorizontalDistance;
    protected final int maxVerticalDistance;
    private final float speedModifier;
    private final boolean mayStrollFromWater;

    public MaidRandomStroll(float speedModifier, int maxHorizontalDistance, int maxVerticalDistance) {
        this(speedModifier, maxHorizontalDistance, maxVerticalDistance, true);
    }

    public MaidRandomStroll(float speedModifier, int maxHorizontalDistance, int maxVerticalDistance, boolean mayStrollFromWater) {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                com.github.tartaricacid.touhoulittlemaid.init.InitEntities.TARGET_POS, MemoryStatus.VALUE_ABSENT));
        this.speedModifier = speedModifier;
        this.maxHorizontalDistance = maxHorizontalDistance;
        this.maxVerticalDistance = maxVerticalDistance;
        this.mayStrollFromWater = mayStrollFromWater;
        this.setMaxCheckRate(50);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel worldIn, EntityMaid maid) {
        if (super.checkExtraStartConditions(worldIn, maid)) {
            return this.mayStrollFromWater || !maid.isInWaterOrBubble();
        }
        return false;
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        Optional<Vec3> optional = Optional.ofNullable(this.getTargetPos(maid));
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                optional.map(pos -> new WalkTarget(pos, this.speedModifier, 0)));
    }

    @Nullable
    protected Vec3 getTargetPos(EntityMaid maid) {
        return LandRandomPos.getPos(maid, this.maxHorizontalDistance, this.maxVerticalDistance);
    }
}

