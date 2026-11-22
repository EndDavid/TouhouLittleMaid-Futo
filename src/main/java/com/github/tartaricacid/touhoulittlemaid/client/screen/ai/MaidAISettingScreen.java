package com.github.tartaricacid.touhoulittlemaid.client.screen.ai;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIDataSerializable;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.FlatColorButton;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.SaveMaidAIDataPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 单只女仆的 AI 设置界面（手写版），对应上游 compat/cloth/PartAIIntegration。
 *
 * <p>上游用 Cloth Config 的条目系统做这个界面；本移植版没有移植那套集成，
 * 因此这里用最普通的 Screen 实现同样的几项设置：</p>
 * <ul>
 *     <li>主人称呼（会替换系统设定里的 ${owner_name}）</li>
 *     <li>自定义设定（会替换 ${custom_setting}）</li>
 *     <li>聊天站点 / 聊天模型（空 = 跟随默认：第一个填了 key 的站点与它的第一个模型）</li>
 *     <li>TTS 站点 / TTS 模型（本轮不播放语音，但字段保留，与上游存档格式一致）</li>
 *     <li>聊天温度（-1 = 跟随全局配置）</li>
 * </ul>
 *
 * <p>站点与模型的候选项来自 SyncAiSettingPacket 发下来的两张表，
 * 因此它们只包含填了 api_key 的站点，不会给出必然失败的选项。</p>
 *
 * <p><b>保存是整份覆盖</b>（与上游 setSavingRunnable 一致）：界面上没有的字段
 * 会保持打开界面时收到的值，不会因为漏写而清零。</p>
 */
public class MaidAISettingScreen extends Screen {
    private static final String EMPTY_LABEL = "-";
    private static final double[] TEMPERATURES = {-1.0D, 0.0D, 0.2D, 0.5D, 0.7D, 1.0D, 1.3D, 1.6D, 1.9D};

    private final EntityMaid maid;
    private final MaidAIDataSerializable data;

    private EditBox ownerNameField;
    private EditBox customSettingField;
    private FlatColorButton chatSiteButton;
    private FlatColorButton chatModelButton;
    private FlatColorButton ttsSiteButton;
    private FlatColorButton ttsModelButton;
    private FlatColorButton temperatureButton;

    /** 当前选中的站点名，空串表示「跟随默认」 */
    private String chatSite;
    private String chatModel;
    private String ttsSite;
    private String ttsModel;
    private double temperature;

    public MaidAISettingScreen(EntityMaid maid, MaidAIDataSerializable data) {
        super(new TranslatableComponent("config.touhou_little_maid.part_ai"));
        this.maid = maid;
        this.data = data;
        this.chatSite = StringUtils.defaultString(data.getChatSiteName());
        this.chatModel = StringUtils.defaultString(data.getChatModel());
        this.ttsSite = StringUtils.defaultString(data.getTtsSiteName());
        this.ttsModel = StringUtils.defaultString(data.getTtsModel());
        this.temperature = data.getChatTemperature();
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 150;
        int y = this.height / 2 - 70;

        this.ownerNameField = new EditBox(this.font, left + 90, y, 210, 18,
                new TranslatableComponent("config.touhou_little_maid.part_ai.owner_name"));
        this.ownerNameField.setMaxLength(64);
        this.ownerNameField.setValue(StringUtils.defaultString(data.getOwnerName()));
        this.addRenderableWidget(this.ownerNameField);
        y += 24;

        this.customSettingField = new EditBox(this.font, left + 90, y, 210, 18,
                new TranslatableComponent("config.touhou_little_maid.part_ai.custom_setting"));
        this.customSettingField.setMaxLength(512);
        this.customSettingField.setValue(StringUtils.defaultString(data.getCustomSetting()));
        this.addRenderableWidget(this.customSettingField);
        y += 24;

        this.chatSiteButton = new FlatColorButton(left + 90, y, 210, 18, new TextComponent(""),
                b -> {
                    this.chatSite = cycle(new ArrayList<>(AIChatScreen.CLIENT_CHAT_SITES.keySet()), this.chatSite);
                    this.chatModel = "";
                    refreshLabels();
                });
        this.addRenderableWidget(this.chatSiteButton);
        y += 24;

        this.chatModelButton = new FlatColorButton(left + 90, y, 210, 18, new TextComponent(""),
                b -> {
                    this.chatModel = cycle(chatModels(), this.chatModel);
                    refreshLabels();
                });
        this.addRenderableWidget(this.chatModelButton);
        y += 24;

        this.ttsSiteButton = new FlatColorButton(left + 90, y, 210, 18, new TextComponent(""),
                b -> {
                    this.ttsSite = cycle(new ArrayList<>(AIChatScreen.CLIENT_TTS_SITES.keySet()), this.ttsSite);
                    this.ttsModel = "";
                    refreshLabels();
                });
        this.addRenderableWidget(this.ttsSiteButton);
        y += 24;

        this.ttsModelButton = new FlatColorButton(left + 90, y, 210, 18, new TextComponent(""),
                b -> {
                    this.ttsModel = cycle(ttsModels(), this.ttsModel);
                    refreshLabels();
                });
        this.addRenderableWidget(this.ttsModelButton);
        y += 24;

        this.temperatureButton = new FlatColorButton(left + 90, y, 210, 18, new TextComponent(""),
                b -> {
                    this.temperature = nextTemperature(this.temperature);
                    refreshLabels();
                });
        this.addRenderableWidget(this.temperatureButton);
        y += 32;

        this.addRenderableWidget(new FlatColorButton(left + 90, y, 100, 20,
                new TranslatableComponent("gui.done"), b -> saveAndClose()));
        this.addRenderableWidget(new FlatColorButton(left + 200, y, 100, 20,
                new TranslatableComponent("gui.cancel"), b -> this.onClose()));

        refreshLabels();
    }

