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
 * 工作台背包：可用 18 格，移植自上游 {@code entity/backpack/CraftingTableBackpack}。
 *
 * <p><b>与上游的差异：</b>上游打开这个背包时用的是
 * {@code CraftingTableBackpackContainer}（自带 3×3 合成格 + 结果格），属于 GUI 里程碑。
 * 目前只有「18 格容量」这一半语义；容器移植后补 {@code getGuiProvider} 即可。</p>
 */
public class CraftingTableBackpack extends IMaidBackpack {
    public static final ResourceLocation ID =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "crafting_table_backpack");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public Item getItem() {
        return InitItems.CRAFTING_TABLE_BACKPACK;
    }

    @Override
    public void onPutOn(ItemStack stack, Player player, EntityMaid maid) {
        // 合成功能由容器提供，物品本身无副作用
    }

    @Override
    public void onTakeOff(ItemStack stack, Player player, EntityMaid maid) {
        this.dropRelativeItems(stack, maid);
    }

    @Override
    public int getAvailableMaxContainerIndex() {
        return BackpackLevel.CRAFTING_TABLE_CAPACITY;
    }
}

