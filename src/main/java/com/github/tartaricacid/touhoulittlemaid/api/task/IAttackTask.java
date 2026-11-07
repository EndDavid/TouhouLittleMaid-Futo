package com.github.tartaricacid.touhoulittlemaid.api.task;

import com.github.tartaricacid.touhoulittlemaid.entity.misc.DefaultMonsterType;
import com.github.tartaricacid.touhoulittlemaid.entity.misc.MonsterType;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * 攻击类工作模式的公共部分，移植自上游 {@code api/task/IAttackTask}。
 *
 * <p>与上游的差异（依赖尚未移植的子系统，见注释）：</p>
 * <ul>
 *     <li>攻击白/黑名单配置（{@code MaidConfig.MAID_ATTACK_IGNORE}）与每个女仆独立的
 *         {@code AttackListData}：需要配置系统与 Task 数据系统，M2-3 一并补回。</li>
 *     <li>{@code getTaskConfigGuiProvider}：需要容器系统，M2-2。</li>
 * </ul>
 */
public interface IAttackTask extends IMaidTask {
    /**
     * 寻找合适的第一个攻击目标
     *
     * @param maid 女仆
     * @return 合适的攻击目标
     */
    static Optional<? extends LivingEntity> findFirstValidAttackTarget(EntityMaid maid) {
        return maid.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).flatMap(
                mobs -> mobs.findClosest((e) -> maid.canAttack(e) && maid.isWithinRestriction(e.blockPosition())));
    }

    /**
     * 能否攻击该对象
     *
     * @param maid   女仆
     * @param target 攻击的目标
     * @return 能否攻击？
     */
    default boolean canAttack(EntityMaid maid, LivingEntity target) {
        // 获取实体 ID
        ResourceLocation location = Registry.ENTITY_TYPE.getKey(target.getType());
        if (location == null) {
            return false;
        }
        String id = location.toString();

        // 配置里的攻击忽略名单（上游 MaidConfig.MAID_ATTACK_IGNORE）
        if (com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig.attackIgnore().contains(id)) {
            return false;
        }

        // 排除一些盔甲架，还有玩家
        if (target instanceof ArmorStand || target instanceof Player) {
            return false;
        }
        // 有主的宠物也不攻击
        if (target instanceof TamableAnimal tamableAnimal && tamableAnimal.getOwnerUUID() != null) {
            return false;
        }

        // 女仆自己的攻击名单（任务设置界面里配置的）优先于默认判定：
        MonsterType monsterType = maid.getAttackList().attackGroups().get(location);
        if (monsterType == null) {
            // 名单里没有，走默认判定
            monsterType = DefaultMonsterType.getMonsterType(target);
        }
        return DefaultMonsterType.canAttack(maid, target, monsterType);
    }

    /**
     * 是否拥有额外攻击方式，用于一些额外增伤的设计
     * 比如女仆副手持有灭火器，会额外对下界生物造成二次伤害
     *
     * @param maid   女仆
     * @param target 攻击目标
     * @return 是否有额外攻击方式
     */
    default boolean hasExtraAttack(EntityMaid maid, Entity target) {
        return false;
    }

    /**
     * 执行额外伤害
     *
     * @param maid   女仆
     * @param target 攻击目标
     * @return 是否成功造成伤害
     */
    default boolean doExtraAttack(EntityMaid maid, Entity target) {
        return false;
    }

    @Override
    default boolean enablePanic(EntityMaid maid) {
        return false;
    }
}

