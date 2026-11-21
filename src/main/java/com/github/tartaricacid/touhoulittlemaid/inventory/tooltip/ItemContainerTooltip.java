package com.github.tartaricacid.touhoulittlemaid.inventory.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraftforge.items.IItemHandler;

/**
 * 「容器里的东西」这一行 tooltip，移植自上游 {@code inventory/tooltip/ItemContainerTooltip}。
 *
 * <p>它只是把物品栏原样带到客户端去：服务端的 {@code ItemPicnicBasket#getTooltipImage}
 * 构造它，客户端在 {@code InitClientTooltip} 里换成 {@code ClientItemContainerTooltip} 画出来。
 * 1.18.2 与 1.20 在这里没有 API 差异（原版两版都有
 * {@code Item#getTooltipImage} 与 {@code TooltipComponent}）。</p>
 *
 * <p>用 {@link IItemHandler} 而不是 {@code ItemStackHandler}：上游就是接口类型，
 * 这里保持同样的宽松度，将来别的容器也能复用这条 tooltip。</p>
 */
public record ItemContainerTooltip(IItemHandler handler) implements TooltipComponent {
}

