package com.github.tartaricacid.touhoulittlemaid.client.event;

import com.github.tartaricacid.touhoulittlemaid.compat.patchouli.PatchouliCompat;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 登录时若未安装帕秋莉手册，在聊天栏给出上游同款提示，移植自
 * {@code client/event/PlayerLoggedInNotice}。
 *
 * <p><b>与上游的差异只有一处：</b>上游用的是 Forge 的
 * {@code PlayerEvent.PlayerLoggedInEvent}，这里换成 Fabric 的
 * {@code ClientPlayConnectionEvents.JOIN}（客户端收到「可以开始玩」之后才触发，
 * 时机等价，且聊天栏消息本来就应该只在客户端发）。</p>
 *
 * <p>文本、颜色、加粗、下划线、点击打开下载页、悬停显示链接，全部沿用
 * {@code message.touhou_little_maid.missing_patchouli.*} 这三个语言键，
 * 因此中英文与上游逐字一致。</p>
 */
public final class PlayerLoggedInNotice {
    /**
     * 与上游一样，一次游戏会话只提示一次。重进存档 / 换服务器不再刷屏。
     */
    private static boolean notFirst = false;

    private PlayerLoggedInNotice() {
    }

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onEnterGame());
    }

    private static void onEnterGame() {
        if (notFirst) {
            return;
        }
        notFirst = true;
        if (!PatchouliCompat.isLoaded()) {
            sendMissingPatchouliMessage(Minecraft.getInstance().player);
        }
    }

    /**
     * 发送「缺少帕秋莉手册」提示。
     *
     * <p>侧边栏的「任务手册」按钮在未安装 Patchouli 时也会调用它 —— 上游那里弹的是一个
     * 警告界面，本移植版按需求改成同样的聊天栏提示，少一个界面类，信息完全一致。</p>
     *
     * @param player 接收者；为 null（还没进世界）时什么也不做
     */
    public static void sendMissingPatchouliMessage(@Nullable Player player) {
        if (player == null) {
            return;
        }
        MutableComponent title = new TranslatableComponent("message.touhou_little_maid.missing_patchouli.title")
                .withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(true));
        ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.OPEN_URL,
                I18n.get("message.touhou_little_maid.missing_patchouli.url"));
        HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new TranslatableComponent("message.touhou_little_maid.missing_patchouli.url"));
        MutableComponent base = new TranslatableComponent("message.touhou_little_maid.missing_patchouli.click_here")
                .withStyle(style -> style.withColor(ChatFormatting.GOLD)
                        .withBold(false)
                        .withUnderlined(true)
                        .withClickEvent(clickEvent)
                        .withHoverEvent(hoverEvent));
        player.sendMessage(title.append(new TextComponent(" ")).append(base), Util.NIL_UUID);
    }
}

