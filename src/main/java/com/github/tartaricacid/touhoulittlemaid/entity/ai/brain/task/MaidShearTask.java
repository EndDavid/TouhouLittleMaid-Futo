package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;

/**
 * 剪羊毛 / 挤蘑菇，移植自上游 {@code MaidShearTask}。
 *
 * <p>与上游的差异：上游判定的是 Forge 的 {@code IForgeShearable}（Forge 给
 * {@code Sheep} / {@code MushroomCow} 打的补丁接口）。<b>好消息是 1.18.2 原版就有
 * {@link Shearable} 接口</b>，羊与哞菇都实现了它，因此这里直接判定原版接口，
 * 判定范围与 Forge 完全一致。</p>
 *
 * <p>掉落物交给实体自己处理（{@code Shearable#shear} 内部会
 * {@code spawnAtLocation}），所以本类不再手动生成掉落物 —— 上游之所以手动生成，
 * 是因为 {@code IForgeShearable#onSheared} 只<b>返回</b>掉落物而不生成。
 * 两者最终效果相同。</p>
 */
public class MaidShearTask extends MaidCheckRateTask {
    private static final int MAX_DELAY_TIME = 12;
    private final float speedModifier;
    private LivingEntity shearableEntity = null;

    public MaidShearTask(float speedModifier) {
        super(ImmutableMap.of(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
        this.speedModifier = speedModifier;
        this.setMaxCheckRate(MAX_DELAY_TIME);
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        ItemStack mainHandItem = maid.getMainHandItem();
        shearableEntity = null;

        if (!(mainHandItem.getItem() instanceof ShearsItem)) {
            return;
        }

        this.getEntities(maid)
                .find(this::isShearable)
                .filter(Entity::isAlive)
                .filter(e -> maid.isWithinRestriction(e.blockPosition()))
                .filter(maid::canPathReach)
                .findFirst()
                .ifPresent(e -> {
                    shearableEntity = e;
                    BehaviorUtils.setWalkAndLookTargetMemories(maid, e, this.speedModifier, 0);
                });

        if (shearableEntity != null && shearableEntity.closerThan(maid, 2)) {
            ((Shearable) shearableEntity).shear(SoundSource.PLAYERS);
            maid.swing(InteractionHand.MAIN_HAND);
            mainHandItem.hurtAndBreak(1, maid, entityMaid -> entityMaid.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            shearableEntity = null;
        }
    }

    private boolean isShearable(LivingEntity entity) {
        return entity instanceof Shearable shearable && shearable.readyForShearing();
    }

    private NearestVisibleLivingEntities getEntities(EntityMaid maid) {
        return maid.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                .orElse(NearestVisibleLivingEntities.empty());
    }
}

