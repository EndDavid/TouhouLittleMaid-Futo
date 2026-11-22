package com.github.tartaricacid.touhoulittlemaid.entity.chatbubble;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.concurrent.TimeUnit;

/**
 * 聊天气泡的服务端管理器（<b>本轮只移植 AI 聊天需要的那一半</b>），
 * 对应上游 {@code entity/chatbubble/ChatBubbleManger}。
 *
 * <h2>已移植</h2>
 * <ul>
 *     <li>{@link #addChatBubble}：三个槽位里挑最早到期的那个覆盖（上游原样逻辑）；</li>
 *     <li>{@link #tick}：<b>只做过期清理</b>；</li>
 *     <li>{@link #addInnerChatText}：内部提示（没填 API key / 没有角色设定 / 功能关闭），
 *         25 秒内同一只女仆只弹一次，避免连点刷屏；</li>
 *     <li>{@link #addAiChatTextSync} / {@link #addAiChatText}：AI 回复 —— 头顶气泡
 *         + 给主人发一条 {@code <女仆名> 内容} 的聊天栏消息。</li>
 * </ul>
 *
 * <h2>未移植（属于「聊天气泡子系统」那一轮）</h2>
 * <p>上游 {@code tick} 还会每 60 秒随机挑一条台本（{@code idle} / {@code sleep} /
 * {@code work} / {@code morning} / {@code rain} / {@code cold} …）弹出来，
 * 以及受伤时弹 {@code hurt}、饰品带的 {@code other} 气泡。这些都要读模型包里的
 * {@code chat_bubble} 配置与玩家的书与笔台本（{@code MaidScriptBookManager}），
 * 与 AI 聊天无关，本轮不引入。所以这里没有 {@code addHurtChatText}，
 * 也没有任何随机内容。</p>
 *
 * <p>因此：<b>如果 AI 聊天一直没被使用，女仆头顶不会有任何气泡</b> —— 这是预期行为。</p>
 */
public final class ChatBubbleManger {
    private static final Cache<Integer, ChatText> INNER_CHAT_TEXT_CACHE =
            CacheBuilder.newBuilder().expireAfterWrite(25, TimeUnit.SECONDS).build();
    /**
     * 显示时长，15 秒。
     */
    private static final long DURATION = 15 * 1000;

    /**
     * 往三个槽位里塞一条气泡。
     *
     * <p>选槽规则与上游逐字一致：{@code minEndTime} 从 -1 开始，谁「到期时间 ≤ 当前最小值」
     * 就被选中 —— 于是空槽（-1）优先，其次是最早到期的那个。</p>
     */
    public static void addChatBubble(long endTime, ChatText text, EntityMaid maid) {
        if (System.currentTimeMillis() > endTime) {
            return;
        }
        if (text == ChatText.EMPTY_CHAT_TEXT) {
            return;
        }

        Pair<Long, ChatText> bubbleItem = Pair.of(endTime, text);
        MaidChatBubbles chatBubble = maid.getChatBubble();
        long minEndTime = -1;
        int index = 1;
        Pair<Long, ChatText> bubble1 = chatBubble.getBubble1();
        Pair<Long, ChatText> bubble2 = chatBubble.getBubble2();
        Pair<Long, ChatText> bubble3 = chatBubble.getBubble3();

        if (bubble1.getLeft() <= minEndTime) {
            minEndTime = bubble1.getLeft();
        }
        if (bubble2.getLeft() <= minEndTime) {
            minEndTime = bubble2.getLeft();
            index = 2;
        }
        if (bubble3.getLeft() <= minEndTime) {
            index = 3;
        }

        MaidChatBubbles newChatBubble;
        switch (index) {
            default:
            case 1:
                newChatBubble = new MaidChatBubbles(bubbleItem, bubble2, bubble3);
                break;
            case 2:
                newChatBubble = new MaidChatBubbles(bubble1, bubbleItem, bubble3);
                break;
            case 3:
                newChatBubble = new MaidChatBubbles(bubble1, bubble2, bubbleItem);
                break;
        }

        maid.setChatBubble(newChatBubble);
    }

