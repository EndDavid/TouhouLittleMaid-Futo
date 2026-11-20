package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.block.BlockJoy;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.init.InitPoi;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityJoy;
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
 * 「去娱乐」行为，移植自上游 {@code MaidJoyTask}。
 *
 * <p>挂在<b>闲置活动（IDLE）</b>上，权重 7 —— 它在「撒娇（5）」之后、
 * 「四处张望漫步（20）」之前，也就是「没事干的时候，如果附近有没人坐的键盘 / 书架 / 电脑，
 * 就过去坐着」。</p>
 *
 * <h2>它和 {@link MaidBoardGameTask} 的关系</h2>
 * <p>两者结构几乎一样，上游也是两份代码：棋盘走「工作模式」（{@code board_games} 任务自己挂
 * {@code MaidBoardGameTask}），娱乐方块走「闲置」。区别只有两点：</p>
 * <ul>
 *     <li>本类接受<b>任意</b> {@link BlockJoy}；{@link MaidBoardGameTask} 只认
 *         {@code IBoardGameBlock}（否则女仆会在工作模式下跑去坐键盘）。</li>
 *     <li>本类用基类的 {@code startMaidSit}（坐在方块正中央）；
 *         {@link MaidBoardGameTask} 由棋盘自己决定坐在哪一侧。</li>
 * </ul>
 *
 * <p><b>「没人坐」怎么判断：</b>反查方块实体记录的 {@code SitId} 是否对应一个活着的实体。
 * 查不到方块实体时返回 {@code true}（当作已占用）—— 兴趣点是按方块注册的，
 * 棋盘那张 3×3 的边角格也带着同一个兴趣点，而那些格子没有方块实体。</p>
 */
public class MaidJoyTask extends MaidCheckRateTask {
    private final int closeEnoughDist;
    private final float speed;

    public MaidJoyTask(float movementSpeed, int closeEnoughDist) {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                InitEntities.TARGET_POS, MemoryStatus.VALUE_ABSENT));
        this.closeEnoughDist = closeEnoughDist;
        this.speed = movementSpeed;
        this.setMaxCheckRate(10);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel worldIn, EntityMaid maid) {
        if (super.checkExtraStartConditions(worldIn, maid) && maid.canBrainMoving()) {
            BlockPos joyPos = this.findJoy(worldIn, maid);
            if (joyPos != null && maid.isWithinRestriction(joyPos)) {
                if (joyPos.distToCenterSqr(maid.position()) < Math.pow(this.closeEnoughDist, 2)) {
                    maid.getBrain().setMemory(InitEntities.TARGET_POS, new BlockPosTracker(joyPos));
                    return true;
                }
                BehaviorUtils.setWalkAndLookTargetMemories(maid, joyPos, this.speed, 1);
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
            if (blockState.getBlock() instanceof BlockJoy blockJoy) {
                blockJoy.startMaidSit(maid, blockState, worldIn, pos);
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
    private BlockPos findJoy(ServerLevel world, EntityMaid maid) {
        BlockPos blockPos = maid.getBrainSearchPos();
        PoiManager poiManager = world.getPoiManager();
        int range = (int) maid.getRestrictRadius();
        return poiManager.getInRange(type -> type.equals(InitPoi.JOY_BLOCK), blockPos, range, PoiManager.Occupancy.ANY)
                .map(PoiRecord::getPos).filter(pos -> !this.isOccupied(world, pos))
                .min(Comparator.comparingDouble(pos -> pos.distSqr(maid.blockPosition()))).orElse(null);
    }

    private boolean isOccupied(ServerLevel worldIn, BlockPos pos) {
        BlockEntity te = worldIn.getBlockEntity(pos);
        if (te instanceof TileEntityJoy joy) {
            return worldIn.getEntity(joy.getSitId()) != null;
        }
        return true;
    }
}

