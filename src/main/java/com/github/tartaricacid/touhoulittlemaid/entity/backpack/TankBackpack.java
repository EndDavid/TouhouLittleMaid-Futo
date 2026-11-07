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
 * 储罐背包：可用 18 格，移植自上游 {@code entity/backpack/TankBackpack}。
 *
 * <p><b>与上游的差异：</b>上游用 {@code TankBackpackData}（{@code IBackpackData}）
 * + {@code ItemTankBackpack} 的流体组件保存罐内流体，脱下时把流体写回物品 NBT。
 * 流体层尚未移植，因此这里退化为「18 格容量 + 超出新容量的物品掉落」；
 * 物品本身也暂时用普通的 {@code ItemMaidBackpack} 注册。</p>
 */
public class TankBackpack extends IMaidBackpack {
    public static final ResourceLocation ID = new ResourceLocation(TouhouLittleMaid.MOD_ID, "tank");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public Item getItem() {
        return InitItems.TANK_BACKPACK;
    }

    @Override
    public void onPutOn(ItemStack stack, Player player, EntityMaid maid) {
        // TODO 储罐数据（TankBackpackData）移植后在这里回填流体
    }

    @Override
    public void onTakeOff(ItemStack stack, Player player, EntityMaid maid) {
        this.dropRelativeItems(stack, maid);
    }

    @Override
    public int getAvailableMaxContainerIndex() {
        return BackpackLevel.TANK_CAPACITY;
    }
}

