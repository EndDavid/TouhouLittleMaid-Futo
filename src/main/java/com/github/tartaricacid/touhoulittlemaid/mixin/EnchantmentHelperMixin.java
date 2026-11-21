package com.github.tartaricacid.touhoulittlemaid.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 让附魔台的候选列表也尊重 {@link Enchantment#canEnchant(ItemStack)} ——
 * 等价于 Forge 的 {@code Enchantment#canApplyAtEnchantingTable} 补丁。
 *
 * <h2>为什么需要</h2>
 * <p>原版 {@code EnchantmentHelper#getAvailableEnchantmentResults} 的判定是：</p>
 * <pre>{@code
 * if (enchantment.category.canEnchant(stack.getItem()) || stack.is(Items.BOOK)) { ... }
 * }</pre>
 * <p>也就是说它读的是<b>分类</b>而不是附魔自己的 {@code canEnchant}。
 * 本模组的三个附魔没有专属分类（{@code EnchantmentCategory#create} 是 Forge 补丁，
 * 见 {@code InitEnchantments} 的类注释），只能登记在 {@link net.minecraft.world.item.enchantment.EnchantmentCategory#WEAPON} 下，
 * 于是剑 / 斧也会被列出「阻碍 / 极速 / 末影终结者」—— 明显是错的。</p>
 *
 * <h2>怎么补</h2>
 * <p>只做减法：凡是「分类允许、但附魔自己不同意」的条目一律剔除。
 * 对原版附魔没有任何影响（它们的 {@code canEnchant} 就是 {@code category.canEnchant}，
 * 两个条件不会一真一假），因此这刀是精确的。</p>
 *
 * <p>书本特例不受影响：{@code stack} 是 {@code minecraft:book} 时
 * {@code category.canEnchant(book)} 为 {@code false}，第一条判据就不成立，
 * 村民的附魔书交易照常能抽到这三个附魔。</p>
 *
 * <p>代价是「把御币放进附魔台」也刷不出来（上游 Forge 因为有 {@code GOHEI} 分类而刷得出来）。
 * 这里不补「加法」：那要复制原版计算等级区间的那段循环，一旦原版改动就会静默走偏。</p>
 */
@Mixin(EnchantmentHelper.class)
public class EnchantmentHelperMixin {
    @Inject(method = "getAvailableEnchantmentResults", at = @At("RETURN"))
    private static void tlm$respectCanEnchant(int level, ItemStack stack, boolean allowTreasure,
                                              CallbackInfoReturnable<List<EnchantmentInstance>> cir) {
        List<EnchantmentInstance> result = cir.getReturnValue();
        if (result == null || result.isEmpty()) {
            return;
        }
        result.removeIf(instance -> instance.enchantment.category.canEnchant(stack.getItem())
                && !instance.enchantment.canEnchant(stack));
    }
}