    public static int getChatBubbleCount(EntityMaid maid) {
        int count = 0;
        MaidChatBubbles chatBubble = maid.getChatBubble();
        if (chatBubble.getBubble1() != MaidChatBubbles.EMPTY) {
            count = count + 1;
        }
        if (chatBubble.getBubble2() != MaidChatBubbles.EMPTY) {
            count = count + 1;
        }
        if (chatBubble.getBubble3() != MaidChatBubbles.EMPTY) {
            count = count + 1;
        }
        return count;
    }

    /**
     * 服务端每 tick 调用：把到期的槽位清空。
     *
     * <p>两侧都要做（服务端 + 客户端）：服务端清是为了不再同步旧内容，
     * 客户端清是因为网络有延迟 —— 到期那一刻服务端的包可能还没到。</p>
     */
    public static void tick(EntityMaid maid) {
        checkTimeoutChatBubble(maid);
    }

    private static void checkTimeoutChatBubble(EntityMaid maid) {
        MaidChatBubbles chatBubble = maid.getChatBubble();
        Pair<Long, ChatText> bubble1 = chatBubble.getBubble1();
        Pair<Long, ChatText> bubble2 = chatBubble.getBubble2();
        Pair<Long, ChatText> bubble3 = chatBubble.getBubble3();
        long currentTimeMillis = System.currentTimeMillis();
        boolean changed = false;
        if (bubble1 != MaidChatBubbles.EMPTY && currentTimeMillis > bubble1.getLeft()) {
            bubble1 = MaidChatBubbles.EMPTY;
            changed = true;
        }
        if (bubble2 != MaidChatBubbles.EMPTY && currentTimeMillis > bubble2.getLeft()) {
            bubble2 = MaidChatBubbles.EMPTY;
            changed = true;
        }
        if (bubble3 != MaidChatBubbles.EMPTY && currentTimeMillis > bubble3.getLeft()) {
            bubble3 = MaidChatBubbles.EMPTY;
            changed = true;
        }
        // 上游无条件写回；这里只在真的清掉了东西时才写，避免每 tick 触发一次同步包
        if (changed) {
            maid.setChatBubble(new MaidChatBubbles(bubble1, bubble2, bubble3));
        }
    }

    /**
     * 内部提示（翻译键），例如 {@code ai.touhou_little_maid.chat.api_key.empty}。
     *
     * <p>用 {@code {key}} 花括号包起来，渲染时由 {@code ParseI18n} 转成
     * {@code TranslatableComponent} —— 这样客户端语言决定最终文本，
     * 而不是由服务端的语言决定。</p>
     */
    public static void addInnerChatText(EntityMaid maid, String key) {
        if (INNER_CHAT_TEXT_CACHE.getIfPresent(maid.getId()) == null) {
            ChatText chatText = new ChatText(ChatTextType.TEXT, ChatText.EMPTY_ICON_PATH, String.format("{%s}", key));
            INNER_CHAT_TEXT_CACHE.put(maid.getId(), chatText);
            maid.addChatBubble(getEndTime(), chatText);
        }
    }

    /**
     * 可以直接异步加载（HTTP 回调线程调用）。
     */
    public static void addAiChatTextSync(EntityMaid maid, String message) {
        if (StringUtils.isNotBlank(message) && maid.level instanceof ServerLevel serverLevel) {
            MinecraftServer server = serverLevel.getServer();
            server.submit(() -> addAiChatText(maid, message));
        }
    }

    public static void addAiChatText(EntityMaid maid, String message) {
        ChatText chatText = new ChatText(ChatTextType.TEXT, ChatText.EMPTY_ICON_PATH, message);
        maid.addChatBubble(getEndTime(), chatText);

        // 给主人发送聊天栏信息
        if (maid.getOwner() instanceof ServerPlayer player && player.isAlive()) {
            Component name = maid.getName();
            MutableComponent msg = new TextComponent("<").append(name).append(">").append(StringUtils.SPACE).append(message);
            player.sendMessage(msg.withStyle(ChatFormatting.GRAY), Util.NIL_UUID);
        }
    }

    private static long getEndTime() {
        return System.currentTimeMillis() + DURATION;
    }

    private ChatBubbleManger() {
    }
}

