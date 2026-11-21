package com.github.tartaricacid.touhoulittlemaid.item.enchantment;

import com.github.tartaricacid.touhoulittlemaid.item.ItemHakureiGohei;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * 速弹（{@code speedy}），移植自上游 {@code item/enchantment/SpeedyEnchantment}。
 *
 * <p>弹幕初速按 {@code speed * (level + 1)} 放大，见 {@code TaskDanmakuAttack#performRangedAttack}。</p>
 *
 * <p>上游给这个附魔建了一个专属分类 {@code GOHEI}，本移植版改用
 * {@link EnchantmentCategory#WEAPON} + 重写 {@link #canEnchant}，
 * 原因见 {@code InitEnchantments} 的类注释。</p>
 */
public class SpeedyEnchantment extends Enchantment {
    public SpeedyEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /**
     * 只认御币，理由同 {@link ImpedingEnchantment#canEnchant}。
     */
    @Override
    public boolean canEnchant(ItemStack stack) {
        return ItemHakureiGohei.isGohei(stack);
    }

    @Override
    public int getMaxLevel() {
        return 2;
    }

    /**
     * 消耗 25 35
     */
    @Override
    public int getMinCost(int level) {
        return 15 + level * 10;
    }
}

