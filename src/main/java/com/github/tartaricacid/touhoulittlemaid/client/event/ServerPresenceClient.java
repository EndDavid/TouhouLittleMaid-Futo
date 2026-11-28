package com.github.tartaricacid.touhoulittlemaid.client.event;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.network.ServerPresence;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * 客户端侧的「对面装没装本模组」探测，配套 {@link ServerPresence}。
 *
 * <h2>为什么不是一进服就判定</h2>
 * <p>服务端是在玩家登录完成时（{@code ServerPlayNetworkAddon#onClientReady}）才把
 * 「我能收哪些 channel」的清单发过来的，而客户端的 {@code ClientPlayConnectionEvents.JOIN}
 * 也在同一时刻触发 —— 两者谁先谁后没有保证。若在 JOIN 里立刻问
 * {@code ClientPlayNetworking#canSend}，可能拿到 {@code false} 而误判。
 * 所以这里从 JOIN 起最多再观察 {@link #PROBE_TICKS} tick（3 秒）：
 * 期间任一刻看到 {@code true} 就认定「服务端装了」，超时仍是 {@code false} 才认定「没装」。</p>
 *
 * <h2>误判的代价与方向</h2>
 * <p>两种判定都停止探测，此后不再改变（连接断开时复位）。误判只可能是
 * 「服务端其实装了、但清单迟到超过 3 秒」—— 此时创造页签会被藏起来，重进一次世界即可恢复；
 * 反过来「服务端没装却显示页签」才是要修的那个 bug（物品拿在手里会在服务端失效），
 * 所以这里选择宁可晚一点确认、也不提前放行。</p>
 */
public final class ServerPresenceClient {
    /**
     * JOIN 之后最多观察多少 tick。3 秒足够让 {@code minecraft:register} 包到达，
     * 又短到玩家还没来得及打开创造界面。
     */
    private static final int PROBE_TICKS = 60;

    /** {@code < 0} 表示当前不在探测（未连接 / 已判定）。 */
    private static int ticksLeft = -1;

    private ServerPresenceClient() {
    }

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ServerPresence.setAbsent(false);
            ticksLeft = PROBE_TICKS;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            // 回到标题画面 / 换服：状态归零，下一个连接重新判定
            ServerPresence.setAbsent(false);
            ticksLeft = -1;
        });
        ClientTickEvents.END_CLIENT_TICK.register(ServerPresenceClient::probe);
    }

    private static void probe(Minecraft client) {
        if (ticksLeft < 0) {
            return;
        }
        // 世界尚未就绪时 canSend 一定是 false，不能算数（否则会白耗 tick）
        if (client.player == null || client.getConnection() == null) {
            return;
        }
        if (ClientPlayNetworking.canSend(ServerPresence.CHANNEL)) {
            ticksLeft = -1;
            TouhouLittleMaid.LOGGER.debug(
                    "[Futo] remote server runs Touhou Little Maid, creative tabs kept");
            return;
        }
        if (--ticksLeft > 0) {
            return;
        }
        ticksLeft = -1;
        ServerPresence.setAbsent(true);
        TouhouLittleMaid.LOGGER.debug(
                "[Futo] remote server does not run Touhou Little Maid (no {} channel): "
                        + "hiding its creative tabs, otherwise the items would be unusable",
                ServerPresence.CHANNEL);
        // 直接把「页签里还剩几条」打出来 —— 这是唯一能自动断言的证据。
        // 页签按钮的隐藏要人眼看，但「内容为空」在这里就能验。
        logTabSizes();
    }

    private static void logTabSizes() {
        logTabSize("main", InitItems.MAIN_TAB);
        logTabSize("chair", InitItems.CHAIR_TAB);
        logTabSize("garage_kit", InitItems.GARAGE_KIT_TAB);
    }

    private static void logTabSize(String name, CreativeModeTab tab) {
        NonNullList<ItemStack> items = NonNullList.create();
        tab.fillItemList(items);
        TouhouLittleMaid.LOGGER.debug("[Futo] creative tab '{}' now exposes {} item(s)", name, items.size());
    }
}
