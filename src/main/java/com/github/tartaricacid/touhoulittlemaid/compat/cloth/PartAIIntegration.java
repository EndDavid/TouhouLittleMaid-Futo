package com.github.tartaricacid.touhoulittlemaid.compat.cloth;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.client.screen.ai.AIChatScreen;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.SaveMaidAIDataPacket;
import com.github.tartaricacid.touhoulittlemaid.util.version.TComponent;
import com.google.common.collect.Maps;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.DropdownBoxEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.client.resources.language.LanguageManager;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.SortedMap;

/**
 * 单只女仆的 AI 设定界面，移植自上游 {@code compat/cloth/PartAIIntegration}。
 *
 * <p>与全局配置不同，这里的每一项都写进<b>这只女仆自己的数据</b>
 * （{@link MaidAIChatManager}，本质是 {@code MaidAIDataSerializable}），
 * 不碰 {@code config/*.toml}。所以保存走的是 {@code SaveMaidAIDataPacket}
 * 而不是 {@code ForgeConfigSpec#save()}。</p>
 *
 * <h2>每个站点一组「模型」下拉框，且按选中站点显隐</h2>
 * <p>站点 → 模型是一对多：选了站点 A 才该看到 A 的模型列表。
 * 上游的做法是给<b>每个站点</b>都建一个「模型」条目，
 * 再用 {@code setDisplayRequirement} 让「当前选中的站点不是我就藏起来」。
 * 这里照抄 —— 那样切换站点时不用重建界面。</p>
 *
 * <h2>与上游的一处差异</h2>
 * <p>上游 {@code chat_model} 的默认值是每次循环里重新从 {@code manager.getChatModel()}
 * 取一次（在循环外再判一次 contains）。这里改成先在循环外算一次
 * {@code currentChatModel}，循环里只做 contains 判断 —— 语义完全相同，
 * 但少了一堆重复取值。</p>
 */
public final class PartAIIntegration {
    private PartAIIntegration() {
    }

    public static ConfigBuilder getConfigBuilder(EntityMaid maid) {
        ConfigBuilder root = ConfigBuilder.create().setTitle(TComponent.translatable("config.touhou_little_maid.part_ai"));
        root.setGlobalized(true);
        root.setGlobalizedExpanded(false);
        ConfigEntryBuilder entryBuilder = root.entryBuilder();
        init(root, entryBuilder, maid);
        // 保存：把这批字段打包发给服务端写入这只女仆的 NBT。
        root.setSavingRunnable(() -> SaveMaidAIDataPacket.send(maid.getId(), maid.getAiChatManager()));
        return root;
    }

