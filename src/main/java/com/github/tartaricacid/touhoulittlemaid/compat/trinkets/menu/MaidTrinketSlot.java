package com.github.tartaricacid.touhoulittlemaid.compat.trinkets.menu;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketInventory;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 女仆的饰品槽位（Trinkets 版），对应上游 {@code compat/curios/menu/MaidCurioSlot}。
 *
 * <h2>为什么用原版 {@link Slot} 而不是自定义 handler 包装</h2>
 * <p>Trinkets 的 {@link TrinketInventory} <b>本身就实现了原版 {@link Container}</b>
 * （{@code getContainerSize} / {@code getItem} / {@code setItem} 全都有，且
 * {@code setItem} 内部会调 {@code markUpdate()} 触发同步）。所以直接
 * {@code new Slot(trinketInventory, index, x, y)} 就能读写，不需要再包一层 ——
 * 这点和 Curios（{@code IItemHandler}）不同。</p>
 *
 * <h2>校验</h2>
 * <p>原版 {@code Slot#mayPlace} 只问 {@code Container#canPlaceItem}，而
 * {@code TrinketInventory} 没有覆写它（那套校验在 Trinkets 自己的
 * {@code SlotType} 谓词与 {@code Trinket#canEquip} 上）。所以这里显式去问
 * {@code Trinket#canEquip}，否则玩家能把「项链」塞进「戒指」格。</p>
 */
public class MaidTrinketSlot extends Slot {
    private final EntityMaid maid;
    private final String group;
    private final String name;

    public MaidTrinketSlot(EntityMaid maid, TrinketInventory inventory, int index,
                          String group, String name, int xPosition, int yPosition) {
        super(inventory, index, xPosition, yPosition);
        this.maid = maid;
        this.group = group;
        this.name = name;
    }

    public String getGroup() {
        return this.group;
    }

    public String getName() {
        return this.name;
    }

    /**
     * 槽位对应的 Trinkets 引用（{@code canEquip} 需要它）。
     */
    private SlotReference reference() {
        return new SlotReference((TrinketInventory) this.container, this.index);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return TrinketsApi.getTrinket(stack.getItem()).canEquip(stack, this.reference(), this.maid);
    }

    @Override
    public boolean mayPickup(Player player) {
        return TrinketsApi.getTrinket(this.getItem().getItem())
                .canUnequip(this.getItem(), this.reference(), this.maid);
    }

    @Override
    public int getMaxStackSize() {
        return ((TrinketInventory) this.container).getMaxStackSize();
    }

    /**
     * 界面上显示的名字，移植自上游 {@code MaidCurioSlot#getSlotName}。
     *
     * <p>上游先找 {@code curios.identifier.<组名>} 这条键，找不到就把组名首字母大写。
     * Trinkets 的槽位类型自带 {@code getTranslation()}（返回 {@code TranslatableComponent}），
     * 所以这里优先用它，退化路径与上游一致。</p>
     */
    public net.minecraft.network.chat.Component getSlotName() {
        SlotReference ref = this.reference();
        var slotType = ref.inventory().getSlotType();
        if (slotType != null && slotType.getTranslation() != null) {
            return slotType.getTranslation();
        }
        String key = "trinkets.slot." + this.group + "." + this.name;
        if (net.minecraft.client.resources.language.I18n.exists(key)) {
            return new net.minecraft.network.chat.TranslatableComponent(key);
        }
        String text = this.name.isEmpty() ? this.group : this.name;
        return new net.minecraft.network.chat.TextComponent(
                Character.toUpperCase(text.charAt(0)) + text.substring(1).toLowerCase());
    }
}

