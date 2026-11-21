package com.github.tartaricacid.touhoulittlemaid.item.enchantment;

import com.github.tartaricacid.touhoulittlemaid.item.ItemHakureiGohei;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * 末影之末（{@code enders_ender}），移植自上游 {@code item/enchantment/EndersEnderEnchantment}。
 *
 * <p>原版弹幕对末影人无效（末影人免疫投掷物），这一级附魔把 {@code hurtEnderman} 置位，
 * 于是 {@code EntityDanmaku#onHitEntity} 会跳过那段免疫判定。</p>
 *
 * <p>上游给这个附魔建了一个专属分类 {@code GOHEI}，本移植版改用
 * {@link EnchantmentCategory#WEAPON} + 重写 {@link #canEnchant}，
 * 原因见 {@code InitEnchantments} 的类注释。</p>
 */
public class EndersEnderEnchantment extends Enchantment {
    public EndersEnderEnchantment() {
        super(Rarity.VERY_RARE, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /**
     * 只认御币，理由同 {@link ImpedingEnchantment#canEnchant}。
     */
    @Override
    public boolean canEnchant(ItemStack stack) {
        return ItemHakureiGohei.isGohei(stack);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return 20;
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return 50;
    }
}

