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
 * <p>上游同一处还触发了 {@code GIVE_SMART_SLAB_CONFIG}（灵魂符卡）并同步 AI 设置，
 * 这两块依赖尚未移植的系统，本移植版暂不处理。</p>
 */
public final class EnterServerEvent {
    private EnterServerEvent() {
    }

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (player != null && InitTrigger.GIVE_PATCHOULI_BOOK_CONFIG != null) {
                InitTrigger.GIVE_PATCHOULI_BOOK_CONFIG.trigger(player);
            }
        });
    }
}

