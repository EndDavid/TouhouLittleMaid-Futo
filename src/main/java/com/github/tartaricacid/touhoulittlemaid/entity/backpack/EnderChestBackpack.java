package com.github.tartaricacid.touhoulittlemaid.entity.backpack;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.backpack.IMaidBackpack;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.item.BackpackLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 末影箱背包：<b>不扩大</b>女仆物品栏（仍然是 6 格），移植自上游
 * {@code entity/backpack/EnderChestBackpack}。
 *
 * <p>它的用途是「打开主人自己的末影箱」，因此 {@code onTakeOff} 什么都不做 ——
 * 里面没有属于女仆的物品，不存在超出容量的东西要吐出来。</p>
 *
 * <p><b>与上游的差异：</b>容器（{@code EnderChestBackpackContainer}）属于 GUI 里程碑，
 * 目前这个背包只是「6 格容量 + 可装备」。</p>
 */
public class EnderChestBackpack extends IMaidBackpack {
    public static final ResourceLocation ID =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "ender_chest_backpack");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public Item getItem() {
        return InitItems.ENDER_CHEST_BACKPACK;
    }

    @Override
    public void onPutOn(ItemStack stack, Player player, EntityMaid maid) {
        // 末影箱内容属于玩家，无需迁移
    }

    @Override
    public void onTakeOff(ItemStack stack, Player player, EntityMaid maid) {
        // 与上游一致：末影箱背包脱下时不掉落任何物品
    }

    @Override
    public int getAvailableMaxContainerIndex() {
        return BackpackLevel.EMPTY_CAPACITY;
    }
}

