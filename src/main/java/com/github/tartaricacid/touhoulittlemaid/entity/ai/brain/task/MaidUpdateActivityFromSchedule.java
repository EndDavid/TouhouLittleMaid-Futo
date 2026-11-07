package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;

/**
 * 按日程表切换活动（工作 / 娱乐 / 休息），移植自上游 {@code MaidUpdateActivityFromSchedule}。
 *
 * <p>额外的职责：</p>
 * <ul>
 *     <li>切换活动时让「居家模式」的女仆重新限制在自己的工作点附近。</li>
 *     <li>坐下 / 骑乘时改走骑乘活动表（{@code tlm_ride_idle/work/rest}），并处理
 *         「非工作点任务要主动下车」的逻辑。</li>
 * </ul>
 *
 * <p>与上游的差异：</p>
 * <ul>
 *     <li>{@code EntitySit}（娱乐方块座椅）尚未移植，因此「坐在娱乐方块上就不下车」的判断暂缺。</li>
 *     <li>上游用 20 tick 的时间戳（{@code Brain#lastScheduleUpdate}）限制切换频率，
 *         本版改为依赖 {@code brain.isActive(...)} 与
 *         {@link Brain#updateActivityFromSchedule(long, long)} 自带的节流 ——
 *         语义一致，且不必为了一个私有字段去动 access widener
 *         （详见 {@code util/MaidReflection} 的说明）。</li>
 * </ul>
 */
public class MaidUpdateActivityFromSchedule extends Behavior<EntityMaid> {
    /** 上一次「活动类型」的快照，仅在居家模式下用于触发工作范围回收。 */
    private Activity cacheActivity;

    public MaidUpdateActivityFromSchedule() {
        super(ImmutableMap.of());
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        Brain<EntityMaid> brain = maid.getBrain();
        long dayTime = level.getDayTime();

        // 让女仆在切换日程表时能够改变自己的活动范围
        Activity activity = brain.getSchedule().getActivityAt((int) (dayTime % 24000L));
        if (this.cacheActivity == null) {
            this.cacheActivity = activity;
        }
        if (!this.cacheActivity.equals(activity) && maid.isHomeModeEnable() && maid.canBrainMoving()) {
            this.cacheActivity = activity;
            maid.restrictToHome();
            BehaviorUtils.setWalkAndLookTargetMemories(maid, maid.getRestrictCenter(), 0.7f, 3);
        }

        // 切换日程表，分骑乘和非骑乘两种情况
        updateActivityFromSchedule(level, maid, brain, gameTime);
    }

    public static void updateActivityFromSchedule(EntityMaid maid, Brain<EntityMaid> brain) {
        if (maid.level instanceof ServerLevel serverLevel) {
            updateActivityFromSchedule(serverLevel, maid, brain, serverLevel.getGameTime());
        }
    }

    public static void updateActivityFromSchedule(EntityMaid maid) {
        if (maid.level instanceof ServerLevel serverLevel) {
            updateActivityFromSchedule(serverLevel, maid, maid.getBrain(), serverLevel.getGameTime());
        }
    }

    /**
     * 坐下 / 骑乘时切换到骑乘活动表，否则交给 vanilla 按日程刷新。
     *
     * <p>重复调用的开销由 {@code brain.isActive(...)} 与
     * {@code Brain#updateActivityFromSchedule} 内部节流吸收，因此这里不需要额外计时。</p>
     */
    private static void updateActivityFromSchedule(ServerLevel level, EntityMaid maid,
                                                   Brain<EntityMaid> brain, long gameTime) {
        long dayTime = level.getDayTime();
        if (!maid.isMaidInSittingPose() && !maid.isPassenger()) {
            brain.updateActivityFromSchedule(dayTime, gameTime);
            return;
        }

        Activity activity = brain.getSchedule().getActivityAt((int) (dayTime % 24000L));
        Activity riderActivity;
        if (activity.equals(Activity.WORK)) {
            riderActivity = InitEntities.RIDE_WORK;
        } else if (activity.equals(Activity.IDLE)) {
            riderActivity = InitEntities.RIDE_IDLE;
        } else {
            riderActivity = InitEntities.RIDE_REST;
        }
        if (brain.isActive(riderActivity)) {
            return;
        }

        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.setActiveActivityIfPossible(riderActivity);

        // 如果是拥有工作点的 task，需要脱离骑乘的实体
        if (maid.isPassenger() && !riderActivity.equals(InitEntities.RIDE_WORK)) {
            if (!maid.getTask().workPointTask(maid)) {
                return;
            }
            // 被主人抱着时不下车：玩家已经在直接控制她的位置了
            if (maid.isCarriedByOwner()) {
                return;
            }
            // TODO EntitySit（娱乐方块座椅）移植后，坐在它上面时不应该下车
            maid.stopRiding();
        }
    }
}

