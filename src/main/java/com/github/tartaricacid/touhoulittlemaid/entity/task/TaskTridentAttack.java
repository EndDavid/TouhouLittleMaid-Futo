package com.github.tartaricacid.touhoulittlemaid.entity.task;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.task.IRangedAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidAttackTridentTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidRangedWalkToTarget;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidTridentTargetTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.util.SoundUtil;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 三叉戟投掷模式，移植自上游 {@code entity/task/TaskTridentAttack}。
 *
 * <p>与上游的差异：射程读的是配置 {@code MaidConfig.TRIDENT_RANGE}（默认 48，范围 8~192），
 * 配置系统尚未移植，因此这里用同名常量 {@link #TRIDENT_RANGE} 表示同样的默认值。</p>
 */
public class TaskTridentAttack implements IRangedAttackTask {
    public static final ResourceLocation UID = new ResourceLocation(TouhouLittleMaid.MOD_ID, "trident_attack");
    /**
     * 三叉戟的搜索 / 攻击距离（上游配置项 {@code TridentRange} 的默认值）。
     */
    public static final int TRIDENT_RANGE = 48;

    /**
     * 实际使用值（读配置 {@code MaidConfig.TRIDENT_RANGE}，默认 48）。
     */
    private static int tridentRange() {
        return com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig.tridentRange();
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return Items.TRIDENT.getDefaultInstance();
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return SoundUtil.attackSound(maid, InitSounds.MAID_RANGE_ATTACK, 0.5f);
    }

    @Override
    public List<Pair<Integer, Behavior<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        Behavior<EntityMaid> supplementedTask = new StartAttacking<>(
                this::hasTrident, IRangedAttackTask::findFirstValidAttackTarget);
        Behavior<EntityMaid> findTargetTask = new StopAttackingIfTargetInvalid<>(
                target -> !hasTrident(maid) || farAway(target, maid));
        Behavior<EntityMaid> moveToTargetTask = new MaidRangedWalkToTarget(0.6f);
        Behavior<EntityMaid> maidAttackStrafingTask = new MaidAttackTridentTask();
        Behavior<EntityMaid> shootTargetTask = new MaidTridentTargetTask();

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
                this::hasTrident, IRangedAttackTask::findFirstValidAttackTarget);
        Behavior<EntityMaid> findTargetTask = new StopAttackingIfTargetInvalid<>(
                target -> !hasTrident(maid) || farAway(target, maid));
        Behavior<EntityMaid> shootTargetTask = new MaidTridentTargetTask();

        return Lists.newArrayList(
                Pair.of(5, supplementedTask),
                Pair.of(5, findTargetTask),
                Pair.of(5, shootTargetTask)
        );
    }

    /**
     * 上游用 {@code TargetingConditions.forCombat().range(配置)} 做判定；
     * 配置尚未移植，这里等价地写成「有视线 + 在射程内」。
     */
    @Override
    public boolean canSee(EntityMaid maid, LivingEntity target) {
        return maid.hasLineOfSight(target) && maid.distanceTo(target) <= this.searchRadius(maid);
    }

    @Override
    public AABB searchDimension(EntityMaid maid) {
        if (hasTrident(maid)) {
            float searchRange = this.searchRadius(maid);
            if (maid.hasRestriction()) {
                return new AABB(maid.getRestrictCenter()).inflate(searchRange);
            } else {
                return maid.getBoundingBox().inflate(searchRange);
            }
        }
        return IRangedAttackTask.super.searchDimension(maid);
    }

    @Override
    public float searchRadius(EntityMaid maid) {
        return tridentRange();
    }

    @Override
    public void performRangedAttack(EntityMaid shooter, LivingEntity target, float distanceFactor) {
        ItemStack tridentItem = shooter.getMainHandItem().copy();

        // 去除忠诚附魔，不然三叉戟返回来时发现主人不是玩家，就翻脸不认人了不给捡了
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(tridentItem);
        if (!enchantments.isEmpty() && enchantments.containsKey(Enchantments.LOYALTY)) {
            enchantments.remove(Enchantments.LOYALTY);
            EnchantmentHelper.setEnchantments(enchantments, tridentItem);
        }

        // TODO：伤害和好感度挂钩
        ThrownTrident thrownTrident = new ThrownTrident(shooter.level, shooter, tridentItem);
        double x = target.getX() - shooter.getX();
        double y = target.getEyeY() - shooter.getEyeY();
        double z = target.getZ() - shooter.getZ();

        // 依据距离调整三叉戟速度和不准确度
        float distance = shooter.distanceTo(target);
        float velocity = Mth.clamp(distance / 10f, 1.6f, 3.2f);
        float inaccuracy = 1 - Mth.clamp(distance / 100f, 0, 0.9f);

        // 射出的三叉戟忽略重力，从而能让女仆百发百中
        thrownTrident.setNoGravity(true);
        thrownTrident.shoot(x, y, z, velocity, inaccuracy);
        // 不允许任何人捡起
        thrownTrident.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;

        shooter.getMainHandItem().hurtAndBreak(1, shooter, maid -> maid.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        shooter.level.addFreshEntity(thrownTrident);
        shooter.playSound(SoundEvents.TRIDENT_THROW, 1.0F, 1.0F);
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return Lists.newArrayList(Pair.of("has_trident", this::hasTrident));
    }

    private boolean farAway(LivingEntity target, EntityMaid maid) {
        return maid.distanceTo(target) > this.searchRadius(maid);
    }

    private boolean hasTrident(EntityMaid maid) {
        return maid.getMainHandItem().getItem() instanceof TridentItem;
    }
}

