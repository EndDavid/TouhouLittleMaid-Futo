package com.github.tartaricacid.touhoulittlemaid.ai.manager.entity;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.AutoGenSetting;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.SettingReader;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.Site;
import com.github.tartaricacid.touhoulittlemaid.ai.service.Service;
import com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.ChatClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.request.ChatCompletion;
import com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.response.ChatCompletionResponse;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.TTSApiType;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.TTSClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.TTSRequest;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.AIConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.ChatBubbleManger;
import com.github.tartaricacid.touhoulittlemaid.network.SyncChatTokensPacket;
import com.github.tartaricacid.touhoulittlemaid.network.TTSAudioToClientPacket;
import com.github.tartaricacid.touhoulittlemaid.network.TTSSystemAudioToClientPacket;
import com.github.tartaricacid.touhoulittlemaid.data.ChatTokensData;
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
 * <h2>TTS 分支</h2>
 * <p>与上游一致：解析出 {@code chatText} / {@code ttsText} 后，若 {@code TTS_ENABLED}
 * 且存在可用的 TTS 站点，就先走 {@code tts(...)} 合成语音（气泡与语音同时出现）；
 * TTS 关闭、站点缺失或合成失败时，才回退到 {@code addAiChatTextSync} 只显示气泡。</p>
 * <p>{@code api_type: system} 不走 HTTP：直接把 {@code ttsText} 发给主人，
 * 由客户端的系统旁白朗读（上游同样如此）。</p>
 */
public final class MaidAIChatManager extends MaidAIChatData {
    public MaidAIChatManager(EntityMaid maid) {
        super(maid);
    }
    /**
     * 自动生成人设的请求是否正在飞行中。
     *
     * <p>上游用一个「等待中的气泡 id」抑制重复请求；这里用一个布尔位，效果相同，
     * 而且不依赖气泡子系统：生成期间再说话只会再提示一次，
     * 不会真的再发一次 HTTP 请求（那会多烧一份额度）。</p>
     */
    private boolean generatingSetting = false;

    /**
     * 自动生成的人设正文长度上限。上游在回调里也做过一次 substring 截断：
     * 模型偶尔会写几千字，全部塞进 prompt 反而降低后续对话质量。
     */
    private static final int MAX_AUTO_SETTING_LENGTH = 2000;

    /**
     * 玩家说了一句话。
     *
     * <h2>与 1.18.2 上游的差异：设定为空不再等于「不能聊」</h2>
     * <p>1.18.2 上游的做法是「这个模型没有 settings/*.yml 就提示 no_setting，什么都不做」。
     * 1.20.1 改成了<b>所有女仆都能开界面</b>，第一次说话时若设定为空，
     * 就带着「生成一份人设」的 prompt 发一次请求，把结果落盘成 settings 文件
     * （见 onSettingIsEmpty）。本移植版按 1.20.1 的行为走。</p>
     */
    public void chat(String message, String language) {
        if (!isChatEnabled()) {
            ChatBubbleManger.addInnerChatText(maid, "ai.touhou_little_maid.chat.disable");
            return;
        }
        @Nullable Site site = this.getChatSite();
        if (site == null || StringUtils.isBlank(site.getApiKey())) {
            ChatBubbleManger.addInnerChatText(maid, "ai.touhou_little_maid.chat.api_key.empty");
            return;
        }
        if (!withinTokenLimit()) {
            sendToOwner(new TranslatableComponent(
                    "message.touhou_little_maid.ai_chat.max_tokens_limit").withStyle(ChatFormatting.RED));
            return;
        }
        if (this.getSetting().isEmpty()) {
            this.onSettingIsEmpty(site, language);
            return;
        }
        ChatClient chatClient = Service.getChatClient(site);
        ChatCompletion chatCompletion = Service.getChatCompletion(this, language);
        if (chatCompletion == null) {
            // getSetting() 刚查过非空，这里再取不到只可能是文件在这之间被删掉了
            ChatBubbleManger.addInnerChatText(maid, "ai.touhou_little_maid.chat.llm.role_no_setting");
            return;
        }
        chatCompletion.userChat(message);
        chatClient.chat(chatCompletion).handle(this::onShowChatSync, this::onChatFailSync);
        this.addUserHistory(message);
    }

    /**
     * 这只模型的角色设定为空。
     *
     * <p>配置允许时自动生成（ai.AutoGenSettingEnabled，默认开启），
     * 否则退回 1.18.2 上游的「设定为空」提示。</p>
     */
    private void onSettingIsEmpty(Site site, String language) {
        if (!isAutoGenSettingEnabled()) {
            ChatBubbleManger.addInnerChatText(maid, "ai.touhou_little_maid.chat.llm.role_no_setting");
            return;
        }
        this.autoGenSetting(site, language);
    }

