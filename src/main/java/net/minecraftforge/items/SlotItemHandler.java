package net.minecraftforge.items;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 把 {@link IItemHandler} 包装成原版 {@link Slot}，等价 Forge {@code SlotItemHandler}。
 *
 * <p>原版 {@code Slot} 的所有读写都会落到构造时传入的 {@link Container} 上，
 * 而 Forge 的物品栏不是 {@code Container}。因此这里传一个<b>尺寸为 0</b> 的占位容器，
 * 并把每一个访问器都转发到 {@link IItemHandler}。</p>
 *
 * <p><b>关键：必须重写 {@link #remove(int)}。</b>
 * 原版「从槽位取走物品」的路径是 {@code Slot#remove(amount)}，它默认会去调
 * {@code container.removeItem(...)}。占位容器尺寸是 0，于是永远返回
 * {@link ItemStack#EMPTY} —— 玩家表现就是「只能放进去、拿不出来」。
 * 只重写 {@code onTake} 是不够的：{@code onTake} 在取走之后才被调用，
 * 它拿到的已经是空物品了。</p>
 */
public class SlotItemHandler extends Slot {
    private static final Container EMPTY_INVENTORY = new net.minecraft.world.SimpleContainer(0);

    private final IItemHandler itemHandler;
    private final int index;

    public SlotItemHandler(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
        super(EMPTY_INVENTORY, index, xPosition, yPosition);
        this.itemHandler = itemHandler;
        this.index = index;
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return this.itemHandler.isItemValid(this.index, stack);
    }

    @Override
    public @NotNull ItemStack getItem() {
        return this.itemHandler.getStackInSlot(this.index);
    }

    @Override
    public void set(@NotNull ItemStack stack) {
        this.itemHandler.setStackInSlot(this.index, stack);
        this.setChanged();
    }

    @Override
    public void onQuickCraft(@NotNull ItemStack oldStackIn, @NotNull ItemStack newStackIn) {
    }

    @Override
    public int getMaxStackSize() {
        return this.itemHandler.getSlotLimit(this.index);
    }

    @Override
    public int getMaxStackSize(@NotNull ItemStack stack) {
        ItemStack maxAdd = stack.copy();
        int maxInput = stack.getMaxStackSize();
        maxAdd.setCount(maxInput);

        ItemStack currentStack = this.itemHandler.getStackInSlot(this.index);
        this.itemHandler.setStackInSlot(this.index, ItemStack.EMPTY);

        ItemStack remainder = this.itemHandler.insertItem(this.index, maxAdd, true);
        this.itemHandler.setStackInSlot(this.index, currentStack);

        int current = remainder.getCount();
        int limit = this.getMaxStackSize();
        return Math.min(limit, maxInput - current);
    }

    /**
     * 这是「取出物品」的真正入口 —— 见类注释。
     */
    @Override
    public @NotNull ItemStack remove(int amount) {
        return this.itemHandler.extractItem(this.index, amount, false);
    }

    @Override
    public boolean mayPickup(Player playerIn) {
        ItemStack extracted = this.itemHandler.extractItem(this.index, 1, true);
        return !extracted.isEmpty();
    }

    @Override
    public void setChanged() {
        // 占位容器没有任何状态需要同步；真正的同步由 IItemHandler 的持有者负责
    }
}

