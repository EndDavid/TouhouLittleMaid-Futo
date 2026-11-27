package com.github.tartaricacid.touhoulittlemaid.network;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;

/**
 * 「对面的服务端到底装没装本模组」。
 *
 * <h2>为什么需要它</h2>
 * <p>Fabric 的模组是<b>两端各自加载</b>的：客户端装了、服务端没装，客户端依然会注册本模组的
 * 全部物品、方块与创造模式页签 —— 原版 Minecraft 没有「服务端注册表里没有就藏起来」这层逻辑
 * （那是 Forge 的 registry sync 在做，Fabric 没有等价物）。于是出现这个事故：
 * <b>连到一个没装本模组的服务器，客户端仍然看得到「Touhou Little Maid: Main」创造页签，
 * 还能把物品拖进背包</b>；可一旦真的拿在手里，服务端反序列化这个物品时注册表里没有对应 id，
 * 它会静默变成空气（或直接回弹），表现为「拿到了但完全用不了」。</p>
 *
 * <h2>它怎么判断</h2>
 * <p>不是读服务端 mod 列表（Fabric Loader 不提供），而是利用 Fabric Networking 的握手：
 * 服务端只要 {@code registerGlobalReceiver} 过一个 channel，Fabric 就会在玩家登录完成时
 * 把这个 channel 发给客户端（{@code minecraft:register} 包），客户端的
 * {@code ClientPlayNetworking#canSend(channel)} 随之为 {@code true}。
 * 所以本类只注册<b>一个空载荷的 channel</b>，它的唯一使命就是「声明本模组在场」。</p>
 *
 * <p>判定结果由客户端侧的 {@code client/event/ServerPresenceClient} 写入，
 * 消费方是创造模式页签（{@code init/InitItems} 的三个 {@code appendItems} 与
 * {@code mixin/client/CreativeModeInventoryScreenMixin}）。服务端与单机侧这个标志恒为
 * {@code false}，所以服务端行为一点不变。</p>
 */
public final class ServerPresence {
    /**
     * 「我在场」channel。刻意取一个与现有任何包都不冲突的名字，
     * 也不承载任何载荷 —— 收到它什么都不用做。
     */
    public static final ResourceLocation CHANNEL = TouhouLittleMaid.rl("presence");

    /**
     * 已确认「对面的服务端没有本模组」。
     *
     * <p>用 {@code volatile}：写入在客户端主线程的 tick 里，读取在同一条线程的界面渲染里，
     * 严格说不必同步；但它是 static 可变状态，跨类可见，标上避免将来有人从别的线程读。</p>
     */
    private static volatile boolean absent = false;

    private ServerPresence() {
    }

    /**
     * 服务端注册（在主入口调用）。空的接收器是刻意为之：本 channel 只用来被通告。
     */
    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    // 不读载荷、不回包。真正的用途见类注释。
                });
    }

    /**
     * 服务端没有本模组。创造页签据此清空并隐藏。
     *
     * <p>服务端 / 单机集成服务器侧恒为 {@code false}。</p>
     */
    public static boolean isAbsent() {
        return absent;
    }

    public static void setAbsent(boolean value) {
        absent = value;
    }
}
