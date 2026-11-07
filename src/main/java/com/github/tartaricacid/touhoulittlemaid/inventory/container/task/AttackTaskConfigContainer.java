package com.github.tartaricacid.touhoulittlemaid.inventory.container.task;

import com.github.tartaricacid.touhoulittlemaid.entity.data.AttackListData;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/**
 * 攻击类工作模式的任务设置容器（上游 {@code AttackTaskConfigContainer}）。
 *
 * <p>它自己持有一份攻击名单副本，仅用于<b>界面</b>：</p>
 * <ul>
 *     <li>客户端：这份数据是打开界面时随菜单一起下发的（见
 *         {@code EntityMaid#openMaidGui(Player, int)} 里的 {@code writeToBuf}）；</li>
 *     <li>服务端：直接放真实那份（{@code EntityMaid#getAttackList()}），
 *         客户端关闭界面后发回来的新名单才会整体替换它。</li>
 * </ul>
 *
 * <p>界面里改的是这份副本，关界面时才发一个包提交 —— 这样「加了一条又删掉」
 * 之类的来回操作不会产生十几条网络包，也不会让服务端在编辑过程中反复重建行为树。</p>
 */
public class AttackTaskConfigContainer extends TaskConfigContainer {
    private final AttackListData attackList;

    public AttackTaskConfigContainer(MenuType<AttackTaskConfigContainer> type, int id,
                                     Inventory inventory, int entityId, AttackListData attackList) {
        super(type, id, inventory, entityId);
        this.attackList = attackList == null ? AttackListData.empty() : attackList;
    }

    public AttackListData getAttackList() {
        return this.attackList;
    }
}

