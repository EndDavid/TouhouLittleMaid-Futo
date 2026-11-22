package com.github.tartaricacid.touhoulittlemaid.event;

import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * 玩家进入服务器后的初始化，对应上游 {@code event/EnterServerEvent} 里与手册相关的那部分。
 *
 * <p>上游在 Forge 的 {@code PlayerEvent.PlayerLoggedInEvent} 里主动触发判据：
 * 只有判据被「敲」一下，那条隐藏进度才会去检查条件并发放奖励。
 * 这里用 Fabric 的 {@code ServerPlayConnectionEvents.JOIN} —— 它在客户端回完
 * 「准备就绪」之后触发，此时玩家的进度管理器已经就绪，可以安全写入。</p>
 *
 * <p>上游同一处还触发了 {@code GIVE_SMART_SLAB_CONFIG}（魂符）。本移植版一并补上：
 * 那条隐藏进度会在玩家第一次登录这个世界时发一个 {@code smart_slab_init}，
 * 送出过的记录存在玩家档案里，所以只会发一次。AI 设置同步仍未移植。</p>
 */
public final class EnterServerEvent {
    private EnterServerEvent() {
    }

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (player == null) {
                return;
            }
            if (InitTrigger.GIVE_PATCHOULI_BOOK_CONFIG != null) {
                InitTrigger.GIVE_PATCHOULI_BOOK_CONFIG.trigger(player);
            }
            if (InitTrigger.GIVE_SMART_SLAB_CONFIG != null) {
                InitTrigger.GIVE_SMART_SLAB_CONFIG.trigger(player);
            }
            // 把服务端的站点表与「哪些模型能聊天」同步给这位玩家。
            // 聊天界面与设置界面的候选项全靠它，缺了这一句玩家看到的永远是空列表。
            com.github.tartaricacid.touhoulittlemaid.network.SyncAiSettingPacket.sendTo(player);
            // 累计 token 用量（聊天界面顶部那行计数）
            com.github.tartaricacid.touhoulittlemaid.network.SyncChatTokensPacket.sendTo(player);
        });
    }
}

