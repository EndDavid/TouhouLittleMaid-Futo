package com.github.tartaricacid.touhoulittlemaid.inventory.container.task;

import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/**
 * 任务设置界面的容器基类，移植自上游 {@code inventory/container/task/TaskConfigContainer}。
 *
 * <p>同样没有女仆侧槽位：任务设置的每一行都是按钮 / 输入框。
 * 玩家物品栏坐标与主界面一致，切页签时下方物品栏不会跳动。</p>
 */
public abstract class TaskConfigContainer extends AbstractMaidContainer {
    protected TaskConfigContainer(MenuType<?> type, int id, Inventory inventory, int entityId) {
        super(type, id, inventory, entityId);
        this.addPlayerInv(inventory, PLAYER_INV_X, PLAYER_INV_Y);
    }
}

