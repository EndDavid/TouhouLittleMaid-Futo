package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidAttackStrafingTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidAwaitTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidBedTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidBegTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidClearHurtTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidHealSelfTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidWorkMealTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidClearSleepTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidInteractWithDoor;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidJoyTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFollowOwnerTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidPanicTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidPickupEntitiesTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidRandomStroll;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidRunAwayTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidShootTargetTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidUpdateActivityFromSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.RunIf;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.SetEntityLookTarget;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;

import java.util.List;
import java.util.function.Predicate;

/**
 * 女仆的 Brain（行为树）注册表，移植自上游 {@code entity/ai/brain/MaidBrain}。
 *
 * <p>结构完全对齐上游：</p>
 * <ul>
 *     <li>CORE：游泳上浮 / 换气 / 爬梯 / 看向目标 / 慌乱 / 出界清理 / 开门 / 移动 / 跟随主人 /
 *         自我治疗 / 拾取掉落物 / 起床</li>
 *     <li>IDLE：撒娇 / 觅食 / 娱乐 / 四处张望与漫步</li>
 *     <li>WORK：由当前 Task 的 {@code createBrainTasks} 决定</li>
 *     <li>REST：睡觉（床）</li>
 *     <li>PANIC：清伤 / 逃跑</li>
 *     <li>tlm_ride_idle / tlm_ride_work / tlm_ride_rest：坐下或骑乘时的活动表</li>
 * </ul>
 *
 * <p><b>与上游的差异</b>（都因为对应子系统还没移植，按依赖顺序排在后续里程碑）：</p>
 * <ul>
 *     <li>游泳相关（{@code MaidSwimJumpTask} / {@code MaidBreathAir(Task|StopTask)} /
 *         {@code MaidClimbTask}）：需要游泳管理器与专门的寻路，M3-游泳。</li>
 *     <li>{@code MaidInteractWithDoor}：需要门记忆与路径交互，M3-寻路。</li>
 *     <li>{@code MaidHealSelfTask} / {@code MaidHomeMealTask} / {@code MaidWorkMealTask}：
 *         需要「饮食系统 + 背包」，M2-背包。</li>
 *     <li>{@code MaidBedTask}：需要床的搜索，M3-方块交互。</li>
 * </ul>
 * <p>已接上的：{@code MaidJoyTask}（键盘 / 书架 / 电脑，IDLE 权重 7）、
 * {@code MaidBoardGameTask}（三张棋盘，由 {@code board_games} 任务挂在工作模式上）。</p>
 */
public final class MaidBrain {
    private MaidBrain() {
    }

    public static ImmutableList<MemoryModuleType<?>> getMemoryTypes() {
        List<MemoryModuleType<?>> defaultTypes = Lists.newArrayList(
                MemoryModuleType.PATH,
                MemoryModuleType.DOORS_TO_CLOSE,
                MemoryModuleType.LOOK_TARGET,
                MemoryModuleType.NEAREST_HOSTILE,
                MemoryModuleType.HURT_BY,
                MemoryModuleType.HURT_BY_ENTITY,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
                MemoryModuleType.WALK_TARGET,
                MemoryModuleType.ATTACK_TARGET,
                MemoryModuleType.ATTACK_COOLING_DOWN,
                InitEntities.TARGET_POS
        );
        return ImmutableList.copyOf(defaultTypes);
    }

    public static ImmutableList<SensorType<? extends Sensor<? super EntityMaid>>> getSensorTypes() {
        List<SensorType<? extends Sensor<? super EntityMaid>>> defaultTypes = Lists.newArrayList(
                InitEntities.MAID_NEAREST_LIVING_ENTITY_SENSOR,
                SensorType.HURT_BY,
                InitEntities.MAID_HOSTILES_SENSOR,
                InitEntities.MAID_PICKUP_ENTITIES_SENSOR
        );
        return ImmutableList.copyOf(defaultTypes);
    }

    public static void registerBrainGoals(Brain<EntityMaid> brain, EntityMaid maid) {
        registerSchedule(brain, maid);
        registerCoreGoals(brain);
        registerPanicGoals(brain);

        registerRideIdleGoals(brain);
        registerRideWorkGoals(brain, maid);
        registerRideRestGoals(brain);

        registerIdleGoals(brain);
        registerWorkGoals(brain, maid);
        registerRestGoals(brain);

        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
        MaidUpdateActivityFromSchedule.updateActivityFromSchedule(maid, brain);
    }

    private static void registerSchedule(Brain<EntityMaid> brain, EntityMaid maid) {
        switch (maid.getSchedule()) {
            case ALL:
                brain.setSchedule(InitEntities.MAID_ALL_DAY_SCHEDULES);
                break;
            case NIGHT:
                brain.setSchedule(InitEntities.MAID_NIGHT_SHIFT_SCHEDULES);
                break;
            case DAY:
            default:
                brain.setSchedule(InitEntities.MAID_DAY_SHIFT_SCHEDULES);
                break;
        }
    }

