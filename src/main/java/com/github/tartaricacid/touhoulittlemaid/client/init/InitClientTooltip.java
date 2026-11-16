package com.github.tartaricacid.touhoulittlemaid.client.init;

import com.github.tartaricacid.touhoulittlemaid.client.tooltip.ClientBoardStateTooltip;
import com.github.tartaricacid.touhoulittlemaid.inventory.tooltip.BoardStateTooltip;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;

/**
 * 把「残留道具的棋盘缩略图」这个 tooltip 组件接进渲染流程。
 *
 * <p>对应上游 1.20.1 的 {@code client/init/InitClientTooltip}，但机制完全不同：
 * Forge 是 {@code RegisterClientTooltipComponentFactoriesEvent}（注册一张
 * 「组件类型 → 渲染器」的表），Fabric 是 {@link TooltipComponentCallback} ——
 * 一个「给我这个组件，还你渲染器」的回调。语义一样，写法更直接。</p>
 *
 * <p><b>必须在客户端入口调用</b>：这个类引用 {@code ClientTooltipComponent}，
 * 放进主入口会让专用服务器在加载主类时碰到客户端类型。</p>
 */
public final class InitClientTooltip {
    private InitClientTooltip() {
    }

    public static void init() {
        TooltipComponentCallback.EVENT.register(data -> {
            if (data instanceof BoardStateTooltip boardState) {
                return new ClientBoardStateTooltip(boardState);
            }
            return null;
        });
    }
}

