package com.github.tartaricacid.touhoulittlemaid.mixin;

import net.minecraft.core.Registry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 把 {@link Enchantment#canEnchant(ItemStack)} 与附魔台的候选列表对齐 ——
 * 等价于 Forge 的 {@code Enchantment#canApplyAtEnchantingTable} 补丁。
 */
@Mixin(EnchantmentHelper.class)
public class EnchantmentHelperMixin {
    @Inject(method = "getAvailableEnchantmentResults", at = @At("RETURN"))
    private static void tlm$respectCanEnchant(int level, ItemStack stack, boolean allowTreasure,
                                              CallbackInfoReturnable<List<EnchantmentInstance>> cir) {
        List<EnchantmentInstance> result = cir.getReturnValue();
        if (result == null) {
            return;
        }
        // 减法：分类说行、附魔自己说不行
        result.removeIf(instance -> instance.enchantment.category.canEnchant(stack.getItem())
                && !instance.enchantment.canEnchant(stack));
        // 加法：分类说不行、附魔自己说行。书本已被原版全量放行，跳过以免重复。
        if (stack.is(Items.BOOK)) {
            return;
        }
        for (Enchantment enchantment : Registry.ENCHANTMENT) {
            if (enchantment.isTreasureOnly() && !allowTreasure) {
                continue;
            }
            if (!enchantment.isDiscoverable()) {
                continue;
            }
            if (enchantment.category.canEnchant(stack.getItem())) {
                continue;
            }
            if (!enchantment.canEnchant(stack)) {
                continue;
            }
            for (int lv = enchantment.getMaxLevel(); lv >= enchantment.getMinLevel(); lv--) {
                if (level >= enchantment.getMinCost(lv) && level <= enchantment.getMaxCost(lv)) {
                    result.add(new EnchantmentInstance(enchantment, lv));
                    break;
                }
            }
        }
    }
}
