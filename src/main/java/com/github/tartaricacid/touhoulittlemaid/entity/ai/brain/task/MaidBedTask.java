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
 * 鎵惧簥鐫¤锛岄€愬瓧绉绘鑷笂娓?{@code entity/ai/brain/task/MaidBedTask}銆?
 *
 * <h2>涓ゆ寮忔祦绋?/h2>
 * <ol>
 *     <li>绗竴娆¤Е鍙戯細鍦ㄣ€屾悳绱腑蹇冦€嶅崐寰勫唴鎵炬渶杩戠殑绌哄簥锛屽お杩滃氨璧拌繃鍘诲苟
 *         {@code setNextCheckTickCount(5)}锛堣蛋寰楄繎涓€鐐瑰啀閲嶇畻锛夛紱</li>
 *     <li>绗簩娆¤Е鍙戯細璺濈宸茶繘鍏?{@code closeEnoughDist}锛屾妸鍧愭爣鍐欒繘
 *         {@code TARGET_POS} 骞惰繑鍥?true 鈥斺€?{@link #start} 閲岀湡姝?{@code startSleeping}銆?/li>
 * </ol>
 *
 * <p>鎼滅储涓績鐢ㄧ殑鏄?{@code getBrainSearchPos()}锛氬紑浜嗗眳瀹舵ā寮忓氨鏄眳瀹剁偣锛屽惁鍒欐槸鑷繁鑴氫笅銆?/p>
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
            // 鍐嶇‘璁や竴娆★細杩欎竴 tick 閲屽簥鍙兘宸茬粡琚埆浜哄崰浜?/ 琚媶浜?
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
