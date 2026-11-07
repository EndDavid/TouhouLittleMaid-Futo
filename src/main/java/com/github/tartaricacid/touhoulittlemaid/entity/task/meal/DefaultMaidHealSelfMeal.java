package com.github.tartaricacid.touhoulittlemaid.entity.task.meal;

import com.github.tartaricacid.touhoulittlemaid.api.task.meal.IMaidMeal;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/**
 * 默认的回血餐（上游 {@code DefaultMaidHealSelfMeal}）。
 *
 * <p>与工作餐的区别：<b>不加好感度，而是直接回血</b>，且触发条件是
 * 「缺血 ≥ 2 点」（见 {@code MaidHealSelfTask}）。</p>
 *
 * <p>注意它同样调 {@code maid.startUsingItem(hand)} —— 所以缺血时吃东西
 * 也会走完整的进食动画与进食流程，食物照样被消耗。</p>
 */
public class DefaultMaidHealSelfMeal implements IMaidMeal {
    private static final int MAX_PROBABILITY = 5;

    @Override
    public boolean canMaidEat(EntityMaid maid, ItemStack stack, InteractionHand hand) {
        return stack.isEdible()
                && !IMaidMeal.isBlockList(stack, MaidConfig.healMealsBlockList())
                && !MaidMealManager.isRegexBlockList(stack, MaidConfig.healMealsBlockListRegex());
    }

    @Override
    public void onMaidEat(EntityMaid maid, ItemStack stack, InteractionHand hand) {
        FoodProperties foodProperties = stack.getItem().getFoodProperties();
        if (foodProperties == null) {
            return;
        }
        maid.startUsingItem(hand);
        int nutrition = foodProperties.getNutrition();
        float saturationModifier = foodProperties.getSaturationModifier();
        float total = nutrition + nutrition * saturationModifier * 2;
        // 原版的熟牛肉之类的一般在 20 左右（除了迷之炖菜为 34.2）
        if (maid.getRandom().nextInt(MAX_PROBABILITY) < total) {
            float healCount = Math.max(total / MAX_PROBABILITY, 1);
            maid.heal(healCount);
            maid.spawnHeartParticles(stack.getUseDuration());
        }
    }
}

