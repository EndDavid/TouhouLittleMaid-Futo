package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;

/**
 * 喂养动物（让它们进入繁殖状态），移植自上游 {@code MaidFeedAnimalTask}。
 *
 * <p>只会喂「已成年 + 可以求爱 + 手上有它爱吃的食物」的动物；当工作范围内的
 * 动物数量超过上限时就不再喂，避免无限繁殖卡顿。</p>
 *
 * <p>动物数量超上限时弹一句内部提示气泡
 * （{@code chat_bubble.touhou_little_maid.inner.feed_animal.max_number}），与上游一致。</p>
 */
public class MaidFeedAnimalTask extends MaidCheckRateTask {
    private static final int MAX_DELAY_TIME = 12;
    private final float speedModifier;
    private final int maxAnimalCount;
    private Animal feedEntity = null;

    public MaidFeedAnimalTask(float speedModifier, int maxAnimalCount) {
        super(ImmutableMap.of(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
        this.speedModifier = speedModifier;
        this.maxAnimalCount = maxAnimalCount;
        this.setMaxCheckRate(MAX_DELAY_TIME);
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        feedEntity = null;
        long animalCount = this.getEntities(maid)
                .find(e -> maid.isWithinRestriction(e.blockPosition()))
                .filter(Entity::isAlive)
                .filter(e -> e instanceof Animal).count();

        if (animalCount < this.maxAnimalCount) {
            this.getEntities(maid)
                    .find(e -> maid.isWithinRestriction(e.blockPosition()))
                    .filter(Entity::isAlive)
                    .filter(e -> e instanceof Animal)
                    .filter(e -> ((Animal) e).getAge() == 0)
                    .filter(e -> ((Animal) e).canFallInLove())
                    .filter(e -> ItemsUtil.isStackIn(maid.getAvailableInv(false), ((Animal) e)::isFood))
                    .filter(maid::canPathReach)
                    .findFirst()
                    .ifPresent(e -> {
                        feedEntity = (Animal) e;
                        BehaviorUtils.setWalkAndLookTargetMemories(maid, e, this.speedModifier, 0);
                    });

            if (feedEntity != null && feedEntity.closerThan(maid, 2)) {
                ItemStack food = ItemsUtil.getStack(maid.getAvailableInv(false), feedEntity::isFood);
                if (!food.isEmpty()) {
                    food.shrink(1);
                    maid.swing(InteractionHand.MAIN_HAND);
                    feedEntity.setInLove(null);
                    if (maid.getOwner() instanceof ServerPlayer serverPlayer) {
                        InitTrigger.MAID_EVENT.trigger(serverPlayer, TriggerType.MAID_FEED_ANIMAL);
                    }
                }
                feedEntity = null;
            }
        } else {
            // 附近动物数量已达上限：这一轮不再喂，并弹一句提示
            // （上游同位置的那句气泡，属于「数量超限」这一支，不是「没走到动物旁边」）
            com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.ChatBubbleManger.addInnerChatText(maid,
                    "chat_bubble.touhou_little_maid.inner.feed_animal.max_number");
        }
    }

    private NearestVisibleLivingEntities getEntities(EntityMaid maid) {
        return maid.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                .orElse(NearestVisibleLivingEntities.empty());
    }
}