    private List<String> chatModels() {
        List<String> models = AIChatScreen.CLIENT_CHAT_SITES.get(this.chatSite);
        return models == null ? new ArrayList<>() : new ArrayList<>(models);
    }

    private List<String> ttsModels() {
        List<String> models = AIChatScreen.CLIENT_TTS_SITES.get(this.ttsSite);
        return models == null ? new ArrayList<>() : new ArrayList<>(models);
    }

    private void refreshLabels() {
        this.chatSiteButton.setMessage(new TextComponent(
                "站点: " + (StringUtils.isBlank(chatSite) ? EMPTY_LABEL : chatSite)));
        this.chatModelButton.setMessage(new TextComponent(
                "模型: " + (StringUtils.isBlank(chatModel) ? EMPTY_LABEL : chatModel)));
        this.ttsSiteButton.setMessage(new TextComponent(
                "TTS 站点: " + (StringUtils.isBlank(ttsSite) ? EMPTY_LABEL : ttsSite)));
        this.ttsModelButton.setMessage(new TextComponent(
                "TTS 模型: " + (StringUtils.isBlank(ttsModel) ? EMPTY_LABEL : ttsModel)));
        String tempText = temperature < 0 ? "跟随全局" : String.format("%.1f", temperature);
        this.temperatureButton.setMessage(new TextComponent("温度: " + tempText));
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        // 半透明底：输入框与按钮在明亮背景上不容易看清
        fill(poseStack, 0, 0, this.width, this.height, 0xc0_000000);
        drawCenteredString(poseStack, this.font, this.title, this.width / 2, this.height / 2 - 92, 0xFFFFFF);
        int left = this.width / 2 - 150;
        int y = this.height / 2 - 70 + 5;
        drawString(poseStack, this.font, "主人称呼", left, y, 0xFFFFFF);
        y += 24;
        drawString(poseStack, this.font, "自定义设定", left, y, 0xFFFFFF);
        y += 24;
        drawString(poseStack, this.font, "聊天站点", left, y, 0xFFFFFF);
        y += 24;
        drawString(poseStack, this.font, "聊天模型", left, y, 0xFFFFFF);
        y += 24;
        drawString(poseStack, this.font, "TTS 站点", left, y, 0xFFFFFF);
        y += 24;
        drawString(poseStack, this.font, "TTS 模型", left, y, 0xFFFFFF);
        y += 24;
        drawString(poseStack, this.font, "聊天温度", left, y, 0xFFFFFF);
        super.render(poseStack, mouseX, mouseY, partialTicks);
    }

    private void saveAndClose() {
        data.setChatSiteName(StringUtils.defaultString(chatSite));
        data.setChatModel(StringUtils.defaultString(chatModel));
        data.setTtsSiteName(StringUtils.defaultString(ttsSite));
        data.setTtsModel(StringUtils.defaultString(ttsModel));
        data.setChatTemperature(temperature);
        data.setOwnerName(StringUtils.defaultString(ownerNameField.getValue()));
        data.setCustomSetting(StringUtils.defaultString(customSettingField.getValue()));
        SaveMaidAIDataPacket.send(this.maid.getId(), data);
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 在候选列表里往后挪一位；当前值不在列表里（或为空）时回到第一项，
     * 到底之后回到空串（= 跟随默认）。空串也参与循环，这样玩家能改回去。
     */
    private static String cycle(List<String> options, String current) {
        List<String> ring = new ArrayList<>();
        ring.add("");
        ring.addAll(options);
        int index = ring.indexOf(current);
        if (index < 0) {
            return ring.isEmpty() ? "" : ring.get(0);
        }
        return ring.get((index + 1) % ring.size());
    }

    private static double nextTemperature(double current) {
        int index = 0;
        for (int i = 0; i < TEMPERATURES.length; i++) {
            if (Math.abs(TEMPERATURES[i] - current) < 0.01D) {
                index = i;
                break;
            }
        }
        return TEMPERATURES[(index + 1) % TEMPERATURES.length];
    }
}