package com.github.tartaricacid.touhoulittlemaid.entity.task;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.task.IRangedAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidAttackStrafingTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidShootTargetTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.github.tartaricacid.touhoulittlemaid.util.SoundUtil;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.RunIf;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidRangedWalkToTarget;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromAttackTargetIfTargetOutOfReach;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * 弓箭远程攻击模式，移植自上游 {@code entity/task/TaskBowAttack}。
 *
 * <p>与上游的差异只有一处：上游在 {@code getArrow} 里调用 {@code BowItem#customArrow}
 * 让子类武器（例如箭矢转化类物品）改写箭实体，这个方法在 1.18.2 还不存在
 * （Mojang 在 1.19 才加入），因此这里直接跳过。</p>
 */
public class TaskBowAttack implements IRangedAttackTask {
    public static final ResourceLocation UID = new ResourceLocation(TouhouLittleMaid.MOD_ID, "ranged_attack");
    private static final int MAX_STOP_ATTACK_DISTANCE = 16;

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return Items.BOW.getDefaultInstance();
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return SoundUtil.attackSound(maid, InitSounds.MAID_RANGE_ATTACK, 0.5f);
    }

    @Override
    public List<Pair<Integer, Behavior<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        RunIf<EntityMaid> supplementedTask = new RunIf<>(e -> hasBow(e) && hasArrow(e),
                new StartAttacking<>(IRangedAttackTask::findFirstValidAttackTarget));
        StopAttackingIfTargetInvalid<EntityMaid> findTargetTask = new StopAttackingIfTargetInvalid<>(
                target -> !hasBow(maid) || !hasArrow(maid) || farAway(target, maid));
        Behavior<EntityMaid> moveToTargetTask =
                new MaidRangedWalkToTarget(0.6F);
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
                e -> hasBow(e) && hasArrow(e), IRangedAttackTask::findFirstValidAttackTarget);
        Behavior<EntityMaid> findTargetTask = new StopAttackingIfTargetInvalid<>(
                target -> !hasBow(maid) || !hasArrow(maid) || farAway(target, maid));
        Behavior<EntityMaid> shootTargetTask = new MaidShootTargetTask(2);

        return Lists.newArrayList(
                Pair.of(5, supplementedTask),
                Pair.of(5, findTargetTask),
                Pair.of(5, shootTargetTask)
        );
    }

    @Override
    public void performRangedAttack(EntityMaid shooter, LivingEntity target, float distanceFactor) {
        AbstractArrow entityArrow = getArrow(shooter, distanceFactor);
        if (entityArrow == null) {
            return;
        }
        ItemStack mainHandItem = shooter.getMainHandItem();
        if (!(mainHandItem.getItem() instanceof BowItem)) {
            return;
        }
        double x = target.getX() - shooter.getX();
        double y = target.getBoundingBox().minY + target.getBbHeight() / 3.0F - entityArrow.position().y;
        double z = target.getZ() - shooter.getZ();
        double pitch = Math.sqrt(x * x + z * z) * 0.15D;
        entityArrow.shoot(x, y + pitch, z, 1.6F, 1);
        mainHandItem.hurtAndBreak(1, shooter, maid -> maid.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        shooter.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (shooter.getRandom().nextFloat() * 0.4F + 0.8F));
        shooter.level.addFreshEntity(entityArrow);
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return Lists.newArrayList(Pair.of("has_bow", this::hasBow), Pair.of("has_arrow", this::hasArrow));
    }

    private boolean hasBow(EntityMaid maid) {
        return maid.getMainHandItem().getItem() instanceof BowItem;
    }

    private boolean hasArrow(EntityMaid maid) {
        return findArrow(maid) >= 0;
    }

    private int findArrow(EntityMaid maid) {
        ItemStack mainHandItem = maid.getMainHandItem();
        if (mainHandItem.getItem() instanceof BowItem bowItem) {
            CombinedInvWrapper handler = maid.getAvailableInv(true);
            return ItemsUtil.findStackSlot(handler, bowItem.getAllSupportedProjectiles());
        }
        return -1;
    }

    @Nullable
    private AbstractArrow getArrow(EntityMaid maid, float chargeTime) {
        int slot = findArrow(maid);
        if (slot < 0) {
            return null;
        }

        CombinedInvWrapper handler = maid.getAvailableInv(true);
        ItemStack arrowStack = handler.getStackInSlot(slot);
        ItemStack mainHandItem = maid.getMainHandItem();
        AbstractArrow arrowEntity = ProjectileUtil.getMobArrow(maid, arrowStack, chargeTime);

        // 无限附魔不存在或者小于 0 时，真的要消耗一支箭
        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, mainHandItem) <= 0) {
            arrowStack.shrink(1);
            handler.setStackInSlot(slot, arrowStack);
            // 记得把箭设置为可以拾起状态
            arrowEntity.pickup = AbstractArrow.Pickup.ALLOWED;
        }

        // 箭伤害也和好感度挂钩
        AttributeInstance attackDamage = maid.getAttribute(Attributes.ATTACK_DAMAGE);
        double attackValue = 2.0;
        if (attackDamage != null) {
            attackValue = attackDamage.getBaseValue();
        }
        float multiplier = (float) (attackValue / 2.0f);
        arrowEntity.setBaseDamage(arrowEntity.getBaseDamage() * multiplier);

        return arrowEntity;
    }

    private boolean farAway(LivingEntity target, EntityMaid maid) {
        return maid.distanceTo(target) > MAX_STOP_ATTACK_DISTANCE;
    }
}

