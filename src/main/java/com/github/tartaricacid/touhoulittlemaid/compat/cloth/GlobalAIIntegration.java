package com.github.tartaricacid.touhoulittlemaid.compat.cloth;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.AvailableSites;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.AIConfig;
import com.github.tartaricacid.touhoulittlemaid.util.version.TComponent;
import com.google.common.collect.Maps;
import me.shedaniel.autoconfig.util.Utils;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.DropdownBoxEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.MutableComponent;

import java.nio.file.Path;
import java.util.SortedMap;

/**
 * 配置界面里的「全局 AI」一页，移植自上游 {@code compat/cloth/GlobalAIIntegration}。
 *
 * <p>六项：开关聊天 / 开关 TTS / 聊天温度 / 历史长度上限 / TTS 语言（下拉框）/ 一个
 * 「点这里打开 available_sites.yml」的链接。最后那一项是 {@code ClickEvent.OPEN_FILE}，
 * 点了会直接用系统默认程序打开配置文件 —— 上游就是这么做的，
 * 比自己再写一个编辑界面省事得多。</p>
 *
 * <p><b>与上游的两处差异：</b></p>
 * <ul>
 *     <li>本移植版另有 {@code AutoGenSettingEnabled} 与 {@code MaxTokensPerPlayer} 两项
 *         （1.20.1 的特性），上游 1.18.2 那版界面里没有。这里补进来了，
 *         否则这两项在配置界面里根本改不了。</li>
 *     <li>STT 两项（{@code STTUrl} / {@code STTProxyAddress}）<b>不进界面</b>：
 *         {@code ai/service/stt} 尚未移植，放进去只会让玩家以为填了就有用。
 *         它们仍留在 toml 里，配置文件可与上游互换。</li>
 * </ul>
 *
 * <p><b>TTS 语言下拉框为什么要自己造 cell。</b>
 * {@code LanguageManager#getLanguages()} 返回的是「语言代码 → LanguageInfo」，
 * 而在下拉框里显示 {@code LanguageInfo#toString()}（形如 {@code English (United States)}）
 * 才对玩家有意义。所以顶栏与列表项都要用一个把 code 映射成显示名的 cell creator，
 * 而不是默认的「直接 toString(code)」。</p>
 */
public final class GlobalAIIntegration {
    private GlobalAIIntegration() {
    }

