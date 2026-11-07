package com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.meal.IMaidMeal;
import com.github.tartaricacid.touhoulittlemaid.api.task.meal.MaidMealType;
import com.github.tartaricacid.touhoulittlemaid.entity.favorability.FavorabilityManager;
import com.github.tartaricacid.touhoulittlemaid.entity.favorability.Type;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.meal.MaidMealManager;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.wrapper.RangedWrapper;

import java.util.List;

/**
 * 工作餐：工作时间里，女仆会自己找吃的（上游 {@code MaidWorkMealTask}）。
 *
 * <p>触发条件三条，缺一不可：</p>
 * <ol>
 *     <li>当前工作模式允许吃饭（{@code IMaidTask#enableEating}，默认 true）——
 *         上游留这个开关是给「战斗中不该停下来吃」这类模式用的；</li>
 *     <li>没在睡觉；</li>
 *     <li>工作餐类型的冷却已经走完（{@code Type.WORK_MEAL} 是 2 分钟）。</li>
 * </ol>
 *
 * <p>流程与上游逐行对应：</p>
 * <ol>
 *     <li>先看<b>手上</b>拿着的东西能不能吃，能就直接吃；</li>
 *     <li>否则挑一只空手（优先主手，都满则用副手），从<b>背包</b>里取一份能吃的换到手上，
 *         原本手上的东西存进 {@code hideInv}（吃完 {@code completeUsingItem} 时会还回来）；</li>
 *     <li>换完再吃。</li>
 * </ol>
 */
public class MaidWorkMealTask extends MaidCheckRateTask {
    private static final int MAX_DELAY_TIME = 50;

    public MaidWorkMealTask() {
        super(ImmutableMap.of());
        this.setMaxCheckRate(MAX_DELAY_TIME);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel serverLevel, EntityMaid maid) {
        if (super.checkExtraStartConditions(serverLevel, maid)) {
            String workMealTypeName = Type.WORK_MEAL.getTypeName();
            FavorabilityManager manager = maid.getFavorabilityManager();
            return !maid.isSleeping() && maid.getTask().enableEating(maid) && manager.canAdd(workMealTypeName);
        }
        return false;
    }

    @Override
    protected void start(ServerLevel serverLevel, EntityMaid maid, long gameTime) {
        List<IMaidMeal> maidMeals = MaidMealManager.getMaidMeals(MaidMealType.WORK_MEAL);

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
                    // 原本手上的东西先藏起来，吃完（completeUsingItem）会自动还回手上
                    maid.memoryHandItemStack(handStack);
                    itemInHand = maid.getItemInHand(eanHand);
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

