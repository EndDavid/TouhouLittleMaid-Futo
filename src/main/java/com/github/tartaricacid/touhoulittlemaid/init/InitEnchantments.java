package com.github.tartaricacid.touhoulittlemaid.init;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.item.enchantment.EndersEnderEnchantment;
import com.github.tartaricacid.touhoulittlemaid.item.enchantment.ImpedingEnchantment;
import com.github.tartaricacid.touhoulittlemaid.item.enchantment.SpeedyEnchantment;
import net.minecraft.core.Registry;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

/**
 * 三个自定义附魔（滞弹 / 速弹 / 末影之末），移植自上游 {@code init/InitEnchantments}。
 *
 * <p>三者都只作用于<b>御币</b>，效果全部在弹幕链路上结算：
 * {@code TaskDanmakuAttack} 读出等级 → {@code DanmakuShoot} 带给 {@code EntityDanmaku}
 * → {@code onHitEntity} 施加缓慢 / 放大速度 / 跳过末影人免疫。</p>
 *
 * <h2>为什么没有 {@code GOHEI} 附魔分类</h2>
 * <p>上游写的是
 * {@code EnchantmentCategory.create("gohei", item -> item instanceof ItemHakureiGohei)}，
 * 但 {@code EnchantmentCategory#create} 是 <b>Forge 给原版类打的补丁</b>，
 * 1.18.2 原版没有这个方法。而 {@code EnchantmentCategory} 是枚举：
 * 常量在类初始化时定死，外部既不能新增常量（JLS 8.9 禁止 class 继承 enum），
 * 也没法从外部继承它（构造函数是包私有的枚举构造函数）。</p>
 *
 * <p>所以这里退一步：三个附魔登记在 {@link EnchantmentCategory#WEAPON} 下，
 * 各自重写 {@code canEnchant(ItemStack)} 只认御币 —— 这正是 TLMO Fabric 1.20.1 的做法
 * （它的 {@code ImpedingEnchantment#method_8192} 就是
 * {@code stack.getItem() instanceof ItemHakureiGohei}）。</p>
 *
 * <p>唯一漏掉的是「附魔台」：原版 {@code EnchantmentHelper#getAvailableEnchantmentResults}
 * 直接读 {@code category.canEnchant(item)}，不调 {@code Enchantment#canEnchant}。
 * 若不处理，剑/斧也会被列出这三个附魔。{@code EnchantmentHelperMixin} 在那里补一刀，
 * 于是附魔台不会给出它们 —— 代价是「把御币放进附魔台也刷不出这三个」，
 * 而上游 Forge 刷得出来。获得途径仍与上游一致：创造物品栏的附魔书、
 * 村民的附魔书交易（那条路走的是 {@code Items.BOOK}，被原版的书本特例放行）、铁砧。</p>
 */
public final class InitEnchantments {
    public static final Enchantment IMPEDING = Registry.register(Registry.ENCHANTMENT,
            TouhouLittleMaid.rl("impeding"), new ImpedingEnchantment());

    public static final Enchantment SPEEDY = Registry.register(Registry.ENCHANTMENT,
            TouhouLittleMaid.rl("speedy"), new SpeedyEnchantment());

    public static final Enchantment ENDERS_ENDER = Registry.register(Registry.ENCHANTMENT,
            TouhouLittleMaid.rl("enders_ender"), new EndersEnderEnchantment());

    private InitEnchantments() {
    }

    public static void init() {
        // 静态字段即完成注册
    }

    /**
     * 最高等级的附魔书，放进创造模式物品栏用。
     *
     * <p>上游同名逻辑在 {@code InitCreativeTabs#getEnchantmentBook}；
     * 本移植版没有单独的 {@code InitCreativeTabs}，页签定义在 {@code InitItems} 里。</p>
     */
    public static ItemStack createBook(Enchantment enchantment) {
        return EnchantedBookItem.createForEnchantment(
                new EnchantmentInstance(enchantment, enchantment.getMaxLevel()));
    }
}

