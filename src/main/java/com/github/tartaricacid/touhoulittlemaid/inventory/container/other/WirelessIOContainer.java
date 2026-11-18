package com.github.tartaricacid.touhoulittlemaid.inventory.container.other;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.init.InitMenu;
import com.github.tartaricacid.touhoulittlemaid.item.ItemWirelessIO;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nonnull;

/**
 * 隙间的配置界面（9 格过滤清单 + 玩家物品栏），移植自上游
 * {@code inventory/container/other/WirelessIOContainer}。
 *
 * <p><b>槽位编号：</b>玩家物品栏先加（{@code 0..26} 是主背包、{@code 27..35} 是快捷栏），
 * 过滤清单后加（{@code 36..44}）。这个顺序不能改 ——
 * {@link #clicked} 里「挡住当前手持的那一格」用的就是 {@code 27 + selected}。</p>
 *
 * <p><b>为什么要挡手持格：</b>界面是「右击空气」打开的，手上那件物品本身就是这个界面。
 * 若允许在界面里把它拖进过滤清单或丢出去，NBT 会在同一件物品的两份引用之间来回写，
 * 是一类经典的刷物品路径。上游在 {@code clicked} 里拦掉两种操作：
 * 点手持那一格、以及 {@code SWAP}（数字键换手）。这里原样保留。</p>
 */
public class WirelessIOContainer extends AbstractContainerMenu {
    private final ItemStack wirelessIO;
    private final ItemStackHandler filterListInv;

    public WirelessIOContainer(int id, Inventory inventory, ItemStack wirelessIO) {
        super(InitMenu.WIRELESS_IO, id);
        this.wirelessIO = wirelessIO;
        // 这是一份从 NBT 读出的快照，改完由 clicked 写回物品
        this.filterListInv = ItemWirelessIO.getFilterList(wirelessIO);
        this.addPlayerSlots(inventory);
        this.addWirelessIOSlots();
    }

    @Override
    public boolean stillValid(Player playerIn) {
        return playerIn.getMainHandItem().getItem() == InitItems.WIRELESS_IO;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickTypeIn, Player player) {
        // 禁阻一切对当前手持物品的交互，防止刷物品 bug
        if (slotId == 27 + player.getInventory().selected) {
            return;
        }
        if (clickTypeIn == ClickType.SWAP) {
            return;
        }
        super.clicked(slotId, button, clickTypeIn, player);
        // 过滤清单不是真正的容器，槽位变化不会自己落盘，必须写回物品 NBT
        ItemWirelessIO.setFilterList(wirelessIO, filterListInv);
    }

    private void addWirelessIOSlots() {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 3; ++col) {
                this.addSlot(new WirelessIOSlotItemHandler(filterListInv, col + row * 3,
                        62 + col * 18, 17 + row * 18));
            }
        }
    }

    private void addPlayerSlots(Inventory inventory) {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    /**
     * Shift + 点击的搬运路线，与上游逐字相同。
     *
     * <p>副作用是<b>过滤清单的 9 格无法用 Shift 点击塞入</b>（{@code 27..36} 落在快捷栏上，
     * 而 {@code 0..27} 只覆盖主背包）。上游就是如此，只能手动拖。
     * 反过来，从过滤清单 Shift 点击会把物品搬回主背包，这是正常的。</p>
     */
    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        ItemStack stack1 = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack2 = slot.getItem();
            stack1 = stack2.copy();
            if (index < 27) {
                if (!this.moveItemStackTo(stack2, 27, 36, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack2, 0, 27, false)) {
                return ItemStack.EMPTY;
            }
            if (stack2.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return stack1;
    }

    /**
     * 界面先读它来决定两个开关的初始状态。
     */
    public ItemStack getWirelessIO() {
        return this.wirelessIO;
    }

    /**
     * 过滤清单的一格：只收「能进女仆背包」的物品（黑名单里的物品放进去没有意义，
     * 因为它本来就不会被搬动）。
     */
    private static class WirelessIOSlotItemHandler extends SlotItemHandler {
        private WirelessIOSlotItemHandler(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPlace(@Nonnull ItemStack stack) {
            return EntityMaid.canInsertItem(stack) && super.mayPlace(stack);
        }
    }
}

