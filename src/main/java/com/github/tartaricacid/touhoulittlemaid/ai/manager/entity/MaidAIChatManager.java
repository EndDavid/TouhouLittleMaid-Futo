package com.github.tartaricacid.touhoulittlemaid.ai.manager.entity;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.Site;
import com.github.tartaricacid.touhoulittlemaid.ai.service.Service;
import com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.ChatClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.request.ChatCompletion;
import com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.response.ChatCompletionResponse;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.AIConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.ChatBubbleManger;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
/**
 * AI 聊天的服务端编排器，移植自上游 {@code ai/manager/entity/MaidAIChatManager}。
 *
 * <p>流程：配置开关 → 找站点（必须有 key）→ 组装 prompt（必须有角色设定）→
 * 异步 HTTP → 回到主线程解析 {@code ResponseChat} → 头顶气泡 + 聊天栏。</p>
 *
 * <h2>与上游的差异（TTS 未移植）</h2>
 * <p>上游解析出 {@code chatText} / {@code ttsText} 后，若 {@code TTS_ENABLED} 且有可用
 * TTS 站点，会调用 {@code tts(...)} 走语音合成，播放的同时再显示气泡；失败才回退到
 * {@code addAiChatTextSync}。本移植版没有 TTS 客户端，所以<b>直接显示气泡</b>，
 * 不再判断 {@code TTS_ENABLED} —— 否则一份默认配置（{@code tts: true}）会让
 * AI 回复永远卡在「等语音」上。</p>
 * <p>因此 {@code ttsText} 仍然被解析和校验（模型输出契约里必须有它），只是不播放。</p>
 */
public final class MaidAIChatManager extends MaidAIChatData {
    public MaidAIChatManager(EntityMaid maid) {
        super(maid);
    }
    public void chat(String message, String language) {
        if (isChatEnabled()) {
            @Nullable Site site = this.getChatSite();
            if (site == null || StringUtils.isBlank(site.getApiKey())) {
                ChatBubbleManger.addInnerChatText(maid, "ai.touhou_little_maid.chat.api_key.empty");
            } else {
                ChatClient chatClient = Service.getChatClient(site);
                ChatCompletion chatCompletion = Service.getChatCompletion(this, language);
                if (chatCompletion != null) {
                    chatCompletion.userChat(message);
                    chatClient.chat(chatCompletion).handle(this::onShowChatSync, this::onChatFailSync);
                    this.addUserHistory(message);
                } else {
                    ChatBubbleManger.addInnerChatText(maid, "ai.touhou_little_maid.chat.no_setting");
                }
            }
        } else {
            ChatBubbleManger.addInnerChatText(maid, "ai.touhou_little_maid.chat.disable");
        }
    }
    /**
     * 回调可能来自 HTTP 线程，也可能来自已经关服的一瞬间，所以所有世界操作都
     * 重新投递到服务端主线程；解析失败只提示玩家，不抛。
     */
    private void onShowChatSync(ChatCompletionResponse result) {
        String rawMessage = result.getFirstChoiceMessage();
        try {
            ResponseChat responseChat = Service.GSON.fromJson(rawMessage, ResponseChat.class);
            if (responseChat == null) {
                TouhouLittleMaid.LOGGER.error("[Futo] malformed AI chat response: {}", rawMessage);
                onChatFailSync(new TranslatableComponent("ai.touhou_little_maid.chat.format.json_format_error", rawMessage));
                return;
            }
            String chatText = responseChat.getChatText();
            String ttsText = responseChat.getTtsText();
            if (StringUtils.isBlank(chatText) || StringUtils.isBlank(ttsText)) {
                TouhouLittleMaid.LOGGER.error("[Futo] empty AI chat response: {}", rawMessage);
                onChatFailSync(new TranslatableComponent("ai.touhou_little_maid.chat.format.text_is_empty", rawMessage));
                return;
            }
            this.addAssistantHistory(rawMessage);
            // TTS 未移植：直接气泡 + 聊天栏（上游在 TTS 关闭时也走这一支）
            ChatBubbleManger.addAiChatTextSync(maid, chatText);
        } catch (Exception e) {
            TouhouLittleMaid.LOGGER.error("[Futo] failed to handle AI chat response", e);
        }
    }
    private void onChatFailSync(Throwable throwable) {
        String cause = throwable == null ? "" : StringUtils.defaultString(throwable.getLocalizedMessage());
        sendToOwner(new TranslatableComponent("ai.touhou_little_maid.chat.connect.fail")
                .append(cause).withStyle(ChatFormatting.RED));
    }
    private void onChatFailSync(Component message) {
        sendToOwner(new TranslatableComponent("ai.touhou_little_maid.chat.connect.fail")
                .append(message).withStyle(ChatFormatting.RED));
    }
    /**
     * 给主人发一条系统消息（红色错误提示）。
     *
     * <p>与上游一样切回服务端主线程再发：{@code ServerPlayer#sendMessage} 不是线程安全的。</p>
     */
    private void sendToOwner(Component message) {
        if (!(maid.level instanceof ServerLevel serverLevel)) {
            return;
        }
        MinecraftServer server = serverLevel.getServer();
        server.submit(() -> {
            if (maid.getOwner() instanceof ServerPlayer player) {
                player.sendMessage(message, Util.NIL_UUID);
            }
        });
    }
    private static boolean isChatEnabled() {
        try {
            return AIConfig.CHAT_ENABLED == null || Boolean.TRUE.equals(AIConfig.CHAT_ENABLED.get());
        } catch (Exception ignored) {
            // 配置尚未加载：按「开启」处理，后续会因为站点表为空而提示缺 key
            return true;
        }
    }
}
