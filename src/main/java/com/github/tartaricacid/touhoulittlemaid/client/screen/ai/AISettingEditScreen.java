package com.github.tartaricacid.touhoulittlemaid.client.screen.ai;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIDataSerializable;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.MultiLineEditBox;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.FlatColorButton;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.SaveMaidAIDataPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.Mth;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
/**
 * 单只女仆的「局部 AI 设定」界面，按 TLMO 1.20.1 的 {@code SettingEditScreen} 重做。
 *
 * <h2>版式（与 1.20.1 逐项对齐）</h2>
 * <pre>
 *  主人称呼                                     [ 导出设定 ]
 *  [______________________________]              [   保存   ]
 *  自定义设定                                    [ 保存并退出 ]
 *  +--------------------------------+            [   返回   ]
 *  |  可换行、可滚动、上限 4096 字   |            [查看角色设定]
 *  |                                |
 *  +--------------------------------+
 * </pre>
 * <p>左边是两项设置（主人称呼 = 女仆怎么称呼你；自定义设定 = 填进 prompt 的
 * {@code ${custom_setting}}），右边一列按钮。左上角的「主人称呼」与「自定义设定」
 * 是 {@code EditBox} 与 {@link MultiLineEditBox}。</p>
 *
 * <h2>与 1.20.1 的差异</h2>
 * <ul>
 *     <li><b>没有「导出设定」的原生文件对话框</b>：上游用 LWJGL 的 TinyFileDialogs 弹
 *         系统保存框，1.18.2 这边要多拉一个原生库；改成把同样的 yml 文本复制到剪贴板，
 *         玩家自己粘到 {@code config/touhou_little_maid/settings/} 下即可。</li>
 *     <li><b>没有女仆立绘预览</b>：上游在右下方画一只女仆，这里留空（省一次实体渲染，
 *         也不影响功能）。</li>
 *     <li><b>多一个「查看角色设定」</b>：打开 {@link AISettingBodyScreen}，看模型包里那份
 *         yml 的正文并可以重新生成 —— 上游没有这个入口（只能自己改文件）。</li>
 * </ul>
 */
