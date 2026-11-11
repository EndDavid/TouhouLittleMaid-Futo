package com.github.tartaricacid.touhoulittlemaid.inventory.handler;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.BiFunction;

/**
 * 女仆饰品栏，移植自上游 {@code inventory/handler/BaubleItemHandler}。
 *
 * <p>它是一张普通的 {@link ItemStackHandler}，外加一份 <b>与槽位等长的行为缓存</b>：
 * {@code baubles[i]} 存的是第 i 格物品对应的 {@link IMaidBauble} 实现。
 * 缓存的意义是避免每 tick 对每格都做一次「物品 → 行为」的哈希查表；
 * 代价是必须在<b>每次内容变化</b>时同步刷新缓存 —— 见
 * {@link #onContentsChanged(int)} 与 {@link #onLoad()}。</p>
 *
 * <p><b>为什么 {@link #onLoad()} 也要刷新：</b>反序列化走的是直接写 {@code stacks}
 * 的路径，不经过 {@code setStackInSlot}，因此不会触发 {@code onContentsChanged}。
 * 少了这一步，读档后饰品栏里<b>东西在、效果全无</b>，而且玩家看不出来。</p>
 *
 * <p><b>与上游的差异：</b>上游还有一个
 * {@code BaubleItemHandler(NonNullList<ItemStack>)} 构造函数，全工程无人调用，
 * 且本移植版的 {@code ItemStackHandler} 兼容层不支持「外部列表」构造，故省略。</p>
 */
public class BaubleItemHandler extends ItemStackHandler {
    /**
     * 存储 {@link IMaidBauble} 对象的数组，该数组和饰品栏同等大小。
     */
    private IMaidBauble[] baubles;

    /**
     * 构建默认大小（1 格）的饰品栏。
     */
    public BaubleItemHandler() {
        this(1);
    }

    /**
     * 构建 size 大小的饰品栏。
     *
     * @param size 饰品栏大小
     */
    public BaubleItemHandler(int size) {
        super(size);
        this.baubles = new IMaidBauble[size];
    }

    /**
     * 设定指定格子的物品的 IMaidBauble 对象。
     */
    private void setBaubleInSlot(int slot, @Nullable IMaidBauble bauble) {
        if (slot < 0 || slot >= this.baubles.length) {
            return;
        }
        this.baubles[slot] = bauble;
    }

    /**
     * 获取指定格子的 IMaidBauble 对象。
     *
     * @param slot 指定的格子
     * @return 获取的 IMaidBauble 对象，空槽返回 {@code null}
     */
    @Nullable
    public IMaidBauble getBaubleInSlot(int slot) {
        ItemStack stack = this.getStackInSlot(slot);
        if (stack.isEmpty()) {
            return null;
        }
        return this.baubles[slot];
    }

    /**
     * 内容变化时刷新该格的「物品 → 行为」缓存。
     */
    @Override
    protected void onContentsChanged(int slot) {
        ItemStack stack = this.getStackInSlot(slot);
        if (stack.isEmpty()) {
            this.setBaubleInSlot(slot, null);
        } else {
            this.setBaubleInSlot(slot, BaubleManager.getBauble(stack));
        }
    }

    /**
     * 物品是否合法：只有「在 {@code BaubleManager} 里登记过」的物品才能进饰品栏。
     */
    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return BaubleManager.getBauble(stack) != null;
    }

    /**
     * 插入物品时的逻辑。
     *
     * <p>上游显式拦了一道，本移植版的 {@code ItemStackHandler} 兼容层内部也会拦；
     * 两边都写是为了让「不合法物品原样退回」这件事不依赖兼容层的实现细节。</p>
     */
    @Override
    @Nonnull
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (this.isItemValid(slot, stack)) {
            return super.insertItem(slot, stack, simulate);
        }
        return stack;
    }

    /**
     * 反序列化完成时把整张表重算一遍（原因见类注释）。
     */
    @Override
    protected void onLoad() {
        for (int i = 0; i < this.getSlots(); i++) {
            this.onContentsChanged(i);
        }
    }

    /**
     * 遍历饰品栏直到某个饰品的事件返回 {@code true}。
     *
     * <p>「返回 true 就停」是上游的约定，用途是「同一个效果只结算最近的一件」。</p>
     */
    public boolean fireEvent(BiFunction<IMaidBauble, ItemStack, Boolean> function) {
        for (int i = 0; i < this.getSlots(); i++) {
            ItemStack stack = this.getStackInSlot(i);
            IMaidBauble bauble = this.getBaubleInSlot(i);
            if (!stack.isEmpty() && bauble != null && function.apply(bauble, stack)) {
                return true;
            }
        }
        return false;
    }
}

