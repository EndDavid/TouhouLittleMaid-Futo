package com.github.tartaricacid.touhoulittlemaid.entity.task.meal;

import com.github.tartaricacid.touhoulittlemaid.api.task.meal.IMaidMeal;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.favorability.Type;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/**
 * 默认的家庭餐（上游 {@code DefaultMaidHomeMeal}）—— 女仆坐在野餐垫上吃的那一顿。
 *
 * <p>与 {@link DefaultMaidWorkMeal} 的区别只在好感度换算：</p>
 * <ul>
 *     <li>工作餐是「每次 0 或 1 点」，掷一次 100 面骰；</li>
 *     <li>家庭餐把食物<b>整份营养</b>折算成点数 ——
 *         {@code point = round(营养 + 营养 × 饱和度系数 × 2) / 15}，
 *         余数部分再按概率补 1 点。所以熟牛肉（8 + 12.8 = 20.8）稳定给 1~2 点，
 *         迷之炖菜（34.2）能给到 2 点。</li>
 * </ul>
 * <p>{@code maid.startUsingItem(hand)} 同样是「真正吃下去」的唯一开关，与工作餐一致。</p>
 */
public class DefaultMaidHomeMeal implements IMaidMeal {
    private static final int MAX_PROBABILITY = 15;

    @Override
    public boolean canMaidEat(EntityMaid maid, ItemStack stack, InteractionHand hand) {
        return stack.isEdible()
                && !IMaidMeal.isBlockList(stack, MaidConfig.homeMealsBlockList())
                && !MaidMealManager.isRegexBlockList(stack, MaidConfig.homeMealsBlockListRegex());
    }

    @Override
    public void onMaidEat(EntityMaid maid, ItemStack stack, InteractionHand hand) {
        // 1.18.2 只有 Item#getFoodProperties()（无 entity 参数版本），
        // 带 LivingEntity 参数的重载到 1.19 才出现。
        FoodProperties foodProperties = stack.getItem().getFoodProperties();
        if (foodProperties == null) {
            return;
        }
        maid.startUsingItem(hand);
        int nutrition = foodProperties.getNutrition();
        float saturationModifier = foodProperties.getSaturationModifier();
        float total = nutrition + nutrition * saturationModifier * 2;
        // 原版的熟牛肉之类的一般在 20 左右（除了迷之炖菜为 34.2）
        int point = Math.round(total) / MAX_PROBABILITY;
        float tailPoint = total - point * MAX_PROBABILITY;
        if (0 < tailPoint && maid.getRandom().nextInt(MAX_PROBABILITY) < tailPoint) {
            point = point + 1;
        }
        maid.getFavorabilityManager().apply(Type.HOME_MEAL, point);
        if (point > 0) {
            maid.spawnHeartParticles(stack.getUseDuration());
        }
    }
}

