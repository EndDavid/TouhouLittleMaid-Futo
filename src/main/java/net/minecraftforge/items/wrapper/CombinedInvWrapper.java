package net.minecraftforge.items.wrapper;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * 把多个 {@link IItemHandler} 串接成一个大的逻辑物品栏，等价 Forge {@code CombinedInvWrapper}。
 *
 * <p>上游用它把「护甲 + 双手 + 背包」拼成女仆的「可用物品栏」。</p>
 */
public class CombinedInvWrapper implements IItemHandler {
    protected final IItemHandler[] itemHandler;
    protected final int[] baseIndex;
    protected final int slotCount;

    public CombinedInvWrapper(IItemHandler... itemHandler) {
        this.itemHandler = itemHandler;
        this.baseIndex = new int[itemHandler.length];
        int index = 0;
        for (int i = 0; i < itemHandler.length; i++) {
            index += itemHandler[i].getSlots();
            this.baseIndex[i] = index;
        }
        this.slotCount = index;
    }

    /** 返回该全局槽位所属的子物品栏下标，越界返回 -1。 */
    protected int getIndex(int slot) {
        for (int i = 0; i < this.baseIndex.length; i++) {
            if (slot - this.baseIndex[i] < 0) {
                return i;
            }
        }
        return -1;
    }

    /** 全局槽位 → 子物品栏内槽位。 */
    protected int getSlot(int index, int slot) {
        return index == 0 ? slot : slot - this.baseIndex[index - 1];
    }

    @Override
    public int getSlots() {
        return this.slotCount;
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        int index = this.getIndex(slot);
        if (index < 0) {
            return ItemStack.EMPTY;
        }
        return this.itemHandler[index].getStackInSlot(this.getSlot(index, slot));
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        int index = this.getIndex(slot);
        if (index < 0) {
            return stack;
        }
        return this.itemHandler[index].insertItem(this.getSlot(index, slot), stack, simulate);
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        int index = this.getIndex(slot);
        if (index < 0) {
            return ItemStack.EMPTY;
        }
        return this.itemHandler[index].extractItem(this.getSlot(index, slot), amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        int index = this.getIndex(slot);
        if (index < 0) {
            return 0;
        }
        return this.itemHandler[index].getSlotLimit(this.getSlot(index, slot));
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        int index = this.getIndex(slot);
        if (index < 0) {
            return;
        }
        this.itemHandler[index].setStackInSlot(this.getSlot(index, slot), stack);
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        int index = this.getIndex(slot);
        if (index < 0) {
            return false;
        }
        return this.itemHandler[index].isItemValid(this.getSlot(index, slot), stack);
    }

    @Override
    public String toString() {
        return "CombinedInvWrapper" + Arrays.toString(this.baseIndex);
    }
}
