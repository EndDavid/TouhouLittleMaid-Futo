package com.github.tartaricacid.touhoulittlemaid.entity.task;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFeedAnimalTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.github.tartaricacid.touhoulittlemaid.util.SoundUtil;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.MeleeAttack;
import net.minecraft.world.entity.ai.behavior.RunIf;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromAttackTargetIfTargetOutOfReach;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 喂养动物模式，移植自上游 {@code entity/task/TaskFeedAnimal}。
 *
 * <p>它同时实现 {@link IAttackTask}：动物数量超过上限后，女仆会开始「清减」——
 * 拿武器攻击动物。这是上游的原始设计（避免养太多卡服），不是 bug。</p>
 *
 * <p>与上游的差异：</p>
 * <ul>
 *     <li>动物数量上限读配置 {@code MaidConfig.FEED_ANIMAL_MAX_NUMBER}（默认 15），
 *         配置系统尚未移植，这里用 {@link #FEED_ANIMAL_MAX_NUMBER} 表示同样的默认值。</li>
 *     <li>上游的 {@code getTaskConfigGuiProvider}（给这个上限做滑条）需要容器系统，省略。</li>
 * </ul>
 */
public class TaskFeedAnimal implements IAttackTask {
    public static final ResourceLocation UID = new ResourceLocation(TouhouLittleMaid.MOD_ID, "feed_animal");
    /**
     * 工作范围内允许存在的动物数量上限（上游配置项 {@code FeedAnimalMaxNumber} 的默认值）。
     */
    public static final int FEED_ANIMAL_MAX_NUMBER = 50;

    /**
     * 瀹為檯浣跨敤鍊硷紙璇婚厤缃?{@code MaidConfig.FEED_ANIMAL_MAX_NUMBER}锛岄粯璁?50锛夈€?
     */
    private static int maxAnimalCount() {
        return com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig.feedAnimalMaxNumber();
    }
    private static final int MAX_STOP_ATTACK_DISTANCE = 8;

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return Items.WHEAT.getDefaultInstance();
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return SoundUtil.environmentSound(maid, InitSounds.MAID_FEED_ANIMAL, 0.5f);
    }

    @Override
    public List<Pair<Integer, Behavior<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        RunIf<EntityMaid> supplementedTask = new RunIf<>(this::hasAssaultWeapon,
                new StartAttacking<>(this::findFirstValidAttackTarget));
        StopAttackingIfTargetInvalid<EntityMaid> findTargetTask = new StopAttackingIfTargetInvalid<>(
                target -> !this.hasAssaultWeapon(maid) || this.farAway(target, maid));
        SetWalkTargetFromAttackTargetIfTargetOutOfReach moveToTargetTask =
                new SetWalkTargetFromAttackTargetIfTargetOutOfReach(0.6f);
        MeleeAttack attackTargetTask = new MeleeAttack(20);

        return Lists.newArrayList(
                Pair.of(5, new MaidFeedAnimalTask(0.6f, maxAnimalCount())),
                Pair.of(6, supplementedTask),
                Pair.of(6, findTargetTask),
                Pair.of(6, moveToTargetTask),
                Pair.of(6, attackTargetTask)
        );
    }

    private Optional<? extends LivingEntity> findFirstValidAttackTarget(EntityMaid maid) {
        long animalCount = this.getEntities(maid)
                .find(e -> maid.isWithinRestriction(e.blockPosition()))
                .filter(Entity::isAlive)
                .filter(e -> e instanceof Animal).count();

        if (animalCount < (maxAnimalCount() - 2)) {
            return Optional.empty();
        }

        return this.getEntities(maid)
                .find(e -> maid.isWithinRestriction(e.blockPosition()))
                .filter(Entity::isAlive)
                .filter(e -> e instanceof Animal)
                .filter(e -> ((Animal) e).getAge() == 0)
                .filter(e -> ((Animal) e).canFallInLove())
                .filter(e -> ItemsUtil.isStackIn(maid.getAvailableInv(false), ((Animal) e)::isFood))
                .filter(maid::canPathReach)
                .findFirst();
    }

    /**
     * 本模式下「攻击」的目标永远是动物，因此绕过默认的怪物判定。
     */
    @Override
    public boolean canAttack(EntityMaid maid, LivingEntity target) {
        return true;
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return Lists.newArrayList(Pair.of("can_feed", Predicates.alwaysTrue()),
                Pair.of("assault_weapon", this::hasAssaultWeapon));
    }

    private NearestVisibleLivingEntities getEntities(EntityMaid maid) {
        return maid.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                .orElse(NearestVisibleLivingEntities.empty());
    }

    private boolean hasAssaultWeapon(EntityMaid maid) {
        return maid.getMainHandItem().getAttributeModifiers(EquipmentSlot.MAINHAND)
                .containsKey(Attributes.ATTACK_DAMAGE);
    }

    private boolean farAway(LivingEntity target, EntityMaid maid) {
        return maid.distanceTo(target) > MAX_STOP_ATTACK_DISTANCE;
    }
}

