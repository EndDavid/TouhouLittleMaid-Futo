package net.minecraftforge.items.wrapper;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * 把 {@link LivingEntity} 的护甲槽包装成 {@link IItemHandler}，等价 Forge {@code EntityArmorInvWrapper}。
 *
 * <p>槽位顺序与 Forge 一致：0=脚、1=腿、2=胸、3=头。</p>
 */
public class EntityArmorInvWrapper implements IItemHandler {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
    };

    private final LivingEntity entity;

    public EntityArmorInvWrapper(LivingEntity entity) {
        this.entity = entity;
    }

    @Override
    public int getSlots() {
        return SLOTS.length;
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < SLOTS.length ? this.entity.getItemBySlot(SLOTS[slot]) : ItemStack.EMPTY;
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (slot < 0 || slot >= SLOTS.length || stack.isEmpty()) {
            return stack;
        }
        EquipmentSlot equipmentSlot = SLOTS[slot];
        ItemStack existing = this.entity.getItemBySlot(equipmentSlot);
        if (!existing.isEmpty()) {
            return stack;
        }
        if (!this.isItemValid(slot, stack)) {
            return stack;
        }
        if (!simulate) {
            this.entity.setItemSlot(equipmentSlot, stack.copy());
        }
        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= SLOTS.length || amount <= 0) {
            return ItemStack.EMPTY;
        }
        EquipmentSlot equipmentSlot = SLOTS[slot];
        ItemStack existing = this.entity.getItemBySlot(equipmentSlot);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!simulate) {
            this.entity.setItemSlot(equipmentSlot, ItemStack.EMPTY);
        }
        return existing;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        if (slot < 0 || slot >= SLOTS.length) {
            return;
        }
        this.entity.setItemSlot(SLOTS[slot], stack);
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        if (slot < 0 || slot >= SLOTS.length) {
            return false;
        }
        // 1.18.2 没有 ItemStack#canEquip，改用等价的静态反查：
        // 该物品「本应」放进哪个护甲槽，必须与目标槽一致。
        return LivingEntity.getEquipmentSlotForItem(stack) == SLOTS[slot];
    }
}
