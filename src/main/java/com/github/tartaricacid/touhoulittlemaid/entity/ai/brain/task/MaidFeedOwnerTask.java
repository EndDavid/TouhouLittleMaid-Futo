package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.api.task.IFeedTask;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

/**
 * 给主人喂食，移植自上游 {@code MaidFeedOwnerTask}。
 *
 * <p>挑选规则与上游一致：</p>
 * <ul>
 *     <li>先找 {@link IFeedTask.Priority#HIGH}（牛奶解毒、蜂蜜解中毒、恰好填满饥饿值的食物）；</li>
 *     <li>再找 {@link IFeedTask.Priority#LOW}（吃不死但填不满的）；</li>
 *     <li>主人血量低于 50% 时才允许 {@link IFeedTask.Priority#LOWEST}（会浪费的食物）。</li>
 * </ul>
 *
 * <p>与上游的差异：上游喂完会触发进度触发器 {@code MAID_FEED_PLAYER}，
 * 进度系统尚未移植，因此省略。</p>
 */
public class MaidFeedOwnerTask extends MaidCheckRateTask {
    private static final int MAX_DELAY_TIME = 20;
    private final IFeedTask task;
    private final float walkSpeed;
    private final int closeEnoughDist;

    public MaidFeedOwnerTask(IFeedTask task, int closeEnoughDist, float walkSpeed) {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
        this.task = task;
        this.walkSpeed = walkSpeed;
        this.closeEnoughDist = closeEnoughDist;
        this.setMaxCheckRate(MAX_DELAY_TIME);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel worldIn, EntityMaid maid) {
        if (super.checkExtraStartConditions(worldIn, maid)) {
            LivingEntity owner = maid.getOwner();
            if (owner instanceof Player && owner.isAlive() && maid.isWithinRestriction(owner.blockPosition())) {
                if (owner.closerThan(maid, closeEnoughDist)) {
                    return true;
                }
                BehaviorUtils.setWalkAndLookTargetMemories(maid, owner, walkSpeed, 1);
            }
            return false;
        }
        return false;
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        LivingEntity owner = maid.getOwner();
        if (!(owner instanceof Player player) || !player.isAlive()) {
            return;
        }
        boolean dying = player.getHealth() / player.getMaxHealth() < 0.5f;
        IntList lowestFoods = new IntArrayList();
        IntList lowFoods = new IntArrayList();
        IntList highFoods = new IntArrayList();

        CombinedInvWrapper inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); ++i) {
            ItemStack stack = inv.getStackInSlot(i);
            if (this.task.isFood(stack, player)) {
                IFeedTask.Priority priority = this.task.getPriority(stack, player);
                if (priority == IFeedTask.Priority.HIGH) {
                    highFoods.add(i);
                    break;
                }
                if (priority == IFeedTask.Priority.LOW) {
                    lowFoods.add(i);
                    break;
                }
                if (dying && priority == IFeedTask.Priority.LOWEST) {
                    lowestFoods.add(i);
                    break;
                }
            }
        }

        if (highFoods.isEmpty() && lowFoods.isEmpty() && lowestFoods.isEmpty()) {
            return;
        }

        IntList chosen = !highFoods.isEmpty() ? highFoods : !lowFoods.isEmpty() ? lowFoods : lowestFoods;
        chosen.stream().skip(maid.getRandom().nextInt(chosen.size())).findFirst().ifPresent(slot -> {
            inv.setStackInSlot(slot, this.task.feed(inv.getStackInSlot(slot), player));
            maid.swing(InteractionHand.MAIN_HAND);
            this.setNextCheckTickCount(5);
            if (player instanceof ServerPlayer serverPlayer) {
                InitTrigger.MAID_EVENT.trigger(serverPlayer, TriggerType.MAID_FEED_PLAYER);
            }
        });
    }
}

