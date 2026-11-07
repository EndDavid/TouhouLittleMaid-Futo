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
 * 大背包：可用全部 36 格，移植自上游 {@code entity/backpack/BigBackpack}。
 *
 * <p>因为容量就是物品栏上限，换下大背包时 {@code dropRelativeItems}
 * 只会掉落「新背包装不下」的部分（例如换成小背包 → 掉落第 12 格之后的所有东西）。</p>
 */
public class BigBackpack extends IMaidBackpack {
    public static final ResourceLocation ID = new ResourceLocation(TouhouLittleMaid.MOD_ID, "big_backpack");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public Item getItem() {
        return InitItems.MAID_BACKPACK_BIG;
    }

    @Override
    public void onPutOn(ItemStack stack, Player player, EntityMaid maid) {
        // 大背包没有额外副作用
    }

    @Override
    public void onTakeOff(ItemStack stack, Player player, EntityMaid maid) {
        this.dropRelativeItems(stack, maid);
    }

    @Override
    public int getAvailableMaxContainerIndex() {
        return BackpackLevel.BIG_CAPACITY;
    }
}
