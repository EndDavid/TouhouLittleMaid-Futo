package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.CrossbowAttack;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

/**
 * 原版的 {@code CrossbowAttack} 在 {@code checkExtraStartConditions} 里调用
 * {@code BehaviorUtils.canSee}，而那个封装会把「能看见」限制在 16 格内，
 * 于是超视距打击失效。这里只把这一句换掉，其余逻辑仍交给原版。
 *
 * <p>1.18.2 的可见性方法是 {@code hasLineOfSight}（没有 {@code LivingEntity#canSee}）。</p>
 */
public class MaidCrossbowAttack extends CrossbowAttack<EntityMaid, EntityMaid> {
    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        return maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET)
                .map(target -> maid.isHolding(this::isCrossbow) && maid.hasLineOfSight(target))
                .orElse(false);
    }

    /**
     * 标记「正在远程攻击」。
     *
     * <p>原版 {@code CrossbowAttack} 只写 {@code setChargingCrossbow}，<b>从不</b>写
     * {@code setSwingingArms}；而 {@code MaidShootTargetTask} / {@code MaidTridentTargetTask} /
     * {@code MaidAttackStrafingTask} 都会写。少了这一个标志，弩在「装填 → 发射 → 冷却」
     * 的冷却期会被动画层判成「不在攻击」，手臂于是掉回待机姿态 ——
     * 也就是「每射一发就把手放下」。</p>
     *
     * <p>上游没有这一段，是因为它对旧版模型用的 {@code MaidBaseAnimation#getArmSwing}
     * 本来就只认这个标志，弩因此在旧版模型上一直不举臂。这里补上，是为了让弓 / 弩 /
     * 御币 / 三叉戟这四类远程武器的旧版模型表现一致。</p>
     */
    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        super.start(level, maid, gameTime);
        maid.setSwingingArms(true);
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        super.stop(level, maid, gameTime);
        maid.setSwingingArms(false);
    }

    private boolean isCrossbow(ItemStack stack) {
        return stack.getItem() instanceof CrossbowItem;
    }
}

