package com.github.tartaricacid.touhoulittlemaid.entity.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 工作模式注册表，移植自上游 {@code entity/task/TaskManager}。
 *
 * <p>已移植：{@code idle}（空闲）、{@code attack}（近战）、{@code ranged_attack}（弓）、
 * {@code crossbow_attack}（弩）、{@code trident_attack}（三叉戟）,
 * 以及 {@code shears}（剪羊毛）、{@code milk}（挤奶）、{@code honey}（采蜜）、
 * {@code torch}（插火把）、{@code feed}（喂食主人）、{@code feed_animal}（喂养动物）、
 * {@code farm}（普通作物）、{@code sugar_cane}（甘蔗）、{@code melon}（西瓜 / 南瓜）、
 * {@code extinguishing}（灭火）。</p>
 *
 * <p>后续里程碑再补的：</p>
 * <ul>
 *     <li>{@code cocoa} / {@code grass} / {@code snow} / {@code fishing}：需要丛林 / 雪地专用逻辑与
 *         钓竿 AI，属 M3。</li>
 *     <li>{@code danmaku_attack}：需要弹幕实体，属 M3-实体。</li>
 * </ul>
 */
public final class TaskManager {
    private static Map<ResourceLocation, IMaidTask> TASK_MAP;
    private static List<IMaidTask> TASK_INDEX;
    private static IMaidTask IDLE_TASK;

    private TaskManager() {
    }

    public static void init() {
        TaskManager manager = new TaskManager();
        IDLE_TASK = new TaskIdle();
        TASK_MAP = Maps.newHashMap();
        TASK_INDEX = Lists.newArrayList();

        manager.add(IDLE_TASK);
        manager.add(new TaskAttack());
        manager.add(new TaskBowAttack());
        manager.add(new TaskCrossBowAttack());
        manager.add(new TaskTridentAttack());
        manager.add(new TaskDanmakuAttack());

        // 依赖背包、但不需要 GUI 的工作模式
        manager.add(new TaskShears());
        manager.add(new TaskMilk());
        manager.add(new TaskHoney());
        manager.add(new TaskTorch());
        manager.add(new TaskFeedOwner());
        manager.add(new TaskFeedAnimal());
        manager.add(new TaskNormalFarm());
        manager.add(new TaskSugarCane());
        manager.add(new TaskMelon());
        manager.add(new TaskExtinguishing());
        manager.add(new TaskCocoa());
        manager.add(new TaskGrass());
        manager.add(new TaskSnow());
        manager.add(new TaskFishing());

        TASK_MAP = ImmutableMap.copyOf(TASK_MAP);
        TASK_INDEX = ImmutableList.copyOf(TASK_INDEX);
    }

    /**
     * 获取 Task
     */
    public static Optional<IMaidTask> findTask(ResourceLocation uid) {
        return Optional.ofNullable(TASK_MAP.get(uid));
    }

    /**
     * 默认 Task
     */
    public static IMaidTask getIdleTask() {
        return IDLE_TASK;
    }

    public static Map<ResourceLocation, IMaidTask> getTaskMap() {
        return TASK_MAP;
    }

    public static List<IMaidTask> getTaskIndex() {
        return TASK_INDEX;
    }

    /**
     * 注册 Task
     */
    public void add(IMaidTask task) {
        TASK_MAP.put(task.getUid(), task);
        TASK_INDEX.add(task);
    }
}
