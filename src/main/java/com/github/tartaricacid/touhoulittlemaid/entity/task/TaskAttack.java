package com.github.tartaricacid.touhoulittlemaid.entity.task;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidUseShieldTask;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityExtinguishingAgent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.util.SoundUtil;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.MeleeAttack;
import net.minecraft.world.entity.ai.behavior.RunIf;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromAttackTargetIfTargetOutOfReach;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * 近战攻击模式，移植自上游 {@code entity/task/TaskAttack}。
 *
 * <p>与上游的差异：上游的「额外攻击」依赖灭火器物品（{@code InitItems.EXTINGUISHER}），
 * 物品系统尚未移植，因此 {@code hasExtraAttack} / {@code doExtraAttack} 暂缺。</p>
 */
public class TaskAttack implements IAttackTask {
    public static final ResourceLocation UID = new ResourceLocation(TouhouLittleMaid.MOD_ID, "attack");
    private static final int MAX_STOP_ATTACK_DISTANCE = 8;

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return Items.DIAMOND_SWORD.getDefaultInstance();
    }

    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return SoundUtil.attackSound(maid, InitSounds.MAID_ATTACK, 0.5f);
    }

    @Override
    public List<Pair<Integer, Behavior<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        RunIf<EntityMaid> supplementedTask = new RunIf<>(this::hasAssaultWeapon,
                new StartAttacking<>(IAttackTask::findFirstValidAttackTarget));
        StopAttackingIfTargetInvalid<EntityMaid> findTargetTask = new StopAttackingIfTargetInvalid<>(
                target -> !this.hasAssaultWeapon(maid) || this.farAway(target, maid));
        SetWalkTargetFromAttackTargetIfTargetOutOfReach moveToTargetTask =
                new SetWalkTargetFromAttackTargetIfTargetOutOfReach(0.6f);
        MeleeAttack attackTargetTask = new MeleeAttack(20);
        MaidUseShieldTask maidUseShieldTask = new MaidUseShieldTask();

        return List.of(
                Pair.of(5, supplementedTask),
                Pair.of(5, findTargetTask),
                Pair.of(5, moveToTargetTask),
                Pair.of(5, attackTargetTask),
                Pair.of(5, maidUseShieldTask)
        );
    }

    @Override
    public boolean hasExtraAttack(EntityMaid maid, Entity target) {
        return maid.getOffhandItem().is(InitItems.EXTINGUISHER) && target.fireImmune();
    }

    /**
     * 副手灭火器对火免疫生物的额外伤害：目标脚下生成一团灭火剂,
     * 灭火剂会反过来对火免疫生物持续造成魔法伤害，并消耗副手 1 点耐久。
     *
     * <p>与上游一致：如果目标附近已经有一团灭火剂就不再叠加，避免浪费耐久。</p>
     */
    @Override
    public boolean doExtraAttack(EntityMaid maid, Entity target) {
        Level world = maid.level;
        AABB aabb = target.getBoundingBox().inflate(1.5, 1, 1.5);
        List<EntityExtinguishingAgent> extinguishingAgents =
                world.getEntitiesOfClass(EntityExtinguishingAgent.class, aabb, Entity::isAlive);
        if (extinguishingAgents.isEmpty()) {
            world.addFreshEntity(new EntityExtinguishingAgent(world, target.position()));
            maid.getOffhandItem().hurtAndBreak(1, maid, m -> m.broadcastBreakEvent(InteractionHand.OFF_HAND));
            return true;
        }
        return false;
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return List.of(Pair.of("assault_weapon", this::hasAssaultWeapon),
                Pair.of("extinguisher", this::hasExtinguisher));
    }

    private boolean hasAssaultWeapon(EntityMaid maid) {
        return maid.getMainHandItem().getAttributeModifiers(EquipmentSlot.MAINHAND)
                .containsKey(Attributes.ATTACK_DAMAGE);
    }

    private boolean hasExtinguisher(EntityMaid maid) {
        return maid.getOffhandItem().is(InitItems.EXTINGUISHER);
    }

    private boolean farAway(LivingEntity target, EntityMaid maid) {
        return maid.distanceTo(target) > MAX_STOP_ATTACK_DISTANCE;
    }
}
