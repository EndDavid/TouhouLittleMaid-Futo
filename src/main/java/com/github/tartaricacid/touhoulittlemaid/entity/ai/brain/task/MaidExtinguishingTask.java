package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityExtinguishingAgent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 灭火行为，移植自上游 {@code MaidExtinguishingTask}。
 *
 * <p>三件事，优先级从高到低：</p>
 * <ol>
 *     <li>主人着火且在范围内 → 跑过去喷射（2 格内才喷，否则先靠近）；</li>
 *     <li>女仆自己着火 → 立刻喷射；</li>
 *     <li>身边 2 格内有着火的驯服生物 → 喷射。</li>
 * </ol>
 *
 * <p>每喷一次扣 1 点耐久、挥一次手。与上游一致，本模式禁用慌乱 AI
 * （见 {@code TaskExtinguishing#enablePanic}），否则她着火后会先逃跑而不是灭火。</p>
 */
public class MaidExtinguishingTask extends MaidCheckRateTask {
    private static final int MAX_DELAY_TIME = 12;
    private final float speed;

    public MaidExtinguishingTask(float speed) {
        super(ImmutableMap.of());
        this.speed = speed;
        this.setMaxCheckRate(MAX_DELAY_TIME);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel worldIn, EntityMaid owner) {
        return super.checkExtraStartConditions(worldIn, owner) && this.isExtinguisher(owner.getMainHandItem());
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        Level world = maid.level;
        LivingEntity owner = maid.getOwner();
        ItemStack mainhandItem = maid.getMainHandItem();
        Brain<EntityMaid> brain = maid.getBrain();

        if (owner instanceof Player && owner.isAlive() && owner.isOnFire() && this.isExtinguisher(mainhandItem)
            && maid.isWithinRestriction(owner.blockPosition())) {
            if (maid.closerThan(owner, 2)) {
                brain.eraseMemory(MemoryModuleType.PATH);
                brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                world.addFreshEntity(new EntityExtinguishingAgent(worldIn, owner.position()));
                mainhandItem.hurtAndBreak(1, maid, m -> m.broadcastBreakEvent(InteractionHand.MAIN_HAND));
                maid.swing(InteractionHand.MAIN_HAND);
            } else {
                BehaviorUtils.setWalkAndLookTargetMemories(maid, owner, this.speed, 2);
            }
        }

        if (maid.isOnFire() && this.isExtinguisher(mainhandItem)) {
            world.addFreshEntity(new EntityExtinguishingAgent(worldIn, maid.position()));
            mainhandItem.hurtAndBreak(1, maid, m -> m.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            maid.swing(InteractionHand.MAIN_HAND);
        }

        List<TamableAnimal> tameableEntities = world.getEntitiesOfClass(TamableAnimal.class,
                maid.getBoundingBox().inflate(2, 1, 2), Entity::isOnFire);
        if (!tameableEntities.isEmpty() && this.isExtinguisher(mainhandItem)) {
            world.addFreshEntity(new EntityExtinguishingAgent(worldIn, maid.position()));
            mainhandItem.hurtAndBreak(1, maid, m -> m.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            maid.swing(InteractionHand.MAIN_HAND);
        }
    }

    private boolean isExtinguisher(ItemStack stack) {
        return stack.getItem() == InitItems.EXTINGUISHER;
    }
}

