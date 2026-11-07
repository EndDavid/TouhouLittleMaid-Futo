package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.meal.IMaidMeal;
import com.github.tartaricacid.touhoulittlemaid.api.task.meal.MaidMealType;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.meal.MaidMealManager;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.wrapper.RangedWrapper;

import java.util.List;

/**
 * 回血餐：缺血时自己找东西吃（上游 {@code MaidHealSelfTask}）。
 *
 * <p>这条行为挂在 <b>CORE</b>（优先级 3），也就是说它<b>不挑活动</b> ——
 * 工作 / 空闲 / 休息时缺血都会触发，因为「快死了先吃点」比手头的事更要紧。</p>
 *
 * <p>与工作餐的区别：这里没有好感度冷却（{@code Type.HEAL_MEAL} 只是注册了表项，
 * 上游也没在 {@code apply} 里用它），触发条件直接是「缺失生命 ≥ 2」；
 * 每次检查间隔 50 tick，所以不会出现「一掉血就停下手里的事」。</p>
 *
 * <p>{@code MAX_CHECK_MISSING_HEATH = 2} 上游留了 TODO（想用配置管控），这里照抄常量。</p>
 */
public class MaidHealSelfTask extends MaidCheckRateTask {
    private static final int MAX_DELAY_TIME = 50;
    private static final int MAX_CHECK_MISSING_HEATH = 2;

    public MaidHealSelfTask() {
        super(ImmutableMap.of());
        this.setMaxCheckRate(MAX_DELAY_TIME);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel serverLevel, EntityMaid maid) {
        if (super.checkExtraStartConditions(serverLevel, maid)) {
            float missingHealth = maid.getMaxHealth() - maid.getHealth();
            return !maid.isSleeping() && maid.getTask().enableEating(maid)
                    && missingHealth >= MAX_CHECK_MISSING_HEATH;
        }
        return false;
    }

    @Override
    protected void start(ServerLevel serverLevel, EntityMaid maid, long gameTime) {
        List<IMaidMeal> maidMeals = MaidMealManager.getMaidMeals(MaidMealType.HEAL_MEAL);

        // 先查询手部的物品能否吃：能就直接开吃，否就进行后续工作
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack itemInHand = maid.getItemInHand(hand);
            if (itemInHand.isEmpty()) {
                continue;
            }
            for (IMaidMeal maidMeal : maidMeals) {
                if (maidMeal.canMaidEat(maid, itemInHand, hand)) {
                    maidMeal.onMaidEat(maid, itemInHand, hand);
                    return;
                }
            }
        }

        // 对手部进行处理：如果有空的手，就用它；两只手都占着就抢副手
        InteractionHand eanHand = InteractionHand.OFF_HAND;
        for (InteractionHand hand : InteractionHand.values()) {
            if (maid.getItemInHand(hand).isEmpty()) {
                eanHand = hand;
                break;
            }
        }
        ItemStack itemInHand = maid.getItemInHand(eanHand);

        // 尝试在背包中寻找食物放入
        boolean hasFood = false;
        RangedWrapper backpackInv = maid.getAvailableBackpackInv();
        swapItemCheck:
        for (int i = 0; i < backpackInv.getSlots(); i++) {
            ItemStack stack = backpackInv.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            for (IMaidMeal maidMeal : maidMeals) {
                if (maidMeal.canMaidEat(maid, stack, eanHand)) {
                    ItemStack foodStack = backpackInv.extractItem(i, backpackInv.getStackInSlot(i).getCount(), false);
                    ItemStack handStack = itemInHand.copy();
                    maid.setItemInHand(eanHand, foodStack);
                    itemInHand = maid.getItemInHand(eanHand);
                    maid.memoryHandItemStack(handStack);
                    hasFood = true;
                    break swapItemCheck;
                }
            }
        }

        // 开吃
        if (hasFood) {
            for (IMaidMeal maidMeal : maidMeals) {
                if (maidMeal.canMaidEat(maid, itemInHand, eanHand)) {
                    maidMeal.onMaidEat(maid, itemInHand, eanHand);
                    return;
                }
            }
        }
    }
}

