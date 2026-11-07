package net.minecraftforge.items.wrapper;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * 只暴露底层物品栏的一段槽位，等价 Forge {@code RangedWrapper}。
 *
 * <p>上游用它把「背包」从「护甲 + 双手 + 背包」的大物品栏里单独切出来。</p>
 */
public class RangedWrapper implements IItemHandler {
    private final IItemHandler compose;
    private final int minSlot;
    private final int maxSlot;

    public RangedWrapper(IItemHandler compose, int minSlot, int maxSlotExclusive) {
        this.compose = compose;
        this.minSlot = minSlot;
        this.maxSlot = maxSlotExclusive;
    }

    @Override
    public int getSlots() {
        return this.maxSlot - this.minSlot;
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        return this.checkSlot(slot) ? this.compose.getStackInSlot(slot + this.minSlot) : ItemStack.EMPTY;
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        return this.checkSlot(slot) ? this.compose.insertItem(slot + this.minSlot, stack, simulate) : stack;
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        return this.checkSlot(slot) ? this.compose.extractItem(slot + this.minSlot, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return this.checkSlot(slot) ? this.compose.getSlotLimit(slot + this.minSlot) : 0;
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        if (this.checkSlot(slot)) {
            this.compose.setStackInSlot(slot + this.minSlot, stack);
        }
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return this.checkSlot(slot) && this.compose.isItemValid(slot + this.minSlot, stack);
    }

    private boolean checkSlot(int localSlot) {
        return localSlot + this.minSlot < this.maxSlot;
    }
}
