package net.minecraftforge.items;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Forge {@code ItemStackHandler} 的等价实现（Fabric 兼容层）。
 *
 * <p>语义与 Forge 1.18.2 保持一致：可序列化到 NBT（键名 {@code Size} / {@code Items}）、
 * 插入时先填已有同类堆叠、超出上限的部分原样返回。</p>
 */
public class ItemStackHandler implements IItemHandler {
    protected final net.minecraft.core.NonNullList<ItemStack> stacks;
    private final int size;

    public ItemStackHandler() {
        this(1);
    }

    public ItemStackHandler(int size) {
        this.size = size;
        this.stacks = net.minecraft.core.NonNullList.withSize(size, ItemStack.EMPTY);
    }

    /**
     * 内容变化回调，子类覆写。
     */
    protected void onContentsChanged(int slot) {
    }

    public void setSize(int size) {
        // 上游只构造时定尺寸；保留方法以避免调用方 NPE
    }

    @Override
    public int getSlots() {
        return this.size;
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        return this.stacks.get(slot);
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!this.isItemValid(slot, stack)) {
            return stack;
        }
        ItemStack existing = this.stacks.get(slot);
        int limit = this.getStackLimit(slot, stack);
        if (!existing.isEmpty()) {
            if (!ItemStack.isSameItemSameTags(existing, stack)) {
                return stack;
            }
            limit -= existing.getCount();
        }
        if (limit <= 0) {
            return stack;
        }
        boolean reachedLimit = stack.getCount() > limit;
        if (!simulate) {
            if (existing.isEmpty()) {
                this.stacks.set(slot, reachedLimit ? copyWithCount(stack, limit) : stack.copy());
            } else {
                existing.grow(reachedLimit ? limit : stack.getCount());
            }
            this.onContentsChanged(slot);
        }
        return reachedLimit ? copyWithCount(stack, stack.getCount() - limit) : ItemStack.EMPTY;
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = this.stacks.get(slot);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int toExtract = Math.min(amount, existing.getMaxStackSize());
        if (existing.getCount() <= toExtract) {
            if (!simulate) {
                this.stacks.set(slot, ItemStack.EMPTY);
                this.onContentsChanged(slot);
                return existing;
            }
            return existing.copy();
        }
        if (!simulate) {
            this.stacks.set(slot, copyWithCount(existing, existing.getCount() - toExtract));
            this.onContentsChanged(slot);
        }
        return copyWithCount(existing, toExtract);
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        if (slot < 0 || slot >= this.size) {
            return;
        }
        this.stacks.set(slot, stack);
        this.onContentsChanged(slot);
    }

    protected int getStackLimit(int slot, @NotNull ItemStack stack) {
        return Math.min(this.getSlotLimit(slot), stack.getMaxStackSize());
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return true;
    }

    // ------------------------------------------------------------------ 序列化

    public CompoundTag serializeNBT() {
        ListTag list = new ListTag();
        for (int i = 0; i < this.size; i++) {
            ItemStack stack = this.stacks.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putByte("Slot", (byte) i);
            stack.save(entry);
            list.add(entry);
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("Size", this.size);
        tag.put("Items", list);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        ListTag list = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("Slot") & 255;
            if (slot < this.size) {
                this.stacks.set(slot, ItemStack.of(entry));
            }
        }
        this.onLoad();
    }

    /**
     * 读档完成回调，子类覆写。
     */
    protected void onLoad() {
    }

    /**
     * 与 Forge 一致：复制一份并指定数量。
     */
    private static ItemStack copyWithCount(ItemStack stack, int count) {
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }
}
