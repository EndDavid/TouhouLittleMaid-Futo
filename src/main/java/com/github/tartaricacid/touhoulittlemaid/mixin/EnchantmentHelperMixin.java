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
 *
 * <h2>为什么需要</h2>
 * <p>原版 {@code EnchantmentHelper#getAvailableEnchantmentResults} 的判定是：</p>
 * <pre>{@code
 * if (enchantment.category.canEnchant(stack.getItem()) || stack.is(Items.BOOK)) { ... }
 * }</pre>
 * <p>它读的是<b>分类</b>，而不是附魔自己的 {@code canEnchant}。本模组的三个附魔
 * （阻碍 / 极速 / 末影终结者）没有专属分类 —— {@code EnchantmentCategory#create}
 * 是 Forge 补丁，1.18.2 原版没有，而枚举常量在类初始化时定死、无法从外部扩展
 * （详见 {@code InitEnchantments} 的类注释）—— 只能登记在
 * {@link net.minecraft.world.item.enchantment.EnchantmentCategory#WEAPON} 下，
 * 再各自重写 {@code canEnchant} 只认御币。</p>
 *
 * <p>于是候选列表的两个方向都是错的，这里各补一刀：</p>
 * <ol>
 *     <li><b>减法</b>：分类允许、但附魔自己不同意的条目剔除。
 *         否则剑 / 斧也会被列出这三个附魔。</li>
 *     <li><b>加法</b>：分类拒绝、但附魔自己同意的条目补上。
 *         否则「把御币放进附魔台」永远只能刷出耐久（那是 {@code BREAKABLE} 分类的）。</li>
 * </ol>
 *
 * <h2>为什么加法是安全的</h2>
 * <p>加法只对「分类判否 且 {@code canEnchant} 判真」的附魔生效。原版附魔的
 * {@code canEnchant} 就是 {@code category.canEnchant(stack.getItem())}，两个条件不可能
 * 一假一真，所以这套逻辑对<b>任何原版附魔都是空操作</b>。</p>
 *
 * <p>书本特例单独跳过：{@code stack} 是 {@code minecraft:book} 时原版已经把所有附魔
 * 都放进候选表了，再补一遍会重复。</p>
 *
 * <p>等级选取循环逐行对齐原版字节码（{@code 96 → 103 → 115 → 126 → 137/158} 那段）：
 * 从 {@code getMaxLevel()} 往下试到 {@code getMinLevel()}，第一个落在
 * {@code [getMinCost(lv), getMaxCost(lv)]} 区间内的等级胜出，每个附魔最多给一个
 * {@link EnchantmentInstance}。</p>
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
