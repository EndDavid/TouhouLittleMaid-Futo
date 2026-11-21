package com.github.tartaricacid.touhoulittlemaid.item.enchantment;

import com.github.tartaricacid.touhoulittlemaid.item.ItemHakureiGohei;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * 滞弹（{@code impeding}），移植自上游 {@code item/enchantment/ImpedingEnchantment}。
 *
 * <p>命中敌人时按等级施加缓慢，等级与时长都在 {@code EntityDanmaku#onHitEntity} 里结算
 * （{@code duration = (20 + level * 10) * 20} tick）。</p>
 *
 * <p>上游给这个附魔建了一个专属分类 {@code GOHEI}，本移植版改用
 * {@link EnchantmentCategory#WEAPON} + 重写 {@link #canEnchant}，
 * 原因见 {@code InitEnchantments} 的类注释。</p>
 */
public class ImpedingEnchantment extends Enchantment {
    public ImpedingEnchantment() {
        super(Rarity.UNCOMMON, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /**
     * 只认御币。
     *
     * <p>这一句是「GOHEI 分类」在本移植版的替身：铁砧、{@code enchant_randomly} 战利品函数
     * 都走这个方法，因此判定边界与上游一致。附魔台的候选列表走的是 {@code category}，
     * 由 {@code EnchantmentHelperMixin} 补平。</p>
     */
    @Override
    public boolean canEnchant(ItemStack stack) {
        return ItemHakureiGohei.isGohei(stack);
    }

    @Override
    public int getMaxLevel() {
        return 4;
    }

    /**
     * 消耗 10 18 26 34
     */
    @Override
    public int getMinCost(int level) {
        return 10 + (level - 1) * 8;
    }
}

