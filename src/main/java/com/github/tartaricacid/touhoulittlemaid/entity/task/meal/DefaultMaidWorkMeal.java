package com.github.tartaricacid.touhoulittlemaid.entity.task.meal;

import com.github.tartaricacid.touhoulittlemaid.api.task.meal.IMaidMeal;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.favorability.Type;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/**
 * 默认的工作餐（上游 {@code DefaultMaidWorkMeal}）。
 *
 * <p><b>这就是「女仆吃东西」的唯一正常入口。</b>它的 {@code onMaidEat} 里那一句
 * {@code maid.startUsingItem(hand)} 才让女仆进入「使用物品」状态，
 * 于是：</p>
 * <ul>
 *     <li>原版进食流程开始走：{@code useItemRemaining} 每 tick 递减，到 0 时
 *         {@code completeUsingItem()} → {@code finishUsingItem()} → {@code eat()}，
 *         食物被真正吃掉、药水效果与营养生效；</li>
 *     <li>动画层 {@code predicateUse} 看到 {@code isUsingItem()} 为真，
 *         播放 {@code use_mainhand:eat}（Gecko 模型）或单臂抬起姿态（旧版模型）。</li>
 * </ul>
 *
 * <p>没有这条路径时女仆永远不会 {@code startUsingItem}，吃东西的动画一次都不会播。</p>
 */
public class DefaultMaidWorkMeal implements IMaidMeal {
    private static final int MAX_PROBABILITY = 100;

    @Override
    public boolean canMaidEat(EntityMaid maid, ItemStack stack, InteractionHand hand) {
        return stack.isEdible()
                && !IMaidMeal.isBlockList(stack, MaidConfig.workMealsBlockList())
                && !MaidMealManager.isRegexBlockList(stack, MaidConfig.workMealsBlockListRegex());
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
        int point = maid.getRandom().nextInt(MAX_PROBABILITY) < total ? 0 : 1;
        maid.getFavorabilityManager().apply(Type.WORK_MEAL, point);
        if (point == 1) {
            maid.spawnHeartParticles(stack.getUseDuration());
        }
    }
}