    /**
     * 让模型写一份人设，成功后落盘成 config/touhou_little_maid/settings/<model>.yml。
     */
    private void autoGenSetting(Site site, String language) {
        // 提示先弹：这一步可能要等十几秒，玩家需要知道正在处理
        ChatBubbleManger.addInnerChatText(maid,
                "ai.touhou_little_maid.chat.llm.role_no_setting_and_gen_setting");
        if (this.generatingSetting) {
            return;
        }
        this.generatingSetting = true;
        ChatCompletion completion = AutoGenSetting.build(this, language);
        Service.getChatClient(site).chat(completion).handle(this::onAutoGenSuccess, this::onAutoGenFail);
    }

    /**
     * 从「角色设定编辑器」发起的重新生成。
     *
     * <p>与首次聊天时的自动生成走同一条链路，区别只有两点：</p>
     * <ul>
     *     <li>不需要先说话，可以直接点；</li>
     *     <li>不看 {@code AutoGenSettingEnabled}（这是玩家的显式请求）。</li>
     * </ul>
     * <p>已有设定文件时仍然不覆盖（见 {@code SettingReader#saveGeneratedSetting}）。</p>
     */
    public void regenerateSetting(String language) {
        @Nullable Site site = this.getChatSite();
        if (site == null || StringUtils.isBlank(site.getApiKey())) {
            ChatBubbleManger.addInnerChatText(maid, "ai.touhou_little_maid.chat.api_key.empty");
            return;
        }
        this.autoGenSetting(site, language);
    }
    private void onAutoGenSuccess(ChatCompletionResponse result) {
        String body = AutoGenSetting.stripCodeFence(result.getFirstChoiceMessage());
        this.addTokens(result);
        runOnServer(() -> {
            this.generatingSetting = false;
            if (StringUtils.isBlank(body)) {
                TouhouLittleMaid.LOGGER.error("[Futo] empty auto generated AI setting");
                ChatBubbleManger.addInnerChatText(maid, "ai.touhou_little_maid.chat.llm.role_no_setting");
                return;
            }
            String trimmed = body.length() > MAX_AUTO_SETTING_LENGTH
                    ? body.substring(0, MAX_AUTO_SETTING_LENGTH) : body;
            if (SettingReader.saveGeneratedSetting(maid.getModelId(), trimmed)) {
                ChatBubbleManger.addInnerChatText(maid, "ai.touhou_little_maid.chat.llm.auto_gen_setting");
            } else {
                // 已有同名文件（玩家手写）或写盘失败：不覆盖，提示仍然缺设定
                ChatBubbleManger.addInnerChatText(maid, "ai.touhou_little_maid.chat.llm.role_no_setting");
            }
        });
    }

    private void onAutoGenFail(Throwable throwable) {
        runOnServer(() -> this.generatingSetting = false);
        this.onChatFailSync(throwable);
    }

