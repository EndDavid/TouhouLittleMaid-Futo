package net.minecraftforge.items;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Forge 物品槽抽象，Fabric 兼容层实现。
 *
 * <p>上游把女仆的背包做成 {@code ItemStackHandler}（Forge 的物品栏实现），并通过
 * {@code IItemHandler} 统一访问。Fabric 侧没有对应类型（fabric-transfer 的 API 语义不同），
 * 因此这里按 Forge 的原始语义重写一份纯数据结构 ——
 * 不涉及 Forge 的 Capability 系统，只是「槽位 + 插入/提取规则」。</p>
 */
public interface IItemHandler {
    /**
     * 槽位数量
     */
    int getSlots();

    @NotNull
    ItemStack getStackInSlot(int slot);

    /**
     * 向指定槽位插入物品，返回<b>没插进去的剩余部分</b>。
     */
    @NotNull
    ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate);

    /**
     * 从指定槽位提取物品，返回<b>实际提取到的部分</b>。
     */
    @NotNull
    ItemStack extractItem(int slot, int amount, boolean simulate);

    /**
     * 直接覆盖某个槽位的内容。
     *
     * <p>Forge 原接口就有这个方法（上游取箭时用 {@code getStackInSlot} 拿到箭、
     * {@code shrink(1)} 再 {@code setStackInSlot} 写回）。</p>
     */
    void setStackInSlot(int slot, @NotNull ItemStack stack);

    /**
     * 单个槽位的容量上限
     */
    int getSlotLimit(int slot);

    /**
     * 该槽位是否接受此物品
     */
    boolean isItemValid(int slot, @NotNull ItemStack stack);
}
