package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.block.BlockPicnicMat;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.init.InitPoi;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityPicnicMat;
import com.google.common.collect.ImmutableMap;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.UUID;

/**
 * 「去野餐垫上坐着」—— 找一张还有空位的垫子并坐上去，移植自上游
 * {@code entity/ai/brain/task/MaidFindHomeMealTask}。
 *
 * <p>它挂在 <b>IDLE</b>（闲置）活动里、权重 6（比 {@code MaidBegTask} 的 5 大、
 * 比 {@code MaidJoyTask} 的 7 小）。与 {@code MaidJoyTask} 的分工：</p>
 * <ul>
 *     <li>这个任务只负责「走过去坐下」，坐下之后吃不吃由 {@link MaidHomeMealTask} 决定；</li>
 *     <li>所以即使垫子上一点食物都没有，女仆也会去坐着 —— 上游就是这样
 *         （空垫子只会让她在坐下后弹一句「没什么可吃的」）。</li>
 * </ul>
 *
 * <h2>与上游的差异</h2>
 * <ol>
 *     <li>上游用 {@code maid.getBrainSearchPos()}、{@code getRestrictRadius()} 这两个助手方法，
 *         本移植版同名方法都存在，直接照抄。</li>
 *     <li>上游 {@code start} 里还会清掉 {@code TARGET_POS} 记忆 —— 1.18.2 的
 *         {@code Brain} 没有 1.19 之后那种「记忆过期」自动清理，这里保持显式 {@code eraseMemory}。</li>
 * </ol>
 */
public class MaidFindHomeMealTask extends MaidCheckRateTask {
    private final int closeEnoughDist;
    private final float speed;

    public MaidFindHomeMealTask(float movementSpeed, int closeEnoughDist) {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                InitEntities.TARGET_POS, MemoryStatus.VALUE_ABSENT));
        this.closeEnoughDist = closeEnoughDist;
        this.speed = movementSpeed;
        this.setMaxCheckRate(10);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel worldIn, EntityMaid maid) {
        if (super.checkExtraStartConditions(worldIn, maid) && maid.canBrainMoving()) {
            BlockPos picnicMatPos = this.findPicnicMat(worldIn, maid);
            if (picnicMatPos != null && maid.isWithinRestriction(picnicMatPos)) {
                // 已经够近就直接坐下（上游这里写的是三次方，照抄；
                // 指数不同只会让「多近算够近」比字面值更宽松，不影响正确性）
                if (picnicMatPos.distToCenterSqr(maid.position()) < Math.pow(this.closeEnoughDist, 3)) {
                    maid.getBrain().setMemory(InitEntities.TARGET_POS, new BlockPosTracker(picnicMatPos));
                    return true;
                }
                BehaviorUtils.setWalkAndLookTargetMemories(maid, picnicMatPos, this.speed, 2);
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
            if (blockState.getBlock() instanceof BlockPicnicMat picnicMat) {
                picnicMat.startMaidSit(maid, blockState, worldIn, pos);
            }
        });
        maid.getBrain().eraseMemory(InitEntities.TARGET_POS);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    @Nullable
    private BlockPos findPicnicMat(ServerLevel world, EntityMaid maid) {
        BlockPos blockPos = maid.getBrainSearchPos();
        PoiManager poiManager = world.getPoiManager();
        int range = (int) maid.getRestrictRadius();
        return poiManager.getInRange(type -> type.equals(InitPoi.HOME_MEAL), blockPos, range, PoiManager.Occupancy.ANY)
                .map(PoiRecord::getPos).filter(pos -> !this.isOccupied(world, pos))
                .min(Comparator.comparingDouble(pos -> pos.distSqr(maid.blockPosition()))).orElse(null);
    }

    /**
     * 四张座位都真的被活着的实体占着才算满。
     *
     * <p>与 {@code MaidBoardGameTask#isOccupied} 的判据相反：那边查不到方块实体就当「占着」，
     * 这边反过来 —— 只有 <b>UUID 有效且实体还在</b> 才算占用。
     * 因为座位实体可能被 {@code /kill} 或者区块卸载后再也不回来，
     * 那种情况下 NBT 里那串 UUID 只是垃圾数据，不该让整张垫子永久失效。</p>
     */
    private boolean isOccupied(ServerLevel worldIn, BlockPos pos) {
        BlockEntity te = worldIn.getBlockEntity(pos);
        if (te instanceof TileEntityPicnicMat picnicMat) {
            for (UUID uuid : picnicMat.getSitIds()) {
                if (uuid.equals(Util.NIL_UUID)) {
                    return false;
                }
                if (worldIn.getEntity(uuid) == null) {
                    return false;
                }
            }
        }
        return true;
    }
}