public class AISettingEditScreen extends Screen {
    /** 「设定已保存」淡出时长（毫秒）。 */
    private static final long MAX_TIP_TIME = 2000;
    private final EntityMaid maid;
    @Nullable
    private final Screen parent;
    private EditBox ownerName;
    private MultiLineEditBox customSetting;
    private long tipTimestamp = -1;
    public AISettingEditScreen(EntityMaid maid) {
        this(null, maid);
    }
    public AISettingEditScreen(@Nullable Screen parent, EntityMaid maid) {
        super(new TranslatableComponent("config.touhou_little_maid.part_ai"));
        this.parent = parent;
        this.maid = maid;
    }
    @Override
    protected void init() {
        this.clearWidgets();
        int posX = this.width / 2 - 195;
        int boxWidth = 256;
        MaidAIDataSerializable data = AIChatScreen.CLIENT_DATA;
        this.ownerName = this.addRenderableWidget(new EditBox(this.font, posX + 1, 30, boxWidth - 2, 20,
                new TranslatableComponent("gui.touhou_little_maid.button.maid_ai_chat_config.owner_name")));
        this.ownerName.setMaxLength(128);
        this.ownerName.setValue(data == null ? StringUtils.EMPTY : StringUtils.defaultString(data.getOwnerName()));
        this.customSetting = this.addRenderableWidget(new MultiLineEditBox(this.font, posX, 70, boxWidth,
                Math.max(40, this.height - 100),
                new TranslatableComponent("gui.touhou_little_maid.button.maid_ai_chat_config.edit_custom_setting.edit"),
                new TextComponent("Custom Setting Box")));
        this.customSetting.setCharacterLimit(4096);
        this.customSetting.setValue(data == null ? StringUtils.EMPTY : StringUtils.defaultString(data.getCustomSetting()));
        // 每次改动都写回客户端副本；真正落盘只发生在「保存 / 保存并退出」
        this.customSetting.setValueListener(value -> {
            MaidAIDataSerializable current = AIChatScreen.CLIENT_DATA;
            if (current != null) {
                current.setCustomSetting(StringUtils.defaultString(value));
            }
        });
        MutableComponent export =
                new TranslatableComponent("gui.touhou_little_maid.button.maid_ai_chat_config.edit_custom_setting.export");
        int buttonX = posX + 265;
        int buttonY = this.ownerName.y;
        this.addRenderableWidget(new FlatColorButton(buttonX, buttonY, 128, 20, export, b -> this.exportSetting())
                .setTooltips("gui.touhou_little_maid.button.maid_ai_chat_config.edit_custom_setting.export.tooltip"));
        this.addRenderableWidget(new FlatColorButton(buttonX, this.customSetting.y, 128, 20,
                new TranslatableComponent("selectWorld.edit.save"), b -> {
                    this.saveConfig();
                    this.tipTimestamp = System.currentTimeMillis();
                }));
        this.addRenderableWidget(new FlatColorButton(buttonX, this.customSetting.y + 25, 128, 20,
                new TranslatableComponent("gui.touhou_little_maid.button.maid_ai_chat_config.edit_custom_setting.save_and_quit"),
                b -> {
                    this.saveConfig();
                    this.onClose();
                }));
        this.addRenderableWidget(new FlatColorButton(buttonX, this.customSetting.y + 50, 128, 20,
                new TranslatableComponent("gui.back"), b -> this.onClose()));
        this.addRenderableWidget(new FlatColorButton(buttonX, this.customSetting.y + 75, 128, 20,
                new TranslatableComponent("ai.touhou_little_maid.chat.setting.title"),
                b -> Minecraft.getInstance().setScreen(new AISettingBodyScreen(this, this.maid)))
                .setTooltips("ai.touhou_little_maid.chat.setting.title.tip"));
        // 打开界面时先拿一份最新的设置（AIChatScreen 也会拉，但玩家可能直接从别处进来）
        com.github.tartaricacid.touhoulittlemaid.network.GetMaidAIDataPacket.send(this.maid.getId());
    }
    /**
     * 「导出设定」：把自定义设定包成一份合法的 settings yml 复制到剪贴板。
     *
     * <p>与上游 {@code exportSetting} 写出的结构一致（{@code meta} + {@code setting}），
     * 唯一区别是目的地从「系统保存对话框选的文件」变成剪贴板。</p>
     */
    private void exportSetting() {
        String author = Minecraft.getInstance().player == null
                ? "player" : Minecraft.getInstance().player.getScoreboardName();
        String body = StringUtils.defaultString(this.customSetting.getValue());
        StringBuilder builder = new StringBuilder();
        builder.append("meta:\n");
        builder.append("  author: ").append(author).append('\n');
        builder.append("  model_id: [").append(this.maid.getModelId()).append("]\n");
        builder.append("setting: |\n");
        for (String line : body.split("\n")) {
            builder.append("  ").append(line).append('\n');
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(builder.toString());
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    new TranslatableComponent("gui.touhou_little_maid.button.maid_ai_chat_config.edit_custom_setting.export.clipboard"),
                    false);
        }
    }
    private void saveConfig() {
        MaidAIDataSerializable data = AIChatScreen.CLIENT_DATA;
        if (data == null) {
            // 设置数据还没到（刚打开就点了保存）：什么都不做，避免把其他字段清空
            return;
        }
        data.setOwnerName(StringUtils.defaultString(this.ownerName.getValue()));
        data.setCustomSetting(StringUtils.defaultString(this.customSetting.getValue()));
        SaveMaidAIDataPacket.send(this.maid.getId(), data);
    }
    @Override
    public void tick() {
        this.ownerName.tick();
        super.tick();
    }
    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String ownerNameValue = this.ownerName.getValue();
        String customSettingValue = this.customSetting.getValue();
        super.resize(minecraft, width, height);
        this.ownerName.setValue(ownerNameValue);
        this.customSetting.setValue(customSettingValue);
    }
    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTicks);
        drawString(poseStack, this.font,
                new TranslatableComponent("gui.touhou_little_maid.button.maid_ai_chat_config.owner_name"),
                this.ownerName.x + 2, this.ownerName.y - 12, 0xFFFFFF);
        drawString(poseStack, this.font,
                new TranslatableComponent("gui.touhou_little_maid.button.maid_ai_chat_config.custom_setting"),
                this.customSetting.x + 2, this.customSetting.y - 12, 0xFFFFFF);
        this.renderSavedTip(poseStack);
        this.renderButtonTooltips(poseStack, mouseX, mouseY);
    }
    /** 「设定已保存」：按正弦淡出，与上游 {@code render} 里那段算式一致。 */
    private void renderSavedTip(PoseStack poseStack) {
        long time = System.currentTimeMillis() - this.tipTimestamp;
        if (time >= MAX_TIP_TIME || this.tipTimestamp < 0) {
            return;
        }
        double value = (double) time / MAX_TIP_TIME * Math.PI;
        int alpha = Mth.clamp((int) (Math.sin(value) * 0xFF), 15, 240);
        drawCenteredString(poseStack, this.font,
                new TranslatableComponent("gui.touhou_little_maid.button.maid_ai_chat_config.edit_custom_setting.saved"),
                this.customSetting.x + this.customSetting.getWidth() + 73,
                this.customSetting.y - 12, (alpha << 24) + 0xFF1111);
    }
    private void renderButtonTooltips(PoseStack poseStack, int mouseX, int mouseY) {
        for (net.minecraft.client.gui.components.events.GuiEventListener listener : this.children()) {
            if (listener instanceof FlatColorButton button) {
                button.renderToolTip(poseStack, this, mouseX, mouseY);
            }
        }
    }
    // ---------------------------------------------------------------- 输入转发
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.customSetting.isActive() && this.customSetting.charTyped(codePoint)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.customSetting.isActive() && this.customSetting.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 先让多行框判断（它需要「点在自己身上」才接管），接管成功就不再把事件往后传
        boolean wasActive = this.customSetting.isActive();
        this.customSetting.setActive(true);
        if (this.customSetting.mouseClickedInBox(mouseX, mouseY)) {
            return true;
        }
        // 点在别处：交还焦点给原版控件（EditBox 会自己抢焦点）
        this.customSetting.setActive(false);
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        return handled || wasActive;
    }
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent != null ? this.parent : new AIChatScreen(this.maid));
        }
    }
}
