package com.github.tartaricacid.touhoulittlemaid.entity.monster;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityPowerPoint;
import net.minecraft.world.entity.LivingEntity;

/**
 * 「死亡时掉落 P 点」的标记接口，移植自上游 {@code entity/monster/IHasPowerPoint}。
 *
 * <p>上游目前只有 {@code EntityFairy} 实现它。之所以做成接口而不是直接写在妖精里，
 * 是为了让「哪些生物会掉 P 点」成为一个可以扩展的约定（附属模组 / 未来的妖精变体）。</p>
 *
 * <p><b>deathTime == 20 这个魔数：</b>死亡动画播完、实体即将被移除的那一 tick。
 * 在这个时机掉落，P 点会正好出现在尸体消失的位置，观感与上游一致；
 * 太早掉会飘走，太晚掉则实体已被移除、拿不到坐标。</p>
 */
public interface IHasPowerPoint {
    /**
     * 掉落的总 P 点数值。
     *
     * <p>与玩家灵力的换算是 <b>100 : 1</b> —— 也就是说返回值 {@code 16} 对应 0.16 灵力。</p>
     */
    int getPowerPoint();

    /**
     * 按 {@link EntityPowerPoint#getPowerValue(int)} 的档位把总数值拆成若干堆掉落物。
     *
     * <p>拆分而不是掉一个「价值 16 的大点」是上游的设计：实体贴图按 value 分 11 档，
     * 一个巨大的单点会显示成最大号图标，视觉上比一堆小点更突兀。</p>
     */
    default void dropPowerPoint(LivingEntity entity) {
        int dropTime = 20;
        if (entity.deathTime == dropTime && !entity.level.isClientSide) {
            int totalPowerPoint = getPowerPoint();
            while (totalPowerPoint > 0) {
                int powerSplit = EntityPowerPoint.getPowerValue(totalPowerPoint);
                totalPowerPoint -= powerSplit;
                entity.level.addFreshEntity(EntityPowerPoint.create(entity.level,
                        entity.getX(), entity.getY(), entity.getZ(), powerSplit));
            }
        }
    }
}

