package com.github.tartaricacid.touhoulittlemaid.entity.task;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.api.task.IRangedAttackTask;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidAttackStrafingTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidRangedWalkToTarget;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidShootTargetTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.projectile.DanmakuShoot;
import com.github.tartaricacid.touhoulittlemaid.init.InitEnchantments;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.item.ItemHakureiGohei;
import com.github.tartaricacid.touhoulittlemaid.util.SoundUtil;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.RunIf;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromAttackTargetIfTargetOutOfReach;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * 弹幕攻击模式，移植自上游 {@code entity/task/TaskDanmakuAttack}。
 *
 * <p>主手必须持有御币（{@link ItemHakureiGohei}），否则整个模式不生效。
 * 弹幕数量按<b>周围可攻击目标数</b>三档切换（上游设计，用来避免卡服）：</p>
 *
 * <table border="1">
 *     <caption>目标数与弹幕形态</caption>
 *     <tr><th>可攻击目标数</th><th>形态</th><th>伤害系数</th></tr>
 *     <tr><td>≤ 1</td><td>单发直射（有「多重射击」时改为 3 发 15° 扇）</td><td>{@code attack * (f + 1)}</td></tr>
 *     <tr><td>≤ 5</td><td>8 发 60° 扇</td><td>{@code attack * (f + 1.2)}</td></tr>
 *     <tr><td>&gt; 5</td><td>32 发 120° 扇</td><td>{@code attack * (f + 1.5)}</td></tr>
 * </table>
 *
 * <p><b>三个自定义附魔</b>（{@code InitEnchantments}）：</p>
 * <ul>
 *     <li>{@code impeding}（滞弹）→ 弹幕命中后按等级施加缓慢（时长
 *         {@code (20 + level * 10) * 20} tick，在 {@code EntityDanmaku} 里结算）；</li>
 *     <li>{@code speedy}（速弹）→ 初速乘 {@code level + 1}，即 I 级两倍、II 级三倍；</li>
 *     <li>{@code enders_ender}（末影之末）→ 弹幕能伤到末影人 / 末影龙。</li>
 * </ul>
 *
 * <p><b>与上游的差异：</b></p>
 * <ul>
 *     <li>{@code isWeapon(maid, stack)}（上游 1.20.1 用它做「主手是不是武器」的通用判定）
 *         本移植版的 {@code IAttackTask} 还没有这个方法，因此直接用 {@link #hasGohei}。</li>
 *     <li>上游在 {@code speed} 之上还叠了一段距离修正
 *         （{@code += clamp(distance / 40 - 0.4, 0, 2.4)}），不准确度也随距离从 1.0 收到 0.2。
 *         本移植版目前用固定值，属于既有实现，未在这次改动范围内。</li>
 * </ul>
 */
public class TaskDanmakuAttack implements IRangedAttackTask {
    public static final ResourceLocation UID =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "danmaku_attack");
    /**
     * 超过这个距离就放弃目标（上游同名常量）。
     */
    private static final int MAX_STOP_ATTACK_DISTANCE = 16;

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return InitItems.HAKUREI_GOHEI.getDefaultInstance();
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return SoundUtil.attackSound(maid, InitSounds.MAID_DANMAKU_ATTACK, 0.5F);
    }

    @Override
    public List<Pair<Integer, Behavior<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        RunIf<EntityMaid> supplementedTask = new RunIf<>(this::hasGohei,
                new StartAttacking<>(IRangedAttackTask::findFirstValidAttackTarget));
        StopAttackingIfTargetInvalid<EntityMaid> findTargetTask = new StopAttackingIfTargetInvalid<>(
                target -> !hasGohei(maid) || farAway(target, maid));
        // 必须用 TLM 自己的 MaidRangedWalkToTarget，不能用原版的
        // SetWalkTargetFromAttackTargetIfTargetOutOfReach。
        //
        // 原版那个是给近战写的：它只在「进入近战距离（约 2 格）」后才停止写 WALK_TARGET。
        // 而 MaidAttackStrafingTask 的前置条件是 WALK_TARGET 必须「不存在」——
        // 于是原版那个任务会让女仆一路走到怪物脸上，同时把走位任务永久挡在外面，
        // 表现就是「不后撤不横移，反而往前冲」。
        // MaidRangedWalkToTarget 改成「只要 canSee(目标) 且距离 < 工作半径 × 0.65 就清掉
        // WALK_TARGET」，走位任务才能接管。
        Behavior<EntityMaid> moveToTargetTask = new MaidRangedWalkToTarget(0.6F);
        MaidAttackStrafingTask maidAttackStrafingTask = new MaidAttackStrafingTask();
        MaidShootTargetTask shootTargetTask = new MaidShootTargetTask(2);

        return Lists.newArrayList(
                Pair.of(5, supplementedTask),
                Pair.of(5, findTargetTask),
                Pair.of(5, moveToTargetTask),
                Pair.of(5, maidAttackStrafingTask),
                Pair.of(5, shootTargetTask)
        );
    }

    @Override
    public List<Pair<Integer, Behavior<? super EntityMaid>>> createRideBrainTasks(EntityMaid maid) {
        Behavior<EntityMaid> supplementedTask = new StartAttacking<>(
                this::hasGohei, IRangedAttackTask::findFirstValidAttackTarget);
        Behavior<EntityMaid> findTargetTask = new StopAttackingIfTargetInvalid<>(
                target -> !hasGohei(maid) || farAway(target, maid));
        Behavior<EntityMaid> shootTargetTask = new MaidShootTargetTask(2);

        return Lists.newArrayList(
                Pair.of(5, supplementedTask),
                Pair.of(5, findTargetTask),
                Pair.of(5, shootTargetTask)
        );
    }

    @Override
    public void performRangedAttack(EntityMaid shooter, LivingEntity target, float distanceFactor) {
        shooter.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).ifPresent(livingEntities -> {
            ItemStack mainHandItem = shooter.getMainHandItem();
            if (!ItemHakureiGohei.isGohei(mainHandItem)) {
                return;
            }
            // 周围可攻击目标数决定弹幕形态。
            //
            // 这里必须用上游 1.20.1 的 enemyEntityTest，而不是 1.18.2 里的裸 canAttack：
            // 判定同时要求「类型与当前目标相同」且「女仆能看见」，否则只要附近还有别的
            // 可攻击生物（哪怕隔着墙、哪怕是另一种怪），计数就会 > 1，
            // 于是「面对单个目标也放一圈弹幕」。
            long entityCount = livingEntities.stream()
                    .filter(entity -> this.enemyEntityTest(shooter, target, entity))
                    .count();
            Level level = shooter.level;

            AttributeInstance attackDamage = shooter.getAttribute(Attributes.ATTACK_DAMAGE);
            float attackValue = 2.0F;
            if (attackDamage != null) {
                attackValue = (float) attackDamage.getBaseValue();
            }
            // 三个自定义附魔（滞弹 / 速弹 / 末影之末）的等级，判定与上游 1.20.1 一致：
            // 都从主手御币的附魔标签里读，缺省 0 / false。
            int impedingLevel = EnchantmentHelper.getItemEnchantmentLevel(InitEnchantments.IMPEDING, mainHandItem);
            int speedyLevel = EnchantmentHelper.getItemEnchantmentLevel(InitEnchantments.SPEEDY, mainHandItem);
            boolean hurtEnderman = EnchantmentHelper.getItemEnchantmentLevel(InitEnchantments.ENDERS_ENDER, mainHandItem) > 0;
            int multiShotLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT, mainHandItem);

            float speed = (0.3F * (distanceFactor + 1)) * (speedyLevel + 1);

            if (entityCount <= 1) {
                if (multiShotLevel > 0) {
                    DanmakuShoot.create().setWorld(level).setThrower(shooter)
                            .setTarget(target).setRandomColor().setRandomType()
                            .setDamage(attackValue * (distanceFactor + 1.2F)).setGravity(0)
                            .setVelocity(speed).setHurtEnderman(hurtEnderman)
                            .setInaccuracy(1.0F).setFanNum(3).setYawTotal(Math.PI / 12)
                            .setImpedingLevel(impedingLevel)
                            .fanShapedShot();
                } else {
                    DanmakuShoot.create().setWorld(level).setThrower(shooter)
                            .setTarget(target).setRandomColor().setRandomType()
                            .setDamage(attackValue * (distanceFactor + 1)).setGravity(0)
                            .setVelocity(speed).setHurtEnderman(hurtEnderman)
                            .setInaccuracy(0.2F).setImpedingLevel(impedingLevel)
                            .aimedShot();
                }
            } else if (entityCount <= 5) {
                DanmakuShoot.create().setWorld(level).setThrower(shooter)
                        .setTarget(target).setRandomColor().setRandomType()
                        .setDamage(attackValue * (distanceFactor + 1.2F)).setGravity(0)
                        .setVelocity(speed).setHurtEnderman(hurtEnderman)
                        .setInaccuracy(0.2F).setFanNum(8).setYawTotal(Math.PI / 3)
                        .setImpedingLevel(impedingLevel)
                        .fanShapedShot();
            } else {
                DanmakuShoot.create().setWorld(level).setThrower(shooter)
                        .setTarget(target).setRandomColor().setRandomType()
                        .setDamage(attackValue * (distanceFactor + 1.5F)).setGravity(0)
                        .setVelocity(speed).setHurtEnderman(hurtEnderman)
                        .setInaccuracy(0.2F).setFanNum(32).setYawTotal(2 * Math.PI / 3)
                        .setImpedingLevel(impedingLevel)
                        .fanShapedShot();
            }
            mainHandItem.hurtAndBreak(1, shooter,
                    maid -> maid.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        });
    }

    /**
     * 可见性判定：按配置的弹幕射程（{@code MAID_DANMAKU_RANGE}）过滤目标。
     *
     * <p>上游 1.20.1 的实现就是
     * {@code return IRangedAttackTask.targetConditionsTest(maid, target, MaidConfig.DANMAKU_RANGE);}
     * （用 {@code javap} 核对过字节码）。</p>
     */
    @Override
    public boolean canSee(EntityMaid maid, LivingEntity target) {
        return IRangedAttackTask.targetConditionsTest(maid, target,
                MaidConfig.DANMAKU_RANGE, MaidConfig.danmakuRange());
    }

    @Override
    public AABB searchDimension(EntityMaid maid) {
        if (this.hasGohei(maid)) {
            float radius = this.searchRadius(maid);
            if (maid.hasRestriction()) {
                return new AABB(maid.getRestrictCenter())
                        .inflate(radius, IMaidTask.VERTICAL_SEARCH_RANGE, radius);
            }
            return maid.getBoundingBox().inflate(radius, IMaidTask.VERTICAL_SEARCH_RANGE, radius);
        }
        return IRangedAttackTask.super.searchDimension(maid);
    }

    @Override
    public float searchRadius(EntityMaid maid) {
        return MaidConfig.danmakuRange();
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return Collections.singletonList(Pair.of("has_gohei", this::hasGohei));
    }

    private boolean hasGohei(EntityMaid maid) {
        return ItemHakureiGohei.isGohei(maid.getMainHandItem());
    }

    /**
     * 「这个实体算不算当前交战范围内的敌人」，移植自上游 1.20.1
     * {@code TaskDanmakuAttack#enemyEntityTest}（用 {@code javap -c} 核对过字节码）：
     *
     * <pre>
     * boolean canAttack = maid.canAttack(other);
     * boolean sameType  = target.getType() == other.getType();
     * return canAttack &amp;&amp; sameType &amp;&amp; maid.canSee(other);
     * </pre>
     *
     * <p><b>为什么必须与目标同类型</b>：这个方法的返回值只用于「决定放单发还是扇形」。
     * 1.18.2 的写法是裸 {@code shooter::canAttack}，会把搜索盒（弹幕模式是 64 格）里
     * 所有可攻击生物都算进来 —— 只要附近有僵尸 + 骷髅 + 苦力怕，哪怕女仆只想打其中一只，
     * 计数也会瞬间 &gt; 5，于是「面对单个目标也放 32 发一圈」。加上「同类型」之后，
     * 1 只僵尸就只算 1，才会走单发分支。</p>
     */
    private boolean enemyEntityTest(EntityMaid maid, LivingEntity target, LivingEntity other) {
        boolean canAttack = maid.canAttack(other);
        boolean sameType = target.getType() == other.getType();
        return canAttack && sameType && maid.canSee(other);
    }

    private boolean farAway(LivingEntity target, EntityMaid maid) {
        return maid.distanceTo(target) > MAX_STOP_ATTACK_DISTANCE;
    }
}
