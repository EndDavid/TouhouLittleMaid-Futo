package com.github.tartaricacid.touhoulittlemaid.config.subconfig;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.AvailableSites;
import net.minecraftforge.common.ForgeConfigSpec;
/**
 * AI 聊天 / TTS / STT 的配置，移植自上游 {@code config/subconfig/AIConfig}。
 *
 * <p>九项与上游逐字一致（键名、默认值、范围都不改），配置文件可与上游互换。</p>
 *
 * <p>已经生效：聊天四项（{@link #CHAT_ENABLED} / {@link #CHAT_TEMPERATURE} /
 * {@link #CHAT_PROXY_ADDRESS} / {@link #MAID_MAX_HISTORY_CHAT_SIZE}），
 * TTS 三项（{@link #TTS_ENABLED} / {@link #TTS_LANGUAGE} / {@link #TTS_PROXY_ADDRESS}），
 * 以及 1.20.1 才有的 {@link #AUTO_GEN_SETTING_ENABLED} 与 {@link #MAX_TOKENS_PER_PLAYER}。</p>
 *
 * <p><b>STT 两项仍只是「留位」</b>：{@code ai/service/stt} 尚未移植，
 * 因此 {@link #STT_URL} / {@link #STT_PROXY_ADDRESS} 填了不会有任何效果，
 * 保留下来只是为了配置文件可与上游互换。</p>
 */
public class AIConfig {
    public static ForgeConfigSpec.BooleanValue CHAT_ENABLED;
    public static ForgeConfigSpec.DoubleValue CHAT_TEMPERATURE;
    public static ForgeConfigSpec.ConfigValue<String> CHAT_PROXY_ADDRESS;
    public static ForgeConfigSpec.BooleanValue TTS_ENABLED;
    public static ForgeConfigSpec.ConfigValue<String> TTS_LANGUAGE;
    public static ForgeConfigSpec.ConfigValue<String> TTS_PROXY_ADDRESS;
    public static ForgeConfigSpec.ConfigValue<String> STT_URL;
    public static ForgeConfigSpec.ConfigValue<String> STT_PROXY_ADDRESS;
    public static ForgeConfigSpec.IntValue MAID_MAX_HISTORY_CHAT_SIZE;
    /**
     * 角色设定为空时自动生成一份（1.20.1 TLMO 的特性，1.18.2 上游还没有）。
     */
    public static ForgeConfigSpec.BooleanValue AUTO_GEN_SETTING_ENABLED;
    /**
     * 每位玩家可累计使用的 token 上限（0 或负数表示不限制）。
     * 1.20.1 同样有这个限制，超限后用 message.touhou_little_maid.ai_chat.max_tokens_limit 提示。
     */
    public static ForgeConfigSpec.IntValue MAX_TOKENS_PER_PLAYER;
    public static void init(ForgeConfigSpec.Builder builder) {
        // 读取网站列表（jar 内置的 + config/touhou_little_maid/available_sites.yml）
        AvailableSites.readSites();
        builder.push("ai");
        builder.comment("Whether or not to enable the AI Chat feature");
        CHAT_ENABLED = builder.define("ChatEnabled", true);
        builder.comment("Chat temperature, the higher this value, the more random the output will be");
        CHAT_TEMPERATURE = builder.defineInRange("ChatTemperature", 0.5, 0, 2);
        builder.comment("Chat AI Proxy Address, such as 127.0.0.1:1080, empty is no proxy, SOCKS proxies are not supported");
        CHAT_PROXY_ADDRESS = builder.define("ChatProxyAddress", "");
        builder.comment("Whether or not to enable the TTS feature");
        TTS_ENABLED = builder.define("TTSEnabled", true);
        builder.comment("The TTS language you intend to use");
        TTS_LANGUAGE = builder.define("TTSLanguage", "en_us");
        builder.comment("TTS Proxy Address, such as 127.0.0.1:1080, empty is no proxy, SOCKS proxies are not supported");
        TTS_PROXY_ADDRESS = builder.define("TTSProxyAddress", "");
        builder.comment("STT Url address, currently only the player2 app is supported");
        STT_URL = builder.define("STTUrl", "http://127.0.0.1:4315/v1/stt");
        builder.comment("STT Proxy Address, such as 127.0.0.1:1080, empty is no proxy, SOCKS proxies are not supported");
        STT_PROXY_ADDRESS = builder.define("STTProxyAddress", "");
        builder.comment("Generate a character setting automatically when the maid's model has none");
        AUTO_GEN_SETTING_ENABLED = builder.define("AutoGenSettingEnabled", true);
        builder.comment("Max tokens a single player may spend in total, 0 means unlimited");
        MAX_TOKENS_PER_PLAYER = builder.defineInRange("MaxTokensPerPlayer", 0, 0, Integer.MAX_VALUE);

        builder.comment("The maximum historical conversation length cached by the maid");
        MAID_MAX_HISTORY_CHAT_SIZE = builder.defineInRange("MaidMaxHistoryChatSize", 16, 1, 128);
        builder.pop();
    }
}
