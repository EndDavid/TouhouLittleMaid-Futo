package com.github.tartaricacid.touhoulittlemaid.ai.manager.entity;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.AvailableSites;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.CharacterSetting;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.SettingReader;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.Site;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.AIConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.CappedQueue;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import java.util.Optional;
/**
 * 服务端的聊天状态：站点回退、模型回退、温度回退、历史队列。
 * 移植自上游 {@code ai/manager/entity/MaidAIChatData}。
 *
 * <p>几处「回退」都是上游的既定行为，别当成 bug：</p>
 * <ul>
 *     <li>站点名为空或已不存在 → 用第一个<b>已启用</b>的站点（{@code enabled: true}）；</li>
 *     <li>模型名为空 → 用该站点模型列表的第一个；</li>
 *     <li>温度 &lt; 0（默认 -1）→ 用全局 {@code AIConfig.CHAT_TEMPERATURE}。</li>
 * </ul>
 */
public abstract class MaidAIChatData extends MaidAIDataSerializable {
    protected final EntityMaid maid;
    protected final CappedQueue<HistoryChat> history;
    public MaidAIChatData(EntityMaid maid) {
        this.maid = maid;
        this.history = new CappedQueue<>(maxHistorySize());
    }
    /**
     * 历史长度上限，读不到配置时退化为 16。
     *
     * <p>为什么要防御：女仆实体在<b>注册阶段的原型构造</b>里就会被创建
     * （见 {@code EntityMaid#defineSynchedData} 的注释），那时配置可能还没加载，
     * {@code ConfigValue#get()} 会抛 {@code IllegalStateException}。
     * 上游没有这个问题是因为它的管理器在真实实体构造时才建 —— 本移植版为了稳妥
     * 把这里包一层，默认值与配置里的默认值一致。</p>
     */
    private static int maxHistorySize() {
        try {
            if (AIConfig.MAID_MAX_HISTORY_CHAT_SIZE != null) {
                Integer value = AIConfig.MAID_MAX_HISTORY_CHAT_SIZE.get();
                if (value != null && value > 0) {
                    return value;
                }
            }
        } catch (Exception ignored) {
            // 配置尚未加载：用默认值
        }
        return 16;
    }
    @Nullable
    public Site getChatSite() {
        Site site;
        if (StringUtils.isBlank(chatSiteName)) {
            site = AvailableSites.getFirstAvailableChatSite();
        } else {
            site = AvailableSites.getChatSite(chatSiteName);
            if (site == null) {
                site = AvailableSites.getFirstAvailableChatSite();
            }
        }
        return site;
    }
    @Nullable
    public Site getTtsSite() {
        Site site;
        if (StringUtils.isBlank(ttsSiteName)) {
            site = AvailableSites.getFirstAvailableTtsSite();
        } else {
            site = AvailableSites.getTtsSite(ttsSiteName);
            if (site == null) {
                site = AvailableSites.getFirstAvailableTtsSite();
            }
        }
        return site;
    }
    @Override
    public String getChatModel() {
        Site site = getChatSite();
        String model = StringUtils.EMPTY;
        if (site != null && !site.getModels().isEmpty()) {
            if (StringUtils.isBlank(chatModel)) {
                model = site.getModels().get(0);
            } else {
                model = chatModel;
            }
        }
        return model;
    }
    @Override
    public String getTtsModel() {
        Site site = getTtsSite();
        String model = StringUtils.EMPTY;
        if (site != null && !site.getModels().isEmpty()) {
            if (StringUtils.isBlank(ttsModel)) {
                model = site.getModels().get(0);
            } else {
                model = ttsModel;
            }
        }
        return model;
    }
    @Override
    public double getChatTemperature() {
        if (chatTemperature >= 0) {
            return chatTemperature;
        }
        try {
            if (AIConfig.CHAT_TEMPERATURE != null) {
                return AIConfig.CHAT_TEMPERATURE.get();
            }
        } catch (Exception ignored) {
            // 配置尚未加载：用默认值
        }
        return 0.5D;
    }
    @Override
    public String getTtsLanguage() {
        if (StringUtils.isNotBlank(ttsLanguage)) {
            return ttsLanguage;
        }
        try {
            if (AIConfig.TTS_LANGUAGE != null) {
                return AIConfig.TTS_LANGUAGE.get();
            }
        } catch (Exception ignored) {
            // 配置尚未加载：用默认值
        }
        return "en_us";
    }
    public CappedQueue<HistoryChat> getHistory() {
        return history;
    }
    public void addUserHistory(String message) {
        this.history.add(HistoryChat.userChat(maid, message));
    }
    public void addAssistantHistory(String message) {
        this.history.add(HistoryChat.assistantChat(maid, message));
    }
    public EntityMaid getMaid() {
        return maid;
    }
    public Optional<CharacterSetting> getSetting() {
        String modelId = this.maid.getModelId();
        return SettingReader.getSetting(modelId);
    }
}