    private static void registerCoreGoals(Brain<EntityMaid> brain) {
        Pair<Integer, Behavior<? super EntityMaid>> look = Pair.of(0, new LookAtTargetSink(45, 90));
        Pair<Integer, Behavior<? super EntityMaid>> maidPanic = Pair.of(1, new MaidPanicTask());
        Pair<Integer, Behavior<? super EntityMaid>> healSelf = Pair.of(3, new MaidHealSelfTask());
        Pair<Integer, Behavior<? super EntityMaid>> maidAwait = Pair.of(1, new MaidAwaitTask());
        Pair<Integer, Behavior<? super EntityMaid>> walkToTarget = Pair.of(2, new MoveToTargetSink());
        Pair<Integer, Behavior<? super EntityMaid>> followOwner = Pair.of(3, new MaidFollowOwnerTask(0.5f, 2));
        Pair<Integer, Behavior<? super EntityMaid>> pickupItem =
                Pair.of(10, new MaidPickupEntitiesTask(EntityMaid::isPickup, 0.6f));
        // 开门 / 开栅栏门：走完一个节点就开，离开够远再关（上游 MaidInteractWithDoor）
        Pair<Integer, Behavior<? super EntityMaid>> interactWithDoor = Pair.of(1, new MaidInteractWithDoor());
        Pair<Integer, Behavior<? super EntityMaid>> clearSleep = Pair.of(99, new MaidClearSleepTask());

        brain.addActivity(Activity.CORE, ImmutableList.of(
                look, maidPanic, maidAwait, walkToTarget, followOwner, pickupItem, healSelf, interactWithDoor, clearSleep));
    }

    private static void registerIdleGoals(Brain<EntityMaid> brain) {
        Pair<Integer, Behavior<? super EntityMaid>> beg = Pair.of(5, new MaidBegTask());
        // 娱乐方块（键盘 / 书架 / 电脑）：闲置时去坐着涨好感度（上游权重 7）
        Pair<Integer, Behavior<? super EntityMaid>> joy = Pair.of(7, new MaidJoyTask(0.6f, 2));
        Pair<Integer, Behavior<? super EntityMaid>> supplemented = Pair.of(20, getLookAndRandomWalk(maid -> true));
        Pair<Integer, Behavior<? super EntityMaid>> updateActivity = Pair.of(99, new MaidUpdateActivityFromSchedule());

        brain.addActivity(Activity.IDLE, ImmutableList.of(beg, joy, supplemented, updateActivity));
    }

    private static void registerWorkGoals(Brain<EntityMaid> brain, EntityMaid maid) {
        Pair<Integer, Behavior<? super EntityMaid>> updateActivity = Pair.of(99, new MaidUpdateActivityFromSchedule());
        IMaidTask task = maid.getTask();
        List<Pair<Integer, Behavior<? super EntityMaid>>> pairMaidList = Lists.newArrayList(task.createBrainTasks(maid));
        pairMaidList.add(updateActivity);
        pairMaidList.add(Pair.of(6, new MaidBegTask()));
        pairMaidList.add(Pair.of(7, new MaidWorkMealTask()));
        pairMaidList.add(Pair.of(20, getLookAndRandomWalk(e -> e.getTask().enableLookAndRandomWalk(e))));
        brain.addActivity(Activity.WORK, ImmutableList.copyOf(pairMaidList));
    }

    private static void registerRestGoals(Brain<EntityMaid> brain) {
        Pair<Integer, Behavior<? super EntityMaid>> bed = Pair.of(5, new MaidBedTask(0.6f, 2));
        Pair<Integer, Behavior<? super EntityMaid>> supplemented = Pair.of(20, getLookAndRandomWalk(maid -> true));
        Pair<Integer, Behavior<? super EntityMaid>> updateActivity = Pair.of(99, new MaidUpdateActivityFromSchedule());

        brain.addActivity(Activity.REST, ImmutableList.of(bed, supplemented, updateActivity));
    }

    private static void registerPanicGoals(Brain<EntityMaid> brain) {
        Pair<Integer, Behavior<? super EntityMaid>> clearHurt = Pair.of(5, new MaidClearHurtTask());
        Pair<Integer, Behavior<? super EntityMaid>> runAway =
                Pair.of(5, MaidRunAwayTask.entity(MemoryModuleType.NEAREST_HOSTILE, 0.7f, false));

        brain.addActivity(Activity.PANIC, ImmutableList.of(clearHurt, runAway));
    }

