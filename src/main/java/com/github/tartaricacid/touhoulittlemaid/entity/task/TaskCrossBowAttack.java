package com.github.tartaricacid.touhoulittlemaid.entity.task;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.task.IRangedAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidAttackStrafingTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCrossbowAttack;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.github.tartaricacid.touhoulittlemaid.util.SoundUtil;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidRangedWalkToTarget;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromAttackTargetIfTargetOutOfReach;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * 弩远程攻击模式，移植自上游 {@code entity/task/TaskCrossBowAttack}。
 *
 * <p>拉弩 / 装填 / 发射全部交给原版 {@code CrossbowAttack} 行为（女仆实现了
 * {@code CrossbowAttackMob}），本类只负责「有没有弩、有没有弹药」的判定。
 * 弹药从「双手 + 背包」里找，装填动作走
 * {@code EntityMaid#getProjectile}。</p>
 */
public class TaskCrossBowAttack implements IRangedAttackTask {
    public static final ResourceLocation UID = new ResourceLocation(TouhouLittleMaid.MOD_ID, "crossbow_attack");
    private static final int MAX_STOP_ATTACK_DISTANCE = 16;

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return Items.CROSSBOW.getDefaultInstance();
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return SoundUtil.attackSound(maid, InitSounds.MAID_RANGE_ATTACK, 0.5f);
    }

    @Override
    public List<Pair<Integer, Behavior<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        Behavior<EntityMaid> supplementedTask = new StartAttacking<>(
                entityMaid -> hasCrossBow(entityMaid) && hasAmmunition(entityMaid),
                IRangedAttackTask::findFirstValidAttackTarget);
        Behavior<EntityMaid> findTargetTask = new StopAttackingIfTargetInvalid<>(
                target -> !hasCrossBow(maid) || !hasAmmunition(maid) || farAway(target, maid));
        Behavior<EntityMaid> moveToTargetTask = new MaidRangedWalkToTarget(0.6F);
        Behavior<EntityMaid> maidAttackStrafingTask = new MaidAttackStrafingTask();
        Behavior<EntityMaid> shootTargetTask = new MaidCrossbowAttack();

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
                entityMaid -> hasCrossBow(entityMaid) && hasAmmunition(entityMaid),
                IRangedAttackTask::findFirstValidAttackTarget);
        Behavior<EntityMaid> findTargetTask = new StopAttackingIfTargetInvalid<>(
                target -> !hasCrossBow(maid) || !hasAmmunition(maid) || farAway(target, maid));
        Behavior<EntityMaid> shootTargetTask = new MaidCrossbowAttack();

        return Lists.newArrayList(
                Pair.of(5, supplementedTask),
                Pair.of(5, findTargetTask),
                Pair.of(5, shootTargetTask)
        );
    }

    @Override
    public void performRangedAttack(EntityMaid shooter, LivingEntity target, float distanceFactor) {
        // 原版 {@code CrossbowAttackMob} 的默认实现：装填完毕后调用一次齐射
        shooter.performCrossbowAttack(shooter, 1.6F);
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return Lists.newArrayList(Pair.of("has_crossbow", this::hasCrossBow),
                Pair.of("has_ammunition", this::hasAmmunition));
    }

    private boolean hasCrossBow(EntityMaid maid) {
        return maid.getMainHandItem().getItem() instanceof CrossbowItem;
    }

    private boolean hasAmmunition(EntityMaid maid) {
        return maid.getOffhandItem().getItem() instanceof FireworkRocketItem || hasArrow(maid);
    }

    private boolean hasArrow(EntityMaid maid) {
        return findArrow(maid) >= 0;
    }

    private int findArrow(EntityMaid maid) {
        ItemStack mainHandItem = maid.getMainHandItem();
        if (mainHandItem.getItem() instanceof CrossbowItem crossbowItem) {
            CombinedInvWrapper handler = maid.getAvailableInv(true);
            return ItemsUtil.findStackSlot(handler, crossbowItem.getAllSupportedProjectiles());
        }
        return -1;
    }

    private boolean farAway(LivingEntity target, EntityMaid maid) {
        return maid.distanceTo(target) > MAX_STOP_ATTACK_DISTANCE;
    }
}

