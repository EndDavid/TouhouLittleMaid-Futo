package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.api.block.IBoardGameBlock;
import com.github.tartaricacid.touhoulittlemaid.api.block.IBoardGameEntityBlock;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.init.InitPoi;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * 「去下棋」行为，移植自上游 {@code MaidBoardGameTask}。
 *
 * <p>逻辑很短：在活动范围里找一张<b>没人坐</b>的棋盘 → 没走近就走过去 → 走到了就坐下。</p>
 *
 * <p><b>「没人坐」怎么判断：</b>{@link #isOccupied} 反查棋盘记录的 {@code SitId} 是否对应一个活着的实体。
 * 注意它在查不到方块实体时返回 {@code true}（= 当作已占用），这是故意的：
 * 3×3 棋盘的八格边角也注册成了兴趣点，而它们没有方块实体 —— 如果返回 false，
 * 女仆会挑中一个边角、走过去、然后坐下失败，永远坐不到棋盘上。</p>
 *
 * <p>{@link #closeEnoughDist} 是「多近才算走到」。上游给的是 2 格，正好是
 * {@code BlockCChess#startMaidSit} 里女仆落坐的偏移距离 —— 改这个数要连带看那处。</p>
 */
public class MaidBoardGameTask extends MaidCheckRateTask {
    private final int closeEnoughDist;
    private final float speed;

    public MaidBoardGameTask(float movementSpeed, int closeEnoughDist) {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                InitEntities.TARGET_POS, MemoryStatus.VALUE_ABSENT));
        this.closeEnoughDist = closeEnoughDist;
        this.speed = movementSpeed;
        this.setMaxCheckRate(10);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel worldIn, EntityMaid maid) {
        if (super.checkExtraStartConditions(worldIn, maid) && maid.canBrainMoving()) {
            BlockPos gamePos = findGameBlock(worldIn, maid);
            if (gamePos != null && maid.isWithinRestriction(gamePos)) {
                if (gamePos.distToCenterSqr(maid.position()) < Math.pow(this.closeEnoughDist, 2)) {
                    maid.getBrain().setMemory(InitEntities.TARGET_POS, new BlockPosTracker(gamePos));
                    return true;
                }
                BehaviorUtils.setWalkAndLookTargetMemories(maid, gamePos, speed, 1);
                this.setNextCheckTickCount(5);
            } else {
                maid.getBrain().eraseMemory(InitEntities.TARGET_POS);
            }
        }
        return false;
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        maid.getBrain().getMemory(InitEntities.TARGET_POS).ifPresent((targetPos) -> {
            BlockPos pos = targetPos.currentBlockPosition();
            BlockState blockState = worldIn.getBlockState(pos);
            if (blockState.getBlock() instanceof IBoardGameBlock gameBlock) {
                gameBlock.startMaidSit(maid, blockState, worldIn, pos);
                if (maid.getOwner() instanceof ServerPlayer serverPlayer) {
                    InitTrigger.MAID_EVENT.trigger(serverPlayer, TriggerType.MAID_SIT_JOY);
                }
            }
        });
        maid.getBrain().eraseMemory(InitEntities.TARGET_POS);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    @Nullable
    private BlockPos findGameBlock(ServerLevel world, EntityMaid maid) {
        BlockPos blockPos = maid.getBrainSearchPos();
        PoiManager poiManager = world.getPoiManager();
        int range = (int) maid.getRestrictRadius();
        return poiManager.getInRange(type -> type.equals(InitPoi.JOY_BLOCK), blockPos, range, PoiManager.Occupancy.ANY)
                .map(PoiRecord::getPos).filter(pos -> !isOccupied(world, pos))
                .min(Comparator.comparingDouble(pos -> pos.distSqr(maid.blockPosition()))).orElse(null);
    }

    private boolean isOccupied(ServerLevel worldIn, BlockPos pos) {
        BlockEntity te = worldIn.getBlockEntity(pos);
        if (te instanceof IBoardGameEntityBlock gameBlock) {
            return worldIn.getEntity(gameBlock.getSitId()) != null;
        }
        // 没有方块实体（3×3 的边角格）→ 当作已占用，别让女仆跑去坐空气
        return true;
    }
}

