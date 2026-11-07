package com.github.tartaricacid.touhoulittlemaid.entity.ai.goal;

import com.github.tartaricacid.touhoulittlemaid.entity.monster.EntityFairy;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 女仆妖精的攻击行为，移植自上游 {@code entity/ai/goal/FairyAttackGoal}。
 *
 * <p><b>为什么不用原版的 {@code RangedAttackGoal}：</b>妖精是飞行单位，它的攻击节奏是
 * 「贴到 {@code minDistance} 以内就悬停，蓄 1 秒后开火」，而不是原版那套
 * 「看不到就往前挪一格」。原版 {@code RangedAttackGoal} 会持续重算寻路并把
 * Y 轴速度抹平，对飞行生物来说表现为「原地抖动、永远不开火」。</p>
 *
 * <p>三个关键细节（都与上游一致）：</p>
 * <ul>
 *     <li>进入射程后立刻 {@code setNoGravity(true)} —— 否则飞行移动控制器每 tick
 *         都在往下拽，蓄力期间会缓慢下沉；</li>
 *     <li>蓄力用 {@code withInRangeTime} 计数而不是计时器，出射程就清零，
 *         避免「擦边进出射程」时反复重置；</li>
 *     <li>开火前把目标换算成 {@code 1 - distance/minDistance} 的命中系数 ——
 *         越远伤害与初速越低，这是上游让近身更疼的手法。</li>
 * </ul>
 */
public class FairyAttackGoal extends Goal {
    /**
     * 进入射程后蓄力多少 tick 才开火（20 tick = 1 秒）。
     */
    private static final int MAX_WITH_IN_RANGE_TIME = 20;

    private final EntityFairy entityFairy;
    private final double minDistance;
    private final double speedIn;
    private Path path;
    private int withInRangeTime;

    public FairyAttackGoal(EntityFairy entityFairy, double minDistance, double speedIn) {
        this.entityFairy = entityFairy;
        this.minDistance = minDistance;
        this.speedIn = speedIn;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.entityFairy.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        this.path = this.entityFairy.getNavigation().createPath(target, 0);
        return this.path != null;
    }

    @Override
    public void start() {
        this.entityFairy.getNavigation().moveTo(this.path, this.speedIn);
    }

    @Override
    public void tick() {
        LivingEntity target = this.entityFairy.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        this.entityFairy.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distance = this.entityFairy.distanceTo(target);
        if (this.entityFairy.getSensing().hasLineOfSight(target) && distance >= this.minDistance) {
            this.entityFairy.getNavigation().moveTo(target, this.speedIn);
            this.withInRangeTime = 0;
        } else if (distance < this.minDistance) {
            this.entityFairy.getNavigation().stop();
            this.withInRangeTime++;
            Vec3 motion = this.entityFairy.getDeltaMovement();
            this.entityFairy.setDeltaMovement(motion.x, 0, motion.z);
            this.entityFairy.setNoGravity(true);
            if (this.withInRangeTime > MAX_WITH_IN_RANGE_TIME) {
                float percent = (float) (distance / this.minDistance);
                this.entityFairy.performRangedAttack(target, 1 - percent);
                this.withInRangeTime = 0;
            }
        } else {
            this.withInRangeTime = 0;
        }
    }

    /**
     * 目标变成创造 / 旁观模式的玩家时立刻放弃。
     *
     * <p>不这么写的话，创造玩家站在妖精面前会被无限锁定：原版的
     * {@code NearestAttackableTargetGoal} 只在<b>找目标</b>时过滤创造模式，
     * 一旦目标已经被记住就不会自动丢弃。</p>
     */
    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.entityFairy.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        boolean playerAndCannotBeAttacked = target instanceof Player player
                && (player.isSpectator() || player.isCreative());
        return !playerAndCannotBeAttacked;
    }

    @Override
    public void stop() {
        LivingEntity target = this.entityFairy.getTarget();
        boolean playerAndCannotBeAttacked = target instanceof Player player
                && (player.isSpectator() || player.isCreative());
        if (playerAndCannotBeAttacked) {
            this.entityFairy.setTarget(null);
        }
        this.entityFairy.getNavigation().stop();
        this.withInRangeTime = 0;
        // 放手时把重力还回去，否则她会一直悬在空中不再下落
        this.entityFairy.setNoGravity(false);
    }
}

