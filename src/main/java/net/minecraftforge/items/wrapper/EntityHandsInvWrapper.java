package net.minecraftforge.items.wrapper;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * 把 {@link LivingEntity} 的主副手包装成 {@link IItemHandler}，等价 Forge {@code EntityHandsInvWrapper}。
 *
 * <p>槽位顺序与 Forge 一致：0=主手、1=副手。</p>
 */
public class EntityHandsInvWrapper implements IItemHandler {
    private static final InteractionHand[] HANDS = {InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND};

    protected final LivingEntity entity;

    public EntityHandsInvWrapper(LivingEntity entity) {
        this.entity = entity;
    }

    @Override
    public int getSlots() {
        return HANDS.length;
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < HANDS.length ? this.entity.getItemInHand(HANDS[slot]) : ItemStack.EMPTY;
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (slot < 0 || slot >= HANDS.length || stack.isEmpty() || !this.isItemValid(slot, stack)) {
            return stack;
        }
        InteractionHand hand = HANDS[slot];
        ItemStack existing = this.entity.getItemInHand(hand);
        if (!existing.isEmpty()) {
            return stack;
        }
        if (!simulate) {
            this.entity.setItemInHand(hand, stack.copy());
        }
        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= HANDS.length || amount <= 0) {
            return ItemStack.EMPTY;
        }
        InteractionHand hand = HANDS[slot];
        ItemStack existing = this.entity.getItemInHand(hand);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!simulate) {
            this.entity.setItemInHand(hand, ItemStack.EMPTY);
        }
        return existing;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        if (slot < 0 || slot >= HANDS.length) {
            return;
        }
        this.entity.setItemInHand(HANDS[slot], stack);
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return slot >= 0 && slot < HANDS.length;
    }
}
