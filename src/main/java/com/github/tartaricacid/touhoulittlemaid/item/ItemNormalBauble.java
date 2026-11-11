package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 普通饰品（无耐久），移植自上游 {@code item/ItemNormalBauble}。
 *
 * <p>「无耐久」意味着它的效果可以无限次触发，因此上游只把这几个做成普通饰品：
 * 物品磁铁、静音。保护类饰品全部是有耐久的（见 {@link ItemDamageableBauble}）。</p>
 */
public class ItemNormalBauble extends Item {
    public ItemNormalBauble() {
        super(new Properties().tab(InitItems.MAIN_TAB).stacksTo(1));
    }

    /**
     * 附魔光效常驻 —— 上游用这个把「饰品」与普通物品在视觉上区分开。
     */
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}