    private static void registerRideIdleGoals(Brain<EntityMaid> brain) {
        Pair<Integer, Behavior<? super EntityMaid>> beg = Pair.of(4, new MaidBegTask());
        Pair<Integer, Behavior<? super EntityMaid>> look = Pair.of(6, getLook(maid -> true));
        Pair<Integer, Behavior<? super EntityMaid>> updateActivity = Pair.of(99, new MaidUpdateActivityFromSchedule());

        brain.addActivity(InitEntities.RIDE_IDLE, ImmutableList.of(beg, look, updateActivity));
    }

    private static void registerRideWorkGoals(Brain<EntityMaid> brain, EntityMaid maid) {
        Pair<Integer, Behavior<? super EntityMaid>> updateActivity = Pair.of(99, new MaidUpdateActivityFromSchedule());
        IMaidTask task = maid.getTask();
        List<Pair<Integer, Behavior<? super EntityMaid>>> pairMaidList = Lists.newArrayList(task.createRideBrainTasks(maid));
        pairMaidList.add(updateActivity);
        pairMaidList.add(Pair.of(6, new MaidBegTask()));
        pairMaidList.add(Pair.of(7, new MaidWorkMealTask()));
        pairMaidList.add(Pair.of(20, getLook(e -> e.getTask().enableLookAndRandomWalk(e))));
        brain.addActivity(InitEntities.RIDE_WORK, ImmutableList.copyOf(pairMaidList));
    }

    private static void registerRideRestGoals(Brain<EntityMaid> brain) {
        Pair<Integer, Behavior<? super EntityMaid>> updateActivity = Pair.of(99, new MaidUpdateActivityFromSchedule());
        brain.addActivity(InitEntities.RIDE_REST, ImmutableList.of(updateActivity));
    }

    private static Behavior<? super EntityMaid> getLook(Predicate<EntityMaid> enableCondition) {
        Pair<Behavior<? super EntityMaid>, Integer> lookToPlayer = Pair.of(new SetEntityLookTarget(EntityType.PLAYER, 5), 1);
        Pair<Behavior<? super EntityMaid>, Integer> lookToMaid = Pair.of(new SetEntityLookTarget(InitEntities.MAID, 5), 1);
        Pair<Behavior<? super EntityMaid>, Integer> lookToWolf = Pair.of(new SetEntityLookTarget(EntityType.WOLF, 5), 1);
        Pair<Behavior<? super EntityMaid>, Integer> lookToCat = Pair.of(new SetEntityLookTarget(EntityType.CAT, 5), 1);
        Pair<Behavior<? super EntityMaid>, Integer> lookToParrot = Pair.of(new SetEntityLookTarget(EntityType.PARROT, 5), 1);
        Pair<Behavior<? super EntityMaid>, Integer> noLook = Pair.of(new DoNothing(30, 60), 2);
        RunOne<EntityMaid> firstShuffledTask = new RunOne<>(ImmutableList.of(
                lookToPlayer, lookToMaid, lookToWolf, lookToCat, lookToParrot, noLook));
        return new RunIf<>(enableCondition, firstShuffledTask);
    }

    private static Behavior<? super EntityMaid> getLookAndRandomWalk(Predicate<EntityMaid> enableCondition) {
        Pair<Behavior<? super EntityMaid>, Integer> lookToPlayer = Pair.of(new SetEntityLookTarget(EntityType.PLAYER, 5), 1);
        Pair<Behavior<? super EntityMaid>, Integer> lookToMaid = Pair.of(new SetEntityLookTarget(InitEntities.MAID, 5), 1);
        Pair<Behavior<? super EntityMaid>, Integer> lookToWolf = Pair.of(new SetEntityLookTarget(EntityType.WOLF, 5), 1);
        Pair<Behavior<? super EntityMaid>, Integer> lookToCat = Pair.of(new SetEntityLookTarget(EntityType.CAT, 5), 1);
        Pair<Behavior<? super EntityMaid>, Integer> lookToParrot = Pair.of(new SetEntityLookTarget(EntityType.PARROT, 5), 1);
        Pair<Behavior<? super EntityMaid>, Integer> noLook = Pair.of(new DoNothing(30, 60), 2);
        Pair<Behavior<? super EntityMaid>, Integer> walkRandomly = Pair.of(new MaidRandomStroll(0.3f, 5, 3), 1);
        RunOne<EntityMaid> firstShuffledTask = new RunOne<>(ImmutableList.of(
                lookToPlayer, lookToMaid, lookToWolf, lookToCat, lookToParrot, walkRandomly, noLook));
        return new RunIf<>(e -> lookAroundCondition(e, enableCondition), firstShuffledTask);
    }

    public static boolean lookAroundCondition(EntityMaid maid, Predicate<EntityMaid> enableCondition) {
        return enableCondition.test(maid) && !maid.isBegging() && !maid.isSleeping();
    }
}