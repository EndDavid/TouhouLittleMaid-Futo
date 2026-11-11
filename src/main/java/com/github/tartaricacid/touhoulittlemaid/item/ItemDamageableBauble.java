package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 有耐久的饰品，移植自上游 {@code item/ItemDamageableBauble}。
 *
 * <p>每挡下一次对应的伤害扣 1 点耐久，扣光即碎（饰品本身会消失）。
 * {@code setNoRepair()} 与上游一致：这类饰品<b>不允许</b>用铁砧 / 材料修复，
 * 想要新的只能重新合成 —— 这是刻意的消耗品设计。</p>
 */
public class ItemDamageableBauble extends Item {
    public ItemDamageableBauble(int durability) {
        super(new Properties().tab(InitItems.MAIN_TAB).durability(durability));
    }

    /**
     * Not anvil-repairable, matching upstream {@code setNoRepair()}.
     *
     * <p>Vanilla 1.18.2 has no {@code setNoRepair()} on {@code Item.Properties}
     * (that is a Forge addition), so the same intent is expressed by refusing every
     * repair material. These are consumables: you craft a new one.</p>
     */
    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}

