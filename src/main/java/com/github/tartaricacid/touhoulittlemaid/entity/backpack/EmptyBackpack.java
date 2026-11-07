package com.github.tartaricacid.touhoulittlemaid.entity.backpack;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.backpack.IMaidBackpack;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.item.BackpackLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
/**
 * 空背包（未装备任何背包），移植自上游 {@code entity/backpack/EmptyBackpack}。
 *
 * <p>此时女仆只能使用物品栏的前 {@link BackpackLevel#EMPTY_CAPACITY} 格。</p>
 */
public class EmptyBackpack extends IMaidBackpack {
    public static final ResourceLocation ID = new ResourceLocation(TouhouLittleMaid.MOD_ID, "empty");
    @Override
    public ResourceLocation getId() {
        return ID;
    }
    @Override
    public Item getItem() {
        return Items.AIR;
    }
    @Override
    public void onPutOn(ItemStack stack, Player player, EntityMaid maid) {
        // 空背包没有额外副作用
    }
    @Override
    public void onTakeOff(ItemStack stack, Player player, EntityMaid maid) {
        // 空背包没有额外副作用
    }
    @Override
    public int getAvailableMaxContainerIndex() {
        return BackpackLevel.EMPTY_CAPACITY;
    }
}