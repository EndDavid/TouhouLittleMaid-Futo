package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.item.ItemWirelessIO;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 隙间（无线 I/O）配置界面的网络包，合并自上游的
 * {@code WirelessIOGuiMessage} 与 {@code WirelessIOSlotConfigMessage}。
 *
 * <p>两个包的方向<b>都是 C2S</b>：客户端改完开关就告诉服务端改物品 NBT。
 * 没有 S2C —— 界面上的初始值来自「打开菜单时下发的那份 {@code ItemStack} 拷贝」，
 * 改完之后由服务端<b>重新打开一次菜单</b>来刷新客户端那份拷贝
 * （上游 {@code WirelessIOSlotConfigMessage#handle} 最后那句 {@code NetworkHooks.openScreen}
 * 就是干这个）。</p>
 *
 * <h2>载荷格式</h2>
 * <pre>
 * byte  action
 *   SET_GUI_MODE   = 0 → boolean isMaidToChest, boolean isBlacklist
 *   SET_SLOT_CONFIG= 1 → byte[] config（长度 0 = 取消，只重开界面不改数据）
 * </pre>
 *
 * <h2>为什么不按上游拆成两个通道</h2>
 * <p>Fabric 的每条通道都要单独注册一个全局接收器；两个包共用一个通道 + 动作码，
 * 注册点、编解码、校验都只有一份。本项目其他包（神社庭灯、棋盘）也是这个写法。</p>
 *
 * <h2>服务端复核</h2>
 * <p>这是玩家可控的包，所以每次都重新确认「主手拿的确实是隙间」——
 * 上游也做了同样的检查。另外槽位配置的长度必须正好 38：
 * 长度不对的数组写进 NBT 之后，女仆每 5 秒会白跑一次搬运
 * （虽然 {@code WirelessIOBauble} 会兜底，但脏数据不该落盘）。</p>
 */
public final class WirelessIOPacket {
    public static final ResourceLocation CHANNEL = new ResourceLocation(TouhouLittleMaid.MOD_ID, "wireless_io");

    /**
     * C2S：切换 I/O 方向与黑白名单。
     */
    public static final byte SET_GUI_MODE = 0;
    /**
     * C2S：提交槽位配置（空数组表示取消），之后服务端重开界面。
     */
    public static final byte SET_SLOT_CONFIG = 1;

    /**
     * 槽位配置的合法长度：36 个背包槽 + 2 只手。
     */
    private static final int SLOT_NUM = 38;

    private WirelessIOPacket() {
    }

    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    byte action = buf.readByte();
                    switch (action) {
                        case SET_GUI_MODE -> {
                            boolean maidToChest = buf.readBoolean();
                            boolean isBlacklist = buf.readBoolean();
                            server.execute(() -> handleGuiMode(player, maidToChest, isBlacklist));
                        }
                        case SET_SLOT_CONFIG -> {
                            byte[] config = buf.readByteArray();
                            server.execute(() -> handleSlotConfig(player, config));
                        }
                        default -> {
                            // 未知动作码：忽略（可能是版本不一致的客户端）
                        }
                    }
                });
    }

    /**
     * 主手拿着隙间就返回它，否则返回 {@code null}。
     */
    private static ItemStack heldWirelessIO(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        return stack.getItem() == InitItems.WIRELESS_IO ? stack : null;
    }

    private static void handleGuiMode(ServerPlayer player, boolean maidToChest, boolean isBlacklist) {
        ItemStack stack = heldWirelessIO(player);
        if (stack == null) {
            return;
        }
        ItemWirelessIO.setMode(stack, maidToChest);
        ItemWirelessIO.setFilterMode(stack, isBlacklist);
    }

    private static void handleSlotConfig(ServerPlayer player, byte[] config) {
        ItemStack stack = heldWirelessIO(player);
        if (stack == null) {
            return;
        }
        if (config.length == SLOT_NUM) {
            ItemWirelessIO.setSlotConfig(stack, config);
        } else if (config.length != 0) {
            // 长度不对的数组直接丢弃，但仍然重开界面 —— 客户端要回到「已存在的状态」
            TouhouLittleMaid.LOGGER.warn("Ignoring wireless IO slot config of length {}", config.length);
        }
        // 重开界面：让客户端拿到更新后的 ItemStack 拷贝。
        // doCloseContainer() 是「不发关闭包地关」，否则客户端会 setScreen(null) 把鼠标拉回屏幕中心，
        // 紧接着 openMenu 又 releaseMouse —— 表现是鼠标跳到画面正中（女仆界面切页签是同一个坑）。
        player.doCloseContainer();
        player.openMenu((ItemWirelessIO) stack.getItem());
    }
}

