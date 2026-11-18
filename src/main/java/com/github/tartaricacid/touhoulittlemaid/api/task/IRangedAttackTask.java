package com.github.tartaricacid.touhoulittlemaid.api.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 远程攻击类工作模式的公共部分，移植自上游 {@code api/task/IRangedAttackTask}。
 *
 * <p>{@link #targetConditionsTest} 是按配置射程过滤目标的工具方法（上游用它实现
 * 弓 / 弩 / 弹幕 / 三叉戟各自的 {@code canSee}）。本移植版额外接收一个回退值，
 * 因为配置可能在极早期尚未加载。</p>
 */
public interface IRangedAttackTask extends IAttackTask {
    /**
     * 可见性校验工具，来自原版 Sensor 的同一套实现。
     */
    TargetingConditions TARGET_CONDITIONS = TargetingConditions.forCombat();

    /**
     * 寻找第一个可见目标，使用独立的方法，区别于 IAttackTask
     *
     * @param maid 女仆
     * @return 第一个可视对象
     */
    static Optional<? extends LivingEntity> findFirstValidAttackTarget(EntityMaid maid) {
        if (maid.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).isPresent()) {
            List<LivingEntity> list = maid.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).get();
            // 与上游一致：用 maid.canSee(...) 而非固定的 16 格 hasLineOfSight,
            // 这样各远程任务才能自定义射程（见 EntityMaid#canSee）。
            return list.stream()
                    .filter(e -> maid.canAttack(e) && maid.canSee(e))
                    .filter(e -> {
                        net.minecraft.resources.ResourceLocation id =
                                net.minecraft.core.Registry.ENTITY_TYPE.getKey(e.getType());
                        return id == null || !com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig
                                .rangedAttackIgnore().contains(id.toString());
                    })
                    .findAny();
        }
        return Optional.empty();
    }

    /**
     * 按「配置的射程」校验目标是否可被攻击，移植自上游
     * {@code IRangedAttackTask#targetConditionsTest}。
     *
     * <p>上游签名是 {@code (maid, target, ForgeConfigSpec.IntValue)}，直接
     * {@code configRange.get()}。本移植版额外接收一个 {@code fallbackRange}：
     * 配置字段可能为 {@code null}（说明 {@code init()} 没调），
     * 而配置文件尚未加载时 {@code get()} 返回的是定义里的默认值。</p>
     *
     * @param configRange   配置项（可为 {@code null}）
     * @param fallbackRange 配置未加载时的回退值（与上游默认值一致）
     */
    static boolean targetConditionsTest(EntityMaid maid, LivingEntity target,
                                        @Nullable ForgeConfigSpec.IntValue configRange, int fallbackRange) {
        int range = fallbackRange;
        try {
            if (configRange != null) {
                range = configRange.get();
            }
        } catch (Exception ignored) {
            // 配置未加载 → 用回退值
        }
        TARGET_CONDITIONS.range(range);
        return TARGET_CONDITIONS.test(maid, target);
    }

    /**
     * 执行射击动作
     *
     * @param shooter        射击者
     * @param target         射击目标
     * @param distanceFactor 距离因素，即弓箭的蓄力值
     */
    void performRangedAttack(EntityMaid shooter, LivingEntity target, float distanceFactor);

    /**
     * 女仆是否能看到敌人
     * <p>
     * 因为原版默认的攻击识别范围是固定死的 16 格，但是一些远程武器我们希望获得超视距打击
     * 通过修改此处来获得更远的攻击距离
     *
     * @param maid   女仆
     * @param target 攻击目标
     * @return 是否在可视范围内
     */
    default boolean canSee(EntityMaid maid, LivingEntity target) {
        return BehaviorUtils.canSee(maid, target);
    }
}
