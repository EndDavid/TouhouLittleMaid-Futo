package com.github.tartaricacid.touhoulittlemaid.api.task.meal;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 女仆能吃的东西（上游 {@code api/task/meal/IMaidMeal}）。
 *
 * <p>上游把这个接口做成可扩展点：模组 / 附属包可以注册新的 {@code IMaidMeal}
 * 来让女仆吃下非食物类物品，或者改变「吃」带来的收益。
 * Fabric 侧没有 Forge 的事件总线，{@link com.github.tartaricacid.touhoulittlemaid.entity.task.meal.MaidMealManager}
 * 保留同样的 {@code addMaidMeal} 入口，只是暂时没有外部调用者。</p>
 */
public interface IMaidMeal {
    /**
     * 物品是否在黑名单里（按注册名精确匹配）。
     *
     * <p>上游用 {@code ForgeRegistries.ITEMS.getKey}，这里换成 {@code Registry.ITEM}；
     * 取不到注册名时<b>判定为黑名单</b>（宁可不让女仆吃，也不要吃出意外）。</p>
     */
    static boolean isBlockList(ItemStack food, List<String> blockList) {
        ResourceLocation key = Registry.ITEM.getKey(food.getItem());
        return key == null || blockList.contains(key.toString());
    }

    /**
     * 女仆能否吃下这个物品
     */
    boolean canMaidEat(EntityMaid maid, ItemStack stack, InteractionHand hand);

    /**
     * 女仆吃物品时的行为
     */
    void onMaidEat(EntityMaid maid, ItemStack stack, InteractionHand hand);
}

