package com.github.tartaricacid.touhoulittlemaid.client.screen.ai;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.FlatColorButton;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.GetMaidAISettingPacket;
import com.github.tartaricacid.touhoulittlemaid.network.RegenerateAISettingPacket;
import com.github.tartaricacid.touhoulittlemaid.network.SyncMaidAISettingPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
/**
 * 角色设定正文（只读）+ 重新生成 / 复制台本。
 *
 * <p><b>它不是 1.20.1 的 {@code SettingEditScreen}</b> —— 那个界面（主人称呼 + 自定义设定）
 * 见 {@link AISettingEditScreen}。这里显示的是模型包里那份 <b>yml 台本</b>
 * （{@code config/touhou_little_maid/settings/<模型>.yml} 的正文），
 * 由服务端现发（{@code GetMaidAISettingPacket} → {@code SyncMaidAISettingPacket}）。</p>
 *
 * <p>上游没有这个界面：1.20.1 里只能自己去改 yml。本移植版保留它是因为</p>
 * <ul>
 *     <li>自动生成的人设写歪了（例如被 max_tokens 截断）时，玩家需要看到正文才知道；</li>
 *     <li>「重新生成人设」需要一个入口 —— 上游是靠删文件 + 说话触发的。</li>
 * </ul>
 * <p>两者的按钮都有悬浮提示，避免与 {@link AISettingEditScreen} 的「导出设定」混淆。</p>
 */
public class AISettingBodyScreen extends Screen {
    private static final int LINE_HEIGHT = 10;
    private static final int PADDING = 24;
    private final EntityMaid maid;
    @Nullable
    private final Screen parent;
    private final List<String> settingLines = new ArrayList<>();
    public AISettingBodyScreen(EntityMaid maid) {
        this(null, maid);
    }
    public AISettingBodyScreen(@Nullable Screen parent, EntityMaid maid) {
        super(new TranslatableComponent("ai.touhou_little_maid.chat.setting.title"));
        this.parent = parent;
        this.maid = maid;
    }
    @Override
    protected void init() {
        int buttonY = this.height - 30;
        int buttonWidth = 108;
        int gap = 4;
        int total = buttonWidth * 3 + gap * 2;
        int x = this.width / 2 - total / 2;
        this.addRenderableWidget(new FlatColorButton(x, buttonY, buttonWidth, 20,
                new TranslatableComponent("ai.touhou_little_maid.chat.setting.regenerate"), b -> this.regenerate())
                .setTooltips("ai.touhou_little_maid.chat.setting.regenerate.tip"));
        this.addRenderableWidget(new FlatColorButton(x + buttonWidth + gap, buttonY, buttonWidth, 20,
                new TranslatableComponent("ai.touhou_little_maid.chat.setting.export"), b -> this.copyToClipboard())
                .setTooltips("ai.touhou_little_maid.chat.setting.export.tip"));
        this.addRenderableWidget(new Button(x + (buttonWidth + gap) * 2, buttonY, buttonWidth, 20,
                new TranslatableComponent("gui.back"), b -> this.onClose()));
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
            List<FormattedText> parts = this.font.getSplitter().splitLines(
                    new TextComponent(line), this.width - PADDING * 2, Style.EMPTY);
            for (FormattedText part : parts) {
                this.settingLines.add(part.getString());
            }
        }
    }
    private void regenerate() {
        RegenerateAISettingPacket.send(this.maid.getId(), currentLanguage(), this.maid.getName().getString());
    }
    /**
     * 复制台本：把正文包成一份合法的 settings yml 复制到剪贴板。
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
        drawString(poseStack, this.font, new TranslatableComponent("ai.touhou_little_maid.chat.setting.title"),
                PADDING, PADDING - 14, 0xFFFFFF);
        int y = PADDING;
        if (this.settingLines.isEmpty()) {
            drawString(poseStack, this.font,
                    new TranslatableComponent("ai.touhou_little_maid.chat.llm.role_no_setting"),
                    PADDING, y, 0x999999);
        } else {
            for (String line : this.settingLines) {
                if (y > this.height - 40) {
                    drawString(poseStack, this.font, new TextComponent("..."), PADDING, y, 0x888888);
                    break;
                }
                drawString(poseStack, this.font, line, PADDING, y, 0xDDDDDD);
                y += LINE_HEIGHT;
            }
        }
        super.render(poseStack, mouseX, mouseY, partialTicks);
        this.renderButtonTooltips(poseStack, mouseX, mouseY);
    }
    private void renderButtonTooltips(PoseStack poseStack, int mouseX, int mouseY) {
        for (net.minecraft.client.gui.components.events.GuiEventListener listener : this.children()) {
            if (listener instanceof FlatColorButton button) {
                button.renderToolTip(poseStack, this, mouseX, mouseY);
            }
        }
    }
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent != null ? this.parent : new AISettingEditScreen(this.maid));
        }
    }
}
