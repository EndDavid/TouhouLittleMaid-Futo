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
 * 熔炉背包：可用 18 格，移植自上游 {@code entity/backpack/FurnaceBackpack}。
 *
 * <p><b>与上游的差异：</b>上游通过 {@code FurnaceBackpackData}（{@code IBackpackData}）
 * 维护燃料与烧炼进度，并在脱下时把「燃料 + 输入 + 输出」全部吐出来。
 * 数据层尚未移植，因此这里退化为「18 格容量 + 超出新容量的物品掉落」，
 * 见 {@link #onTakeOff}。</p>
 */
public class FurnaceBackpack extends IMaidBackpack {
    public static final ResourceLocation ID =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "furnace_backpack");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public Item getItem() {
        return InitItems.FURNACE_BACKPACK;
    }

    @Override
    public void onPutOn(ItemStack stack, Player player, EntityMaid maid) {
        // TODO 熔炉数据（FurnaceBackpackData）移植后在这里初始化烧炼状态
    }

    @Override
    public void onTakeOff(ItemStack stack, Player player, EntityMaid maid) {
        this.dropRelativeItems(stack, maid);
    }

    @Override
    public int getAvailableMaxContainerIndex() {
        return BackpackLevel.FURNACE_CAPACITY;
    }
}

