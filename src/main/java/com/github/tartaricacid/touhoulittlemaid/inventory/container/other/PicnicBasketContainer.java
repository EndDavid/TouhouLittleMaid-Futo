package com.github.tartaricacid.touhoulittlemaid.inventory.container.other;

import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.init.InitMenu;
import com.github.tartaricacid.touhoulittlemaid.item.ItemPicnicBasket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * 野餐篮的 9 格界面，移植自上游 {@code inventory/container/other/PicnicBasketContainer}。
 *
 * <p><b>槽位编号与上游一致：</b>先加 9 个篮子格（{@code 0..8}），再加玩家物品栏
 * （{@code 9..35} 是主背包、{@code 36..44} 是快捷栏）。{@code quickMoveStack} 与
 * 「挡住手持格」两处都依赖这个顺序，不能照无线 IO 那样把玩家格放前面。</p>
 *
 * <h2>两道防刷物品的闸门</h2>
 * <ol>
 *     <li>{@code slotId == 36 + selected} —— 也就是「手上正拿着的这个篮子」那一格。
 *         允许操作它会让同一件物品出现两份引用，NBT 在两边来回写。</li>
 *     <li>{@code ClickType.SWAP}（数字键 / 副手键换物）—— 它会绕过 {@code quickMoveStack}
 *         直接把槽位内容和手上物品对调，等于把上面的闸门从侧门绕过去。</li>
 * </ol>
 *
 * <p>每次点击之后都要 {@code setContainer} 把拷贝写回物品 NBT：
 * 篮子格不是真正的容器（它只是 NBT 的一份快照），不写回就等于「界面里放进去的东西一关就没了」。</p>
 */
public class PicnicBasketContainer extends AbstractContainerMenu {
    /**
     * 篮子格数。奇偶/位置都与 {@code ItemPicnicBasket#PICNIC_BASKET_SIZE} 必须是同一个数，
     * 但那边是 private，这里只能自己再写一遍 —— 两处都有注释互相指认。
     */
    private static final int BASKET_SLOTS = 9;
    private static final int PLAYER_INV_START = BASKET_SLOTS;

    private final ItemStack picnicBasket;
    private final ItemStackHandler container;

    public PicnicBasketContainer(int id, Inventory inventory, ItemStack picnicBasket) {
        super(InitMenu.PICNIC_BASKET, id);
        this.picnicBasket = picnicBasket;
        this.container = ItemPicnicBasket.getContainer(picnicBasket);
        for (int i = 0; i < BASKET_SLOTS; ++i) {
            this.addSlot(new SlotItemHandler(this.container, i, 8 + i * 18, 18) {
                @Override
                public boolean mayPlace(@NotNull ItemStack stack) {
                    return stack.getItem().isEdible();
                }
            });
        }
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 49 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, 107));
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickTypeIn, Player player) {
        // 禁阻一切对当前手持物品的交互，防止刷物品 bug
        if (slotId == PLAYER_INV_START + player.getInventory().selected) {
            return;
        }
        if (clickTypeIn == ClickType.SWAP) {
            return;
        }
        super.clicked(slotId, button, clickTypeIn, player);
        // 篮子格只是 NBT 的快照，槽位变化不会自己落盘，必须写回物品
        ItemPicnicBasket.setContainer(this.picnicBasket, this.container);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack output = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            output = stack.copy();
            if (index < BASKET_SLOTS) {
                if (!this.moveItemStackTo(stack, BASKET_SLOTS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, 0, BASKET_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return output;
    }

    /**
     * 手上那件必须还是野餐篮。
     *
     * <p>只判「主手是野餐篮」而不是「主手是<b>那一个</b>野餐篮」：上游也只判类型。
     * 严格的引用比较在这里没法做 —— 客户端拿到的是一份拷贝，与服务端那份永远不等。</p>
     */
    @Override
    public boolean stillValid(Player player) {
        return player.getMainHandItem().getItem() == InitItems.PICNIC_BASKET;
    }
}

