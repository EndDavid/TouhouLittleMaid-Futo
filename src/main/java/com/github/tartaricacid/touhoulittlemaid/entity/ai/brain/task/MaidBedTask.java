package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.init.InitPoi;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * 找床睡觉，逐字移植自上游 {@code entity/ai/brain/task/MaidBedTask}。
 *
 * <h2>两段式流程</h2>
 * <ol>
 *     <li>第一次触发：在「搜索中心」半径内找最近的空床，太远就走过去并
 *         {@code setNextCheckTickCount(5)}（走得近一点再重算）；</li>
 *     <li>第二次触发：距离已进入 {@code closeEnoughDist}，把坐标写进
 *         {@code TARGET_POS} 并返回 true —— {@link #start} 里真正 {@code startSleeping}。</li>
 * </ol>
 *
 * <p>搜索中心用的是 {@code getBrainSearchPos()}：开了居家模式就是居家点，否则是自己脚下。</p>
 */
public class MaidBedTask extends MaidCheckRateTask {
    private final int closeEnoughDist;
    private final float speed;

    public MaidBedTask(float movementSpeed, int closeEnoughDist) {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                InitEntities.TARGET_POS, MemoryStatus.VALUE_ABSENT));
        this.closeEnoughDist = closeEnoughDist;
        this.speed = movementSpeed;
        this.setMaxCheckRate(10);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel worldIn, EntityMaid maid) {
        if (super.checkExtraStartConditions(worldIn, maid) && maid.canBrainMoving()) {
            BlockPos bedPos = findBed(worldIn, maid);
            if (bedPos != null && maid.isWithinRestriction(bedPos)) {
                if (bedPos.distToCenterSqr(maid.position()) < Math.pow(this.closeEnoughDist, 2)) {
                    maid.getBrain().setMemory(InitEntities.TARGET_POS, new BlockPosTracker(bedPos));
                    return true;
                }
                BehaviorUtils.setWalkAndLookTargetMemories(maid, bedPos, speed, 1);
                this.setNextCheckTickCount(5);
            } else {
                maid.getBrain().eraseMemory(InitEntities.TARGET_POS);
            }
        }
        return false;
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        maid.getBrain().getMemory(InitEntities.TARGET_POS).ifPresent(targetPos -> {
            BlockPos pos = targetPos.currentBlockPosition();
            BlockState blockState = worldIn.getBlockState(pos);
            // 再确认一次：这一 tick 里床可能已经被别人占了 / 被拆了
            if (blockState.is(InitBlocks.MAID_BED)
                    && blockState.getValue(BlockMaidBed.PART) == BedPart.HEAD
                    && !blockState.getValue(BlockMaidBed.OCCUPIED)) {
                maid.startSleeping(pos);
                maid.setPos(pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5);
            }
        });
        maid.getBrain().eraseMemory(InitEntities.TARGET_POS);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    @Nullable
    private BlockPos findBed(ServerLevel world, EntityMaid maid) {
        BlockPos blockPos = maid.getBrainSearchPos();
        PoiManager poiManager = world.getPoiManager();
        int range = (int) maid.getRestrictRadius();
        return poiManager.getInRange(type -> type.equals(InitPoi.MAID_BED), blockPos, range, PoiManager.Occupancy.ANY)
                .map(PoiRecord::getPos)
                .min(Comparator.comparingDouble(pos -> pos.distSqr(maid.blockPosition())))
                .orElse(null);
    }
}