    public static void aiChat(ConfigBuilder root, ConfigEntryBuilder entryBuilder) {
        ConfigCategory aiChat = root.getOrCreateCategory(TComponent.translatable("config.touhou_little_maid.global_ai"));

        aiChat.addEntry(entryBuilder.startBooleanToggle(TComponent.translatable("config.touhou_little_maid.global_ai.chat_enable"), AIConfig.CHAT_ENABLED.get())
                .setDefaultValue(true).setTooltip(TComponent.translatable("config.touhou_little_maid.global_ai.chat_enable.tooltip"))
                .setSaveConsumer(AIConfig.CHAT_ENABLED::set).build());

        aiChat.addEntry(entryBuilder.startBooleanToggle(TComponent.translatable("config.touhou_little_maid.global_ai.tts_enable"), AIConfig.TTS_ENABLED.get())
                .setDefaultValue(true).setTooltip(TComponent.translatable("config.touhou_little_maid.global_ai.tts_enable.tooltip"))
                .setSaveConsumer(AIConfig.TTS_ENABLED::set).build());

        aiChat.addEntry(entryBuilder.startDoubleField(TComponent.translatable("config.touhou_little_maid.global_ai.chat_temperature"), AIConfig.CHAT_TEMPERATURE.get())
                .setDefaultValue(0.5).setMin(0.0).setMax(2.0)
                .setTooltip(TComponent.translatable("config.touhou_little_maid.global_ai.chat_temperature.tooltip"))
                .setSaveConsumer(AIConfig.CHAT_TEMPERATURE::set).build());

        aiChat.addEntry(entryBuilder.startIntSlider(TComponent.translatable("config.touhou_little_maid.global_ai.maid_max_history_chat_size"),
                        AIConfig.MAID_MAX_HISTORY_CHAT_SIZE.get(), 1, 128).setDefaultValue(16)
                .setTooltip(TComponent.translatable("config.touhou_little_maid.global_ai.maid_max_history_chat_size.tooltip"))
                .setSaveConsumer(AIConfig.MAID_MAX_HISTORY_CHAT_SIZE::set).build());

        // 本移植版额外两项（1.20.1 特性）。
        aiChat.addEntry(entryBuilder.startBooleanToggle(TComponent.translatable("config.touhou_little_maid.global_ai.auto_gen_setting"), AIConfig.AUTO_GEN_SETTING_ENABLED.get())
                .setDefaultValue(true).setTooltip(TComponent.translatable("config.touhou_little_maid.global_ai.auto_gen_setting.tooltip"))
                .setSaveConsumer(AIConfig.AUTO_GEN_SETTING_ENABLED::set).build());

        aiChat.addEntry(entryBuilder.startIntField(TComponent.translatable("config.touhou_little_maid.global_ai.max_tokens_per_player"), AIConfig.MAX_TOKENS_PER_PLAYER.get())
                .setDefaultValue(0).setMin(0).setMax(Integer.MAX_VALUE)
                .setTooltip(TComponent.translatable("config.touhou_little_maid.global_ai.max_tokens_per_player.tooltip"))
                .setSaveConsumer(AIConfig.MAX_TOKENS_PER_PLAYER::set).build());

        SortedMap<String, LanguageInfo> languages = transformLanguageInfo();
        aiChat.addEntry(entryBuilder.startStringDropdownMenu(TComponent.translatable("config.touhou_little_maid.global_ai.tts_language"),
                        AIConfig.TTS_LANGUAGE.get(), TComponent::literal, cell(languages)).setSelections(languages.keySet())
                .setDefaultValue(LanguageManager.DEFAULT_LANGUAGE_CODE)
                .setTooltip(TComponent.translatable("config.touhou_little_maid.global_ai.tts_language.tooltip"))
                .setSaveConsumer(code -> AIConfig.TTS_LANGUAGE.set(code)).build());

        // 「点这里打开 available_sites.yml」。
        // 用 me.shedaniel.autoconfig.util.Utils#getConfigFolder 而不是自己拼路径：
        // 那是 Cloth 的 AutoConfig 用来定位配置目录的同名方法，与 FCAP 写 toml 的位置一致，
        // 免得「界面打开的是一个路径、配置实际存在另一个路径」。
        Path availableSiteFile = Utils.getConfigFolder().resolve(TouhouLittleMaid.MOD_ID).resolve(AvailableSites.FILE_NAME);
        ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.OPEN_FILE, availableSiteFile.toString());
        MutableComponent text = TComponent.translatable("config.touhou_little_maid.global_ai.chat_site.click");
        text.withStyle(style -> style.withUnderlined(true).withColor(ChatFormatting.BLUE).withClickEvent(clickEvent));
        aiChat.addEntry(entryBuilder.startTextDescription(text).build());
    }

    private static DropdownBoxEntry.SelectionCellCreator<String> cell(SortedMap<String, LanguageInfo> languages) {
        LanguageInfo defaultLanguage = languages.get(LanguageManager.DEFAULT_LANGUAGE_CODE);
        return new DropdownBoxEntry.DefaultSelectionCellCreator<>(code -> {
            LanguageInfo info = languages.getOrDefault(code, defaultLanguage);
            // info 为 null 说明玩家的语言包被删了（配置文件里存着旧 code）。
            // 这里退回显示 code 本身，不要让整个界面因为一次 getOrDefault 的 null 而崩。
            return TComponent.literal(info == null ? code : info.toString());
        });
    }

    private static SortedMap<String, LanguageInfo> transformLanguageInfo() {
        SortedMap<String, LanguageInfo> output = Maps.newTreeMap();
        Minecraft.getInstance().getLanguageManager().getLanguages()
                .forEach(languageInfo -> output.put(languageInfo.getCode(), languageInfo));
        return output;
    }
}

