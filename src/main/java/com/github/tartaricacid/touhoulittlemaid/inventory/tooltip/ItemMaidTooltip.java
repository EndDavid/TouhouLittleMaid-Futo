package com.github.tartaricacid.touhoulittlemaid.inventory.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * 「物品里存着一只女仆」的提示框数据（上游 {@code inventory/tooltip/ItemMaidTooltip}）。
 *
 * <p>它本身<b>不含任何渲染代码</b>，只是一个数据载体：
 * {@code Item#getTooltipImage} 把它交给原版，原版再用
 * {@code ClientTooltipComponent#create(TooltipComponent)} 把它换成客户端实现
 * （{@code ClientMaidTooltip}），由后者画出三维女仆预览。</p>
 *
 * <h2>为什么需要这个中间层</h2>
 *
 * <p>{@code Item} 是<b>双端共享</b>的类，不能引用 {@code ClientTooltipComponent}；
 * 而客户端实现又需要拿到「模型 id / 自定义名」这两个字段。所以必须有一个
 * 纯数据的 {@link TooltipComponent} 在中间过一手。</p>
 *
 * <h2>与上游的差异</h2>
 *
 * <p>上游这个 record 有三个字段，第三个是 {@code YsmMaidInfo}（YSM 联动的模型 / 贴图 id）。
 * 本移植版没有 YSM 支持，故去掉。</p>
 */
public record ItemMaidTooltip(String modelId, String customName) implements TooltipComponent {
}

