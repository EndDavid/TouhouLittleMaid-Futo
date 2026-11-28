package com.github.tartaricacid.touhoulittlemaid.init;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.data.AttackListData;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.MaidConfigContainer;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.MaidMainContainer;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.task.AttackTaskConfigContainer;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.task.DefaultTaskConfigContainer;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.other.PicnicBasketContainer;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.other.WirelessIOContainer;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.world.inventory.MenuType;

/**
 * 容器（菜单）注册。
 *
 * <p>Fabric 侧用 {@link ExtendedScreenHandlerType}：它在原版 {@code MenuType} 的基础上
 * 支持「打开界面时额外带一段 {@code PacketByteBuf} 数据」。我们用它把女仆的
 * <b>实体 id</b> 传到客户端，客户端据此在同一维度里找回实体。</p>
 *
 * <p><b>为什么是四个菜单而不是一个：</b>页签切换走的是「关掉当前菜单、打开另一个菜单」，
 * 所以每个页签必须有独立的 {@code MenuType}。这不只是代码组织的选择 ——
 * 客户端的界面类是由菜单类型决定的，只有这样才能在切换时换掉整个界面。
 * 上游同样是四个（主界面按背包分更多个，这里合并成一个）。</p>
 */
public final class InitMenu {
    public static final MenuType<MaidMainContainer> MAID_MAIN = Registry.register(
            Registry.MENU,
            TouhouLittleMaid.rl("maid_main"),
            new ExtendedScreenHandlerType<>((syncId, inventory, buf) ->
                    new MaidMainContainer(InitMenu.MAID_MAIN, syncId, inventory, buf.readInt())));

    public static final MenuType<MaidConfigContainer> MAID_CONFIG = Registry.register(
            Registry.MENU,
            TouhouLittleMaid.rl("maid_config"),
            new ExtendedScreenHandlerType<>((syncId, inventory, buf) ->
                    new MaidConfigContainer(InitMenu.MAID_CONFIG, syncId, inventory, buf.readInt())));

    public static final MenuType<DefaultTaskConfigContainer> TASK_CONFIG_DEFAULT = Registry.register(
            Registry.MENU,
            TouhouLittleMaid.rl("task_config_default"),
            new ExtendedScreenHandlerType<>((syncId, inventory, buf) ->
                    new DefaultTaskConfigContainer(InitMenu.TASK_CONFIG_DEFAULT, syncId, inventory, buf.readInt())));

    /**
     * 攻击名单紧跟在实体 id 之后下发（读的顺序必须与
     * {@code EntityMaid#openMaidGui} 里写的顺序完全一致）。
     */
    public static final MenuType<AttackTaskConfigContainer> TASK_CONFIG_ATTACK = Registry.register(
            Registry.MENU,
            TouhouLittleMaid.rl("task_config_attack"),
            new ExtendedScreenHandlerType<>((syncId, inventory, buf) ->
                    new AttackTaskConfigContainer(InitMenu.TASK_CONFIG_ATTACK, syncId, inventory, buf.readInt(),
                            AttackListData.readFromBuf(buf))));

    /**
     * 隙间（无线 I/O）配置界面。
     *
     * <p>打开时下发的是「主手那件物品」的 {@code ItemStack} 拷贝 ——
     * 界面上的 I/O 方向、过滤模式、9 格过滤清单全从这个拷贝里读。
     * 上游用 {@code IForgeMenuType.create((id, inv, data) -> data.readItem())}，
     * 与这里的写法是同一件事。</p>
     */
    public static final MenuType<WirelessIOContainer> WIRELESS_IO = Registry.register(
            Registry.MENU,
            TouhouLittleMaid.rl("wireless_io"),
            new ExtendedScreenHandlerType<>((syncId, inventory, buf) ->
                    new WirelessIOContainer(syncId, inventory, buf.readItem())));

    /**
     * 野餐篮的 9 格界面。
     *
     * <p>与隙间同款：打开时下发「主手那件物品」的 {@code ItemStack} 拷贝 ——
     * 界面里那 9 格就是这个拷贝上的 NBT。上游用
     * {@code IForgeMenuType.create((id, inv, data) -> data.readItem())}，是同一件事。</p>
     */
    public static final MenuType<PicnicBasketContainer> PICNIC_BASKET = Registry.register(
            Registry.MENU,
            TouhouLittleMaid.rl("picnic_basket"),
            new ExtendedScreenHandlerType<>((syncId, inventory, buf) ->
                    new PicnicBasketContainer(syncId, inventory, buf.readItem())));


    private InitMenu() {
    }

    public static void init() {
        // 静态字段即完成注册
    }
}