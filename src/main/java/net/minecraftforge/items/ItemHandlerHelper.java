package net.minecraftforge.items;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 物品栏操作工具，等价 Forge {@code ItemHandlerHelper}。
 */
public final class ItemHandlerHelper {
    private ItemHandlerHelper() {
    }

    /**
     * 把物品塞进整个物品栏，<b>优先堆叠到已有同类物品上</b>，返回剩余部分。
     *
     * <p>这是上游 `pickupItem` 使用的入口，语义与 Forge 一致：
     * 两轮扫描 —— 先填同类堆叠，再填空槽。</p>
     */
    @NotNull
    public static ItemStack insertItemStacked(IItemHandler inventory, @NotNull ItemStack stack, boolean simulate) {
        if (inventory == null || stack.isEmpty()) {
            return stack;
        }
        if (!stack.isStackable()) {
            return insertItem(inventory, stack, simulate);
        }
        ItemStack remaining = stack.copy();
        // 第一轮：堆叠到已有的同类槽位
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack slot = inventory.getStackInSlot(i);
            if (slot.isEmpty() || !ItemStack.isSameItemSameTags(slot, remaining)) {
                continue;
            }
            remaining = inventory.insertItem(i, remaining, simulate);
            if (remaining.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        // 第二轮：放进空槽位
        return insertItem(inventory, remaining, simulate);
    }

    /**
     * 按槽位顺序插入（不合并堆叠），返回剩余部分。
     */
    @NotNull
    public static ItemStack insertItem(IItemHandler dest, @NotNull ItemStack stack, boolean simulate) {
        if (dest == null || stack.isEmpty()) {
            return stack;
        }
        for (int i = 0; i < dest.getSlots(); i++) {
            stack = dest.insertItem(i, stack, simulate);
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    /**
     * 两个物品是否可堆叠（同物品 + 同 NBT + 都未损坏）。
     */
    public static boolean canItemStacksStack(@NotNull ItemStack a, @NotNull ItemStack b) {
        if (a.isEmpty() || !a.is(b.getItem())) {
            return false;
        }
        return ItemStack.isSameItemSameTags(a, b);
    }

    /**
     * 把物品交给玩家：先塞背包，塞不下就掉在脚边，等价 Forge {@code ItemHandlerHelper#giveItemToPlayer}。
     *
     * <p>签名保留 {@code Player}，是为了和上游调用点一一对应；
     * 原版 {@code Inventory#add} 已经是「先填同类堆叠、再填空槽」的语义。</p>
     */
    public static void giveItemToPlayer(Player player, @NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack remaining = stack.copy();
        if (!player.getInventory().add(remaining) && !remaining.isEmpty()) {
            player.drop(remaining, false);
        }
    }
}
