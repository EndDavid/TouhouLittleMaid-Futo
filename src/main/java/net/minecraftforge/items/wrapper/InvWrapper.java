package net.minecraftforge.items.wrapper;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * 把原版 {@link Container} 包装成 {@link IItemHandler}，等价 Forge 的
 * {@code net.minecraftforge.items.wrapper.InvWrapper}。
 *
 * <p>Forge 侧的箱子物品栏是由 Capability 提供的，本质就是这样一层包装；
 * 本移植版在 Fabric 上没有 Capability，于是直接把这层包装写出来，
 * 让「箱子 → IItemHandler」这一步与上游保持同一套语义：</p>
 *
 * <ul>
 *     <li>插入时先看 {@link Container#canPlaceItem}（原版用它做「这个槽位收不收这件物品」）；</li>
 *     <li>同槽位已有物品时必须<b>同物品同 NBT</b> 才能叠；</li>
 *     <li>单槽上限 = {@code min(容器上限, 物品堆叠上限)}；</li>
 *     <li>返回<b>没插进去的剩余部分</b>。</li>
 * </ul>
 *
 * <p><b>比 Forge 多做的一件事：</b>每次真写入后调用 {@link Container#setChanged()}。
 * 原版容器的 {@code setItem} 只改内存里的列表，不会标脏区块，
 * 于是「女仆把物品塞进箱子」有可能下线后丢失。Forge 靠 Capability 的持有者自己标脏，
 * 这里没有那一层，所以由包装器负责。</p>
 */
public class InvWrapper implements IItemHandler {
    private final Container inv;

    public InvWrapper(Container inv) {
        this.inv = inv;
    }

    public Container getInv() {
        return this.inv;
    }

    @Override
    public int getSlots() {
        return this.inv.getContainerSize();
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        this.validateSlotIndex(slot);
        return this.inv.getItem(slot);
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        this.validateSlotIndex(slot);
        if (!this.inv.canPlaceItem(slot, stack)) {
            return stack;
        }
        int stackLimit = this.getStackLimit(slot, stack);
        if (stackLimit <= 0) {
            return stack;
        }
        ItemStack existing = this.inv.getItem(slot);
        if (!existing.isEmpty()) {
            if (!ItemStack.isSameItemSameTags(existing, stack)) {
                return stack;
            }
            stackLimit -= existing.getCount();
        }
        if (stackLimit <= 0) {
            return stack;
        }
        boolean reachedLimit = stack.getCount() > stackLimit;
        if (!simulate) {
            ItemStack updated = existing.isEmpty()
                    ? (reachedLimit ? copyWithCount(stack, stackLimit) : stack.copy())
                    : copyWithCount(existing, existing.getCount() + (reachedLimit ? stackLimit : stack.getCount()));
            this.inv.setItem(slot, updated);
            this.inv.setChanged();
        }
        return reachedLimit ? copyWithCount(stack, stack.getCount() - stackLimit) : ItemStack.EMPTY;
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        this.validateSlotIndex(slot);
        ItemStack existing = this.inv.getItem(slot);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int toExtract = Math.min(amount, existing.getMaxStackSize());
        if (existing.getCount() <= toExtract) {
            if (!simulate) {
                this.inv.setItem(slot, ItemStack.EMPTY);
                this.inv.setChanged();
                return existing;
            }
            return existing.copy();
        }
        if (!simulate) {
            this.inv.setItem(slot, copyWithCount(existing, existing.getCount() - toExtract));
            this.inv.setChanged();
        }
        return copyWithCount(existing, toExtract);
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        this.validateSlotIndex(slot);
        this.inv.setItem(slot, stack);
        this.inv.setChanged();
    }

    @Override
    public int getSlotLimit(int slot) {
        return this.inv.getMaxStackSize();
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return this.inv.canPlaceItem(slot, stack);
    }

    protected int getStackLimit(int slot, @NotNull ItemStack stack) {
        return Math.min(this.getSlotLimit(slot), stack.getMaxStackSize());
    }

    protected void validateSlotIndex(int slot) {
        if (slot < 0 || slot >= this.inv.getContainerSize()) {
            throw new RuntimeException("Slot " + slot + " not in valid range - [0," + this.inv.getContainerSize() + ")");
        }
    }

    private static ItemStack copyWithCount(ItemStack stack, int count) {
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }
}

