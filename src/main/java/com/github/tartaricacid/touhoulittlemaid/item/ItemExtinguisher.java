package com.github.tartaricacid.touhoulittlemaid.item;

import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import net.minecraft.Util;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 灭火器，移植自上游 {@code item/ItemExtinguisher}。
 *
 * <p>设计上<b>玩家无法使用</b>：右键只会提示一句「你匮乏的知识并不了解这物品该如何使用」，
 * 真正干活的是女仆的灭火模式（{@code extinguishing}），以及攻击模式下副手持有时
 * 对火免疫生物造成的额外伤害。</p>
 *
 * <p>与上游的差异：上游用 Forge 的 {@code Properties#setNoRepair()} 禁止铁砧修复，
 * 原版没有对应开关，因此改为重写 {@link #isValidRepairItem} 返回 {@code false}，语义一致。</p>
 */
public class ItemExtinguisher extends Item {
    /**
     * 耐久与上游一致：128。
     */
    public static final int DURABILITY = 128;

    public ItemExtinguisher() {
        super(new net.fabricmc.fabric.api.item.v1.FabricItemSettings()
                .durability(DURABILITY)
                .tab(InitItems.MAIN_TAB));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level worldIn, Player playerIn, InteractionHand handIn) {
        if (worldIn.isClientSide) {
            playerIn.sendMessage(new TranslatableComponent(
                    "message.touhou_little_maid.extinguisher.player_cannot_use"), Util.NIL_UUID);
        }
        return super.use(worldIn, playerIn, handIn);
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        // 上游 setNoRepair()：不可用铁砧修复
        return false;
    }
}

