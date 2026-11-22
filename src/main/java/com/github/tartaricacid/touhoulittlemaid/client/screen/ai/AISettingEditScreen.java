package com.github.tartaricacid.touhoulittlemaid.client.screen.ai;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIDataSerializable;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.FlatColorButton;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.GetMaidAISettingPacket;
import com.github.tartaricacid.touhoulittlemaid.network.RegenerateAISettingPacket;
import com.github.tartaricacid.touhoulittlemaid.network.SaveMaidAIDataPacket;
import com.github.tartaricacid.touhoulittlemaid.network.SyncMaidAISettingPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import org.apache.commons.lang3.StringUtils;
import java.util.ArrayList;
import java.util.List;
/**
 * 角色设定编辑器，对应 1.20.1 的 SettingEditScreen。
 *
 * <p>上游这个界面做的是：显示这只女仆的人设正文、编辑主人称呼与自定义设定、
 * 把台本导出到磁盘、保存并退出。本移植版保留同样的四件事，差异是</p>
 * <ul>
 *     <li>正文只读（上游也是只读显示，改正文要改 yml）；</li>
 *     <li>「导出」改成复制到剪贴板 —— 1.18.2 走 TinyFileDialogs 要额外原生库，
 *         而剪贴板已经能拿到完整 yml 文本；</li>
 *     <li>多一个「重新生成人设」，让模型重写一份（上游靠删文件 + 说话触发）。</li>
 * </ul>
 */
public class AISettingEditScreen extends Screen {
    private static final int LINE_HEIGHT = 10;
    private static final int PADDING = 24;
    private final EntityMaid maid;
    private final List<String> settingLines = new ArrayList<>();
    private EditBox ownerNameField;
    private EditBox customSettingField;
    public AISettingEditScreen(EntityMaid maid) {
        super(new TranslatableComponent("ai.touhou_little_maid.chat.button.setting.tip"));
        this.maid = maid;
    }
    @Override
    protected void init() {
        int y = this.height - 84;
        // 主人称呼与自定义设定：与上游同样的两个输入框
        this.ownerNameField = new EditBox(this.font, PADDING, y, this.width - PADDING * 2, 18,
                new TranslatableComponent("config.touhou_little_maid.part_ai.owner_name"));
        this.ownerNameField.setMaxLength(64);
        MaidAIDataSerializable data = AIChatScreen.CLIENT_DATA;
        this.ownerNameField.setValue(data == null ? ""
                : StringUtils.defaultString(data.getOwnerName()));
        this.addRenderableWidget(this.ownerNameField);
        this.customSettingField = new EditBox(this.font, PADDING, y + 24, this.width - PADDING * 2, 18,
                new TranslatableComponent("config.touhou_little_maid.part_ai.custom_setting"));
        this.customSettingField.setMaxLength(512);
        this.customSettingField.setValue(data == null ? ""
                : StringUtils.defaultString(data.getCustomSetting()));
        this.addRenderableWidget(this.customSettingField);
        int buttonY = this.height - 30;
        int buttonWidth = 96;
        int gap = 4;
        int total = buttonWidth * 4 + gap * 3;
        int x = this.width / 2 - total / 2;
        this.addRenderableWidget(new FlatColorButton(x, buttonY, buttonWidth, 20,
                new TranslatableComponent("gui.done"), b -> save()));
        this.addRenderableWidget(new FlatColorButton(x + buttonWidth + gap, buttonY, buttonWidth, 20,
                new TranslatableComponent("ai.touhou_little_maid.chat.setting.regenerate"),
                b -> RegenerateAISettingPacket.send(this.maid.getId(), currentLanguage())));
        this.addRenderableWidget(new FlatColorButton(x + (buttonWidth + gap) * 2, buttonY, buttonWidth, 20,
                new TranslatableComponent("ai.touhou_little_maid.chat.setting.export"),
                b -> copyToClipboard()));
        this.addRenderableWidget(new Button(x + (buttonWidth + gap) * 3, buttonY, buttonWidth, 20,
                new TranslatableComponent("gui.cancel"), b -> this.onClose()));
        // 正文由服务端现发（初始为空，收到后 refresh）
        GetMaidAISettingPacket.send(this.maid.getId());
        this.rebuild();
    }
    /** 正文到达时重排。 */
    public void refresh() {
        this.rebuild();
    }
    private void rebuild() {
        this.settingLines.clear();
        String raw = StringUtils.defaultString(SyncMaidAISettingPacket.CLIENT_SETTING);
        if (StringUtils.isBlank(raw)) {
            return;
        }
        for (String line : raw.split("\n")) {
            for (net.minecraft.network.chat.FormattedText part : this.font.getSplitter().splitLines(
                    new TextComponent(line), this.width - PADDING * 2,
                    net.minecraft.network.chat.Style.EMPTY)) {
                this.settingLines.add(part.getString());
            }
        }
    }
    private void save() {
        MaidAIDataSerializable data = AIChatScreen.CLIENT_DATA;
        if (data == null) {
            data = new MaidAIDataSerializable();
        }
        data.setOwnerName(StringUtils.defaultString(this.ownerNameField.getValue()));
        data.setCustomSetting(StringUtils.defaultString(this.customSettingField.getValue()));
        SaveMaidAIDataPacket.send(this.maid.getId(), data);
        this.onClose();
    }
    /**
     * 导出：把正文包成一份合法的 settings yml 复制到剪贴板。
     *
     * <p>结构与手写文件一致，粘到 {@code config/touhou_little_maid/settings/} 下即可用。</p>
     */
    private void copyToClipboard() {
        String raw = StringUtils.defaultString(SyncMaidAISettingPacket.CLIENT_SETTING);
        StringBuilder builder = new StringBuilder();
        builder.append("meta:\n");
        builder.append("  author: ").append(Minecraft.getInstance().player == null ? "player"
                : Minecraft.getInstance().player.getScoreboardName()).append('\n');
        builder.append("  model_id: [").append(this.maid.getModelId()).append("]\n");
        builder.append("setting: |\n");
        for (String line : raw.split("\n")) {
            builder.append("  ").append(line).append('\n');
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(builder.toString());
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    new TranslatableComponent("ai.touhou_little_maid.chat.setting.export.success"), false);
        }
    }
    private static String currentLanguage() {
        LanguageManager manager = Minecraft.getInstance().getLanguageManager();
        LanguageInfo info = manager.getLanguage(manager.getSelected().getCode());
        return info != null ? info.toString() : "English (US)";
    }
    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(poseStack);
        int y = PADDING;
        drawString(poseStack, this.font,
                new TranslatableComponent("ai.touhou_little_maid.chat.setting.title"),
                PADDING, y - 14, 0xFFFFFF);
        if (this.settingLines.isEmpty()) {
            drawString(poseStack, this.font,
                    new TranslatableComponent("ai.touhou_little_maid.chat.llm.role_no_setting"),
                    PADDING, y, 0x999999);
        } else {
            for (String line : this.settingLines) {
                if (y > this.height - 110) {
                    drawString(poseStack, this.font, new TextComponent("..."),
                            PADDING, y, 0x888888);
                    break;
                }
                drawString(poseStack, this.font, line, PADDING, y, 0xDDDDDD);
                y += LINE_HEIGHT;
            }
        }
        super.render(poseStack, mouseX, mouseY, partialTicks);
    }
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
