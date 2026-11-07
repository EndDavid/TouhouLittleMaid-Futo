package com.github.tartaricacid.touhoulittlemaid.inventory.container;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 女仆 GUI 的基类，移植自上游 {@code inventory/container/AbstractMaidContainer}。
 *
 * <p>负责两件事：按 {@code entityId} 找到女仆实体、以及提供一个可复用的玩家物品栏添加方法。</p>
 *
 * <p><b>与上游的一个结构性差异：玩家物品栏不在基类构造函数里自动添加。</b>
 * 原版是在基类构造函数里调的，但那样会从构造函数里调用子类的虚方法（例如「物品栏起始 Y
 * 由背包容量的行数决定」），此时子类字段尚未初始化，取值恒为 0 —— Java 的经典陷阱。
 * 这里改为由子类在自己的字段就绪后显式调用 {@link #addPlayerInv(Inventory, int, int)}。</p>
 *
 * <p>槽位顺序约定：<b>玩家物品栏必须排在最前</b>（36 格），
 * 因为 {@link #quickMoveStack} 靠下标区分「玩家栏」与「女仆栏」（与上游一致）。</p>
 */
public abstract class AbstractMaidContainer extends AbstractContainerMenu {
    /**
     * 玩家物品栏占用的槽位数量（27 + 9）。
     */
    public static final int PLAYER_INVENTORY_SIZE = 36;
    /**
     * Player inventory origin inside the GUI (same on every maid screen, so the
     * bottom inventory does not jump when switching tabs).
     */
    public static final int PLAYER_INV_X = 88;
    public static final int PLAYER_INV_Y = 174;

    @Nullable
    protected final EntityMaid maid;

    protected AbstractMaidContainer(@Nullable MenuType<?> type, int id, Inventory inventory, int entityId) {
        super(type, id);
        this.maid = inventory.player.level.getEntity(entityId) instanceof EntityMaid found ? found : null;
        if (this.maid != null) {
            this.maid.setGuiOpening(true);
        }
    }

    /**
     * 添加玩家物品栏（3 行主栏 + 1 行快捷栏）。
     *
     * @param playerInventory 玩家物品栏
     * @param left            GUI 内相对 X
     * @param top             GUI 内相对 Y（主栏第一行）
     */
    protected void addPlayerInv(Inventory playerInventory, int left, int top) {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, left + col * 18, top + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, left + col * 18, top + 58));
        }
    }

    @Nullable
    public EntityMaid getMaid() {
        return this.maid;
    }

    @Override
    public void removed(Player playerIn) {
        super.removed(playerIn);
        if (this.maid != null) {
            this.maid.setGuiOpening(false);
        }
    }

    @Override
    public boolean stillValid(Player playerIn) {
        // 用加固过的 isOwnedByPlayer：只认 Owner UUID，不依赖 isTame 同步位
        return this.maid != null
               && this.maid.isOwnedByPlayer(playerIn)
               && !this.maid.isSleeping()
               && this.maid.isAlive()
               && this.maid.distanceTo(playerIn) < 5.0F;
    }

    /**
     * Shift + 点击的搬运规则：玩家栏与女仆栏互转（与上游一致）。
     */
    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return result;
        }
        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (index < PLAYER_INVENTORY_SIZE) {
            // 玩家栏 -> 女仆栏
            if (!this.moveItemStackTo(stack, PLAYER_INVENTORY_SIZE, this.slots.size(), false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, PLAYER_INVENTORY_SIZE, true)) {
            // 女仆栏 -> 玩家栏
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }
}

