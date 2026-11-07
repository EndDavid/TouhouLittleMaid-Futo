package com.github.tartaricacid.touhoulittlemaid.inventory.container;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/**
 * 女仆配置界面的容器（{@code 女仆配置界面} 页签），移植自上游
 * {@code inventory/container/config/MaidConfigContainer}。
 *
 * <p>它<b>没有任何槽位</b>，只有玩家自己的物品栏 —— 界面上的 8 行是按钮，不是槽位。
 * 玩家物品栏坐标 (88, 174) 与主界面一致，这样切页签时下方物品栏不会跳动。</p>
 *
 * <p>为什么仍然要一个容器：页签切换是「服务端换菜单」，而且
 * {@code stillValid} 与实体 id 的传递都靠容器，纯客户端界面做不到这两件事。</p>
 */
public class MaidConfigContainer extends AbstractMaidContainer {
    public MaidConfigContainer(MenuType<MaidConfigContainer> type, int id, Inventory inventory, int entityId) {
        super(type, id, inventory, entityId);
        this.addPlayerInv(inventory, PLAYER_INV_X, PLAYER_INV_Y);
    }
}

