package com.github.tartaricacid.touhoulittlemaid.inventory.container.task;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/**
 * 「当前工作模式没有任何配置项」用的容器（上游 {@code TaskConfigContainer} 的默认实现）。
 *
 * <p>它的存在不是为了放东西，而是为了让页签切换有一个合法的目标菜单 ——
 * 界面本身只显示一行提示与一张示意图。</p>
 */
public class DefaultTaskConfigContainer extends TaskConfigContainer {
    public DefaultTaskConfigContainer(MenuType<DefaultTaskConfigContainer> type, int id,
                                      Inventory inventory, int entityId) {
        super(type, id, inventory, entityId);
    }
}

