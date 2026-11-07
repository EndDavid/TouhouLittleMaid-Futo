package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidPathFindingBFS;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

/**
 * 「走到某个方块去干活」的行为基类，移植自上游 {@code MaidMoveToBlockTask}。
 *
 * <p>子类只需实现 {@link #shouldMoveTo}（这个方块值不值得去），本类负责：</p>
 * <ul>
 *     <li>以「工作中心」（居家模式是工作点，否则是自己脚下）为原点螺旋扫描；</li>
 *     <li>用 {@link MaidPathFindingBFS} 做一次 BFS，批量判定可达性；</li>
 *     <li>非居家模式下还要求目标在主人 8 格内，避免主人走远了女仆还在地里干活；</li>
 *     <li>命中后写入 {@code WALK_TARGET} 与 {@code TARGET_POS} 两个记忆，并把该点缓存为
 *         「最近工作点」，下次优先在它附近继续干活。</li>
 * </ul>
 */
public abstract class MaidMoveToBlockTask extends MaidCheckRateTask {
    private static final int MAX_DELAY_TIME = 120;
    private final float movementSpeed;
    private final int verticalSearchRange;
    protected int verticalSearchStart;
    /**
     * 最近工作点标志位（用于记录当前工作的方块位置，缓存下来便于下次在该点附近工作）。
     */
    private BlockPos currentWorkPos;

    public MaidMoveToBlockTask(float movementSpeed) {
        this(movementSpeed, 1);
    }

    public MaidMoveToBlockTask(float movementSpeed, int verticalSearchRange) {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                InitEntities.TARGET_POS, MemoryStatus.VALUE_ABSENT));
        this.movementSpeed = movementSpeed;
        this.verticalSearchRange = verticalSearchRange;
        this.setMaxCheckRate(MAX_DELAY_TIME);
    }

    protected final void searchForDestination(ServerLevel worldIn, EntityMaid maid) {
        MaidPathFindingBFS pathFinding = getOrCreateArrivalMap(worldIn, maid);
        BlockPos centrePos = this.getWorkSearchPos(maid);
        int searchRange = (int) maid.getRestrictRadius();
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        for (int y = this.verticalSearchStart; y <= this.verticalSearchRange; y = y > 0 ? -y : 1 - y) {
            for (int i = 0; i < searchRange; ++i) {
                for (int x = 0; x <= i; x = x > 0 ? -x : 1 - x) {
                    for (int z = x < i && x > -i ? i : 0; z <= i; z = z > 0 ? -z : 1 - z) {
                        mutableBlockPos.setWithOffset(centrePos, x, y - 1, z);
                        if (maid.isWithinRestriction(mutableBlockPos)
                            && shouldMoveTo(worldIn, maid, mutableBlockPos)
                            && checkPathReach(maid, pathFinding, mutableBlockPos)
                            && checkOwnerPos(maid, mutableBlockPos)) {
                            BehaviorUtils.setWalkAndLookTargetMemories(maid, mutableBlockPos, this.movementSpeed, 0);
                            maid.getBrain().setMemory(InitEntities.TARGET_POS,
                                    new BlockPosTracker(mutableBlockPos));
                            // 注意必须先拷贝：mutableBlockPos 是复用的，直接存会在下轮被改写
                            this.currentWorkPos = mutableBlockPos.immutable();
                            this.setNextCheckTickCount(5);
                            this.clearCurrentArrivalMap(pathFinding);
                            return;
                        }
                    }
                }
            }
        }
        this.currentWorkPos = null;
        this.clearCurrentArrivalMap(pathFinding);
    }

    protected void clearCurrentArrivalMap(MaidPathFindingBFS pathFinding) {
        pathFinding.finish();
    }

    /**
     * 获取可达性地图的寻路对象。
     */
    protected MaidPathFindingBFS getOrCreateArrivalMap(ServerLevel worldIn, EntityMaid maid) {
        return new MaidPathFindingBFS(maid.getNavigation().getNodeEvaluator(), worldIn, maid);
    }

    /**
     * 获取工作的搜寻中心点。
     */
    private BlockPos getWorkSearchPos(EntityMaid maid) {
        if (maid.hasRestriction()) {
            // 当且仅当开启 home 模式，并且工作点在工作范围内才返回最近工作点
            if (this.currentWorkPos != null && maid.isWithinRestriction(currentWorkPos)) {
                return this.currentWorkPos;
            } else {
                return maid.getRestrictCenter();
            }
        } else {
            return maid.blockPosition();
        }
    }

    private boolean checkOwnerPos(EntityMaid maid, BlockPos mutableBlockPos) {
        if (maid.isHomeModeEnable()) {
            return true;
        }
        return maid.getOwner() != null && mutableBlockPos.closerToCenterThan(maid.getOwner().position(), 8);
    }

    /**
     * 判定条件。
     *
     * @param worldIn  当前实体所处的 world
     * @param entityIn 当前需要移动的实体
     * @param pos      当前检索的 pos
     * @return 是否符合判定条件
     */
    protected abstract boolean shouldMoveTo(ServerLevel worldIn, EntityMaid entityIn, BlockPos pos);

    protected boolean checkPathReach(EntityMaid maid, BlockPos pos) {
        return maid.canPathReach(pos);
    }

    protected boolean checkPathReach(EntityMaid maid, MaidPathFindingBFS pathFinding, BlockPos pos) {
        return pathFinding.canPathReach(pos);
    }
}

