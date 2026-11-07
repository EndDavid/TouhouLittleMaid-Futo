package com.github.tartaricacid.touhoulittlemaid.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 物品栏工具，移植自上游 {@code util/ItemsUtil}（只搬当前需要的部分）。
 *
 * <p>上游这里还有 {@code getBaubleSlotInMaid} / {@code getItemId} 等与饰品（Bauble）、
 * 注册表相关的工具，等对应子系统移植时再补。</p>
 */
public final class ItemsUtil {
    private ItemsUtil() {
    }

    /**
     * 在物品栏里找第一个符合条件的槽位。
     *
     * @return 找不到返回 {@code -1}
     */
    public static int findStackSlot(IItemHandler handler, Predicate<ItemStack> filter) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (filter.test(stack)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 取出所有符合条件的槽位下标。
     */
    public static List<Integer> getFilterStackSlots(IItemHandler handler, Predicate<ItemStack> filter) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (filter.test(stack)) {
                slots.add(i);
            }
        }
        return slots;
    }

    /**
     * 物品栏里是否存在符合条件的物品。
     */
    public static boolean isStackIn(IItemHandler handler, Predicate<ItemStack> filter) {
        return findStackSlot(handler, filter) >= 0;
    }

    /**
     * 取第一个符合条件的物品。
     *
     * @return 不存在时返回 {@link ItemStack#EMPTY}
     */
    public static ItemStack getStack(IItemHandler handler, Predicate<ItemStack> filter) {
        int slotIndex = findStackSlot(handler, filter);
        return slotIndex >= 0 ? handler.getStackInSlot(slotIndex) : ItemStack.EMPTY;
    }

    /**
     * 掉落指定区间 {@code [startIndex, endIndex)} 的物品。
     *
     * <p>注意提取动作会<b>真的清空槽位</b>（{@code simulate = false}），
     * 所以调用方要保证这些槽位确实不该留在女仆身上了。</p>
     */
    public static void dropEntityItems(Entity entity, IItemHandler itemHandler, int startIndex, int endIndex) {
        for (int i = startIndex; i < endIndex; i++) {
            ItemStack stackInSlot = itemHandler.getStackInSlot(i);
            ItemStack extracted = itemHandler.extractItem(i, stackInSlot.getCount(), false);
            if (!extracted.isEmpty()) {
                entity.spawnAtLocation(extracted);
            }
        }
    }

    /**
     * 掉落从 {@code startIndex} 起的全部物品。
     */
    public static void dropEntityItems(Entity entity, IItemHandler itemHandler, int startIndex) {
        dropEntityItems(entity, itemHandler, startIndex, itemHandler.getSlots());
    }

    /**
     * 掉落全部物品。
     */
    public static void dropEntityItems(Entity entity, IItemHandler itemHandler) {
        dropEntityItems(entity, itemHandler, 0, itemHandler.getSlots());
    }
}
