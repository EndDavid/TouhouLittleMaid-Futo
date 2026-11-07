package com.github.tartaricacid.touhoulittlemaid.client.screen;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.TabIndex;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.task.TaskConfigContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 任务设置页签的基类，移植自上游 {@code client/gui/entity/maid/task/MaidTaskConfigGui}。
 *
 * <p>上游这个类本身几乎是空的，只做一件事：{@code onClose} 时发一个包让服务端重建女仆的
 * Brain（{@code RefreshMaidBrainMessage}）。本移植版不需要那个包 —— 攻击名单的提交包里
 * 服务端已经顺手 {@code refreshBrain} 了，非攻击任务根本没有可改的东西。</p>
 *
 * <p>保留这一层抽象是为了让「任务设置」这个页签身份明确，另外两个子类
 * （默认提示页 / 攻击名单页）共用页签下标。</p>
 */
public abstract class MaidTaskConfigScreen<T extends TaskConfigContainer> extends AbstractMaidScreen<T> {
    protected MaidTaskConfigScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected int getCurrentTab() {
        return TabIndex.TASK_CONFIG;
    }
}