    /**
     * 把一段逻辑投递到服务端主线程：落盘、写同步数据、发消息都只能在主线程做。
     */
    private void runOnServer(Runnable task) {
        if (!(maid.level instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.getServer().submit(task);
    }

    /**
     * 把这次响应的 token 用量记到主人头上，并把新值同步给客户端。
     *
     * <p>对应 1.20.1 的 {@code ChatTokensAttachment} + {@code AIConfig.MAX_TOKENS_PER_PLAYER}：
     * 用量取响应里的 {@code usage.total_tokens}，缺这个字段就不计（自建网关常常不返回）。</p>
     */
    private void addTokens(ChatCompletionResponse result) {
        if (result == null || result.getUsage() == null) {
            return;
        }
        int total = result.getUsage().getTotalTokens();
        if (total <= 0) {
            return;
        }
        runOnServer(() -> {
            if (maid.getOwner() instanceof ServerPlayer player) {
                ChatTokensData.get(player.getServer()).add(player.getUUID(), total);
                SyncChatTokensPacket.sendTo(player);
            }
        });
    }
    /**
     * 主人是否还没用完 token 额度。
     *
     * <p>{@code MAX_TOKENS_PER_PLAYER <= 0} 表示不限制（配置默认值）。</p>
     */
    private boolean withinTokenLimit() {
        try {
            if (AIConfig.MAX_TOKENS_PER_PLAYER == null) {
                return true;
            }
            Integer max = AIConfig.MAX_TOKENS_PER_PLAYER.get();
            if (max == null || max <= 0) {
                return true;
            }
            if (!(maid.getOwner() instanceof ServerPlayer player)) {
                return true;
            }
            return ChatTokensData.get(player.getServer()).get(player.getUUID()) < max;
        } catch (Exception ignored) {
            // 配置尚未加载：放行
            return true;
        }
    }
    private static boolean isAutoGenSettingEnabled() {
        try {
            return AIConfig.AUTO_GEN_SETTING_ENABLED == null
                    || Boolean.TRUE.equals(AIConfig.AUTO_GEN_SETTING_ENABLED.get());
        } catch (Exception ignored) {
            // 配置尚未加载：按默认（开启）处理
            return true;
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
            this.addTokens(result);
            Site ttsSite = this.getTtsSite();
            if (isTtsEnabled() && ttsSite != null && StringUtils.isNotBlank(ttsSite.getApiKey())) {
                this.tts(ttsSite, chatText, ttsText);
            } else {
                // TTS 关闭、没有 tts 站点或站点没填 key：与上游一样直接显示气泡
                ChatBubbleManger.addAiChatTextSync(maid, chatText);
            }
        } catch (Exception e) {
            TouhouLittleMaid.LOGGER.error("[Futo] failed to handle AI chat response", e);
        }
    }
    /**
     * 走 TTS：{@code api_type: system} 走系统旁白，其余类型走 HTTP 合成。
     */
    private void tts(Site site, String chatText, String ttsText) {
        if (TTSApiType.SYSTEM.getName().equals(site.getApiType())) {
            onPlaySoundLocal(chatText, ttsText);
            return;
        }
        TTSClient<?> ttsClient = Service.getTtsClient(site);
        TTSRequest ttsRequest = Service.getTtsRequest(site, ttsText, ttsLang(), this.getTtsModel());
        if (ttsClient == null || ttsRequest == null) {
            // 站点里写了不认识的 api_type：别让语音把这条回复吞掉
            TouhouLittleMaid.LOGGER.warn("[Futo] unsupported TTS api type: {}", site.getApiType());
            ChatBubbleManger.addAiChatTextSync(maid, chatText);
            return;
        }
        requestTts(ttsClient, ttsRequest, chatText);
    }
    /**
     * 发起一次 TTS 请求。
     *
     * <p>{@link TTSClient} 的泛型参数由 {@link TTSRequest} 的具体实现决定，
     * 而 {@code Service} 只保证「客户端与请求体来自同一个站点」，编译期无法表达这层对应关系。
     * 因此这里把客户端按 {@code TTSClient<TTSRequest>} 使用 —— 两种实现的
     * {@code request(...)} 在擦除后本就接受任意 {@code TTSRequest}。</p>
     */
    @SuppressWarnings("unchecked")
    private void requestTts(TTSClient<?> ttsClient, TTSRequest ttsRequest, String chatText) {
        TTSClient<TTSRequest> client = (TTSClient<TTSRequest>) ttsClient;
        client.request(ttsRequest).handle(data -> onPlaySoundSync(chatText, data),
                throwable -> onTtsFailSync(chatText, throwable));
    }
    /**
     * 把配置里的 {@code TtsLanguage}（如 {@code zh_cn}）取前半段当作 BCP-47 主语言；
     * 缺省 {@code en} —— 与上游一致。
     */
    private String ttsLang() {
        String ttsLanguage = this.getTtsLanguage();
        if (StringUtils.isBlank(ttsLanguage)) {
            return "en";
        }
        String[] split = ttsLanguage.split("_");
        return split.length >= 2 ? split[0] : "en";
    }
    /**
     * 系统旁白：只发给主人本人，同时照常显示气泡。
     */
    private void onPlaySoundLocal(String chatText, String ttsText) {
        runOnServer(() -> {
            if (maid.getOwner() instanceof ServerPlayer player) {
                TTSSystemAudioToClientPacket.sendTo(player, ttsText);
            }
            ChatBubbleManger.addAiChatText(maid, chatText);
        });
    }
    /**
     * HTTP TTS 成功：把音频广播给附近玩家，同时显示气泡。
     */
    private void onPlaySoundSync(String chatText, byte[] data) {
        runOnServer(() -> {
            TTSAudioToClientPacket.sendNearby(maid, data);
            ChatBubbleManger.addAiChatText(maid, chatText);
        });
    }
    /**
     * TTS 失败不能连累聊天：气泡照常显示，另外给主人一条红色提示。
     */
    private void onTtsFailSync(String chatText, Throwable throwable) {
        runOnServer(() -> {
            ChatBubbleManger.addAiChatText(maid, chatText);
            String cause = throwable == null ? "" : StringUtils.defaultString(throwable.getLocalizedMessage());
            if (maid.getOwner() instanceof ServerPlayer player) {
                player.sendMessage(new TranslatableComponent("ai.touhou_little_maid.tts.connect.fail")
                        .append(cause).withStyle(ChatFormatting.RED), Util.NIL_UUID);
            }
        });
    }
    private static boolean isTtsEnabled() {
        try {
            return AIConfig.TTS_ENABLED == null || Boolean.TRUE.equals(AIConfig.TTS_ENABLED.get());
        } catch (Exception ignored) {
            // 配置尚未加载：按「开启」处理（没有 tts 站点时仍然只显示气泡）
            return true;
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
