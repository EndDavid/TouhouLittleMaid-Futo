package com.github.tartaricacid.touhoulittlemaid.inventory.handler;

import net.minecraftforge.items.ItemStackHandler;

/**
 * 祭坛 / 神社的「一格贡品」容器，逐字移植自上游 {@code inventory/handler/AltarItemHandler}。
 *
 * <p>唯一区别是槽位上限为 1：一个祭坛位置只放一件贡品，堆叠会破坏
 * 「6 个位置 = 6 条原料」的一一对应关系。</p>
 */
public class AltarItemHandler extends ItemStackHandler {
    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }
}