    @SuppressWarnings("all")
    public static void init(ConfigBuilder root, ConfigEntryBuilder entryBuilder, EntityMaid maid) {
        MaidAIChatManager manager = maid.getAiChatManager();
        ConfigCategory ai = root.getOrCreateCategory(TComponent.translatable("config.touhou_little_maid.part_ai"));

        ai.addEntry(entryBuilder.startTextField(TComponent.translatable("config.touhou_little_maid.part_ai.owner_name"), manager.getOwnerName())
                .setDefaultValue(StringUtils.EMPTY).setSaveConsumer(manager::setOwnerName)
                .setTooltip(TComponent.translatable("config.touhou_little_maid.part_ai.owner_name.tooltip")).build());

        ai.addEntry(entryBuilder.startTextField(TComponent.translatable("config.touhou_little_maid.part_ai.custom_setting"), manager.getCustomSetting())
                .setDefaultValue(StringUtils.EMPTY).setSaveConsumer(manager::setCustomSetting)
                .setTooltip(TComponent.translatable("config.touhou_little_maid.part_ai.custom_setting.tooltip")).build());

        // ---- 聊天：站点 + 每个站点一组模型 ----
        String chatSiteName = manager.getChatSiteName();
        String defaultChatSiteName = AIChatScreen.CLIENT_CHAT_SITES.containsKey(chatSiteName) ? chatSiteName : StringUtils.EMPTY;
        DropdownBoxEntry<String> chatSite = entryBuilder.startStringDropdownMenu(
                        TComponent.translatable("config.touhou_little_maid.part_ai.chat_site_name"), defaultChatSiteName)
                .setDefaultValue(StringUtils.EMPTY).setSelections(AIChatScreen.CLIENT_CHAT_SITES.keySet())
                .setSaveConsumer(manager::setChatSiteName)
                .setTooltip(TComponent.translatable("config.touhou_little_maid.part_ai.chat_site_name.tooltip")).build();
        ai.addEntry(chatSite);

        String currentChatModel = manager.getChatModel();
        for (String key : AIChatScreen.CLIENT_CHAT_SITES.keySet()) {
            List<String> models = AIChatScreen.CLIENT_CHAT_SITES.get(key);
            // 模型列表为空时跳过：否则会造出一个没有任何选项的下拉框，一展开就崩。
            if (models == null || models.isEmpty()) {
                continue;
            }
            String defaultModel = models.contains(currentChatModel) ? currentChatModel : StringUtils.EMPTY;
            ai.addEntry(entryBuilder.startStringDropdownMenu(
                            TComponent.translatable("config.touhou_little_maid.part_ai.chat_model"), defaultModel)
                    .setDefaultValue(StringUtils.EMPTY).setSelections(models)
                    .setTooltip(TComponent.translatable("config.touhou_little_maid.part_ai.chat_model.tooltip"))
                    .setSaveConsumer(result -> {
                        if (StringUtils.isNotBlank(chatSite.getValue()) && chatSite.getValue().equals(key)) {
                            manager.setChatModel(result);
                        }
                    })
                    .setDisplayRequirement(() -> StringUtils.isNotBlank(chatSite.getValue())
                            && chatSite.getValue().equals(key)).build());
        }

        ai.addEntry(entryBuilder.startDoubleField(TComponent.translatable("config.touhou_little_maid.part_ai.chat_temperature"),
                        Math.max(manager.getChatTemperature(), 0))
                .setDefaultValue(0.5).setMin(0.0).setMax(2.0)
                .setTooltip(TComponent.translatable("config.touhou_little_maid.part_ai.chat_temperature.tooltip"))
                .setSaveConsumer(manager::setChatTemperature).build());

        // ---- TTS：同样的「站点 + 每站点一组模型」 ----
        String ttsSiteName = manager.getTtsSiteName();
        String defaultTtsSiteName = AIChatScreen.CLIENT_TTS_SITES.containsKey(ttsSiteName) ? ttsSiteName : StringUtils.EMPTY;
        DropdownBoxEntry<String> ttsSite = entryBuilder.startStringDropdownMenu(
                        TComponent.translatable("config.touhou_little_maid.part_ai.tts_site_name"), defaultTtsSiteName)
                .setDefaultValue(StringUtils.EMPTY).setSelections(AIChatScreen.CLIENT_TTS_SITES.keySet())
                .setSaveConsumer(manager::setTtsSiteName)
                .setTooltip(TComponent.translatable("config.touhou_little_maid.part_ai.tts_site_name.tooltip")).build();
        ai.addEntry(ttsSite);

        String currentTtsModel = manager.getTtsModel();
        for (String key : AIChatScreen.CLIENT_TTS_SITES.keySet()) {
            List<String> models = AIChatScreen.CLIENT_TTS_SITES.get(key);
            if (models == null || models.isEmpty()) {
                continue;
            }
            String defaultModel = models.contains(currentTtsModel) ? currentTtsModel : StringUtils.EMPTY;
            ai.addEntry(entryBuilder.startStringDropdownMenu(
                            TComponent.translatable("config.touhou_little_maid.part_ai.tts_model"), defaultModel)
                    .setDefaultValue(StringUtils.EMPTY).setSelections(models)
                    .setTooltip(TComponent.translatable("config.touhou_little_maid.part_ai.tts_model.tooltip"))
                    .setSaveConsumer(result -> {
                        if (StringUtils.isNotBlank(ttsSite.getValue()) && ttsSite.getValue().equals(key)) {
                            manager.setTtsModel(result);
                        }
                    })
                    .setDisplayRequirement(() -> StringUtils.isNotBlank(ttsSite.getValue())
                            && ttsSite.getValue().equals(key)).build());
        }

        SortedMap<String, LanguageInfo> languages = transformLanguageInfo();
        String defaultLanguage = StringUtils.isBlank(manager.getTtsLanguage())
                ? LanguageManager.DEFAULT_LANGUAGE_CODE : manager.getTtsLanguage();
        ai.addEntry(entryBuilder.startStringDropdownMenu(TComponent.translatable("config.touhou_little_maid.part_ai.tts_language"),
                        defaultLanguage, TComponent::literal, cell(languages)).setSelections(languages.keySet())
                .setDefaultValue(LanguageManager.DEFAULT_LANGUAGE_CODE)
                .setTooltip(TComponent.translatable("config.touhou_little_maid.part_ai.tts_language.tooltip"))
                .setSaveConsumer(manager::setTtsLanguage).build());
    }

    private static DropdownBoxEntry.SelectionCellCreator<String> cell(SortedMap<String, LanguageInfo> languages) {
        LanguageInfo defaultLanguage = languages.get(LanguageManager.DEFAULT_LANGUAGE_CODE);
        return new DropdownBoxEntry.DefaultSelectionCellCreator<>(code -> {
            LanguageInfo info = languages.getOrDefault(code, defaultLanguage);
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

