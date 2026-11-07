package com.github.tartaricacid.touhoulittlemaid.item;

import net.minecraft.world.item.Item;

/**
 * 女仆背包物品，移植自上游 {@code item/ItemMaidBackpack}。
 *
 * <p>物品本身没有任何逻辑 —— 它只是「背包类型」的载体：
 * 右键女仆时由 {@code BackpackManager#findBackpack(ItemStack)} 反查类型，
 * 再按该类型的容量与穿脱回调工作。</p>
 *
 * <p>与上游的差异：上游构造函数无参（内部硬编码 {@code stacksTo(1)} 与创造标签页）；
 * 本移植版把 {@link Properties} 交给注册处传入，便于按物品微调
 * （例如储罐背包后续要挂流体组件）。</p>
 */
public class ItemMaidBackpack extends Item {
    public ItemMaidBackpack(Properties properties) {
        super(properties);
    }
}

