package com.github.tartaricacid.touhoulittlemaid.client.screen.ai;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIDataSerializable;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.FlatColorButton;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.GetMaidAIDataPacket;
import com.github.tartaricacid.touhoulittlemaid.network.GetMaidAIHistoryPacket;
import com.github.tartaricacid.touhoulittlemaid.network.SaveMaidAIDataPacket;
import com.github.tartaricacid.touhoulittlemaid.network.SendUserChatPacket;
import com.github.tartaricacid.touhoulittlemaid.network.SyncChatTokensPacket;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 聊天主界面，按 TLMO 1.20.1 的版式重做（1.18.2 上游只有一个输入框）。
 *
 * <p>自上而下：Token 计数、左右各三个圆形按钮、输入条、底部两条摘要。
 * 左三为历史 / 角色设定 / 设置，右三为选大语言站点与模型 / 选语音站点与模型 / 选语种；
 * 后三个按钮弹出的是自绘可滚动列表（1.20.1 也没用原版下拉框）。</p>
 *
 * <p>按钮字形与 1.20.1 一致（时钟、齿轮、铅笔、星形、喇叭、地球），
 * 它们是 Unifont 自带的 emoji 码位，不需要额外贴图资源。</p>
 */
public class AIChatScreen extends Screen {
    /** 服务端发下来的「站点名 → 模型列表」，只有填了 api_key 的站点才会出现。 */
    public static final Map<String, List<String>> CLIENT_CHAT_SITES = Maps.newLinkedHashMap();
    public static final Map<String, List<String>> CLIENT_TTS_SITES = Maps.newLinkedHashMap();

    /** 这只女仆的 AI 设置副本，由 GetMaidAIDataPacket 的回复填充。 */
    @Nullable
    public static MaidAIDataSerializable CLIENT_DATA = null;

    /** 下次收到设置数据时是切到设置界面，还是只刷新摘要。 */
    public static boolean PENDING_SETTING_SCREEN = false;

    /** 语音合成支持的语种（对应 1.20.1 的 SupportLanguage）。 */
    public static final String[] TTS_LANGUAGES = {"en_us", "zh_cn", "ja_jp", "ko_kr", "ru_ru"};

    private static final int BUTTON_SIZE = 18;
    private static final int BUTTON_GAP = 2;
    private static final int POPUP_ROW_HEIGHT = 12;
    private static final int POPUP_MAX_ROWS = 9;
    private static final int POPUP_WIDTH = 168;

    private final EntityMaid maid;
    private EditBox input;

    @Nullable
    private PopupType openPopup = null;
    private int popupScroll = 0;
    private int popupX = 0;
    private int popupY = 0;
    private int tickCounter = 0;

    private enum PopupType {LLM, TTS, LANGUAGE}

    private record PopupEntry(String label, boolean header, Runnable action) {
    }

    public AIChatScreen(EntityMaid maid) {
        super(new TextComponent("Maid AI Chat Screen"));
        this.maid = maid;
    }

    @Override
    protected void init() {
        // 坐标逐字对齐 1.20.1 的 AIChatScreen#init：
        //   inputX = width/2 - 165, inputY = height/2 + 58, 宽 330 高 20
        //   按钮 18x18、间距 2、y = inputY - 28
        //   左三从 inputX - 8 开始；右三从 inputX + 330 - 18*3 + 2*2 开始
        int inputWidth = 330;
        int inputX = this.width / 2 - 165;
        int inputY = this.height / 2 + 58;

        this.input = new EditBox(this.font, inputX, inputY, inputWidth, 20,
                new TranslatableComponent("chat.editBox"));
        this.input.setMaxLength(512);
        this.input.setBordered(false);
        this.input.setValue("");
        this.input.setCanLoseFocus(false);
        this.addWidget(this.input);
        this.setInitialFocus(this.input);

        int buttonY = inputY - 28;
        int step = BUTTON_SIZE + BUTTON_GAP;
        // 字形全部限定在 BMP 内：1.20.1 用的 U+1F551 / U+1F50A / U+1F310 属于增补平面，
        // 1.18.2 自带的 unifont 没有这些字形，会渲染成方框。这里换成语义等价的 BMP 符号：
        // U+2261 列表 / U+2699 设定 / U+270E 设置 / U+2726 星形 / U+266A 音符 / "Aa" 语种。
        int left = inputX - 8;
        addSideButton(left, buttonY, "\u2261", this::openHistory);
        addSideButton(left + step, buttonY, "\u2699", this::openSettingEditor);
        addSideButton(left + step * 2, buttonY, "\u270E", this::openConfig);
        int right = inputX + inputWidth - BUTTON_SIZE * 3 + BUTTON_GAP * 2 + BUTTON_SIZE;
        addSideButton(right, buttonY, "Aa", () -> togglePopup(PopupType.LANGUAGE, right, buttonY));
        addSideButton(right - step, buttonY, "\u266A",
                () -> togglePopup(PopupType.TTS, right - step, buttonY));
        addSideButton(right - step * 2, buttonY, "\u2726",
                () -> togglePopup(PopupType.LLM, right - step * 2, buttonY));

        // 打开界面时拉一份设置来画底部摘要（不切界面）
        PENDING_SETTING_SCREEN = false;
        GetMaidAIDataPacket.send(this.maid.getId());
    }

    private void addSideButton(int x, int y, String glyph, Runnable action) {
        this.addRenderableWidget(new FlatColorButton(x, y, BUTTON_SIZE, BUTTON_SIZE,
                new TextComponent(glyph), b -> action.run()));
    }

    private void openHistory() {
        GetMaidAIHistoryPacket.send(this.maid.getId());
    }

    /**
     * U+2699 = 角色设定编辑器（1.20.1 的 SettingEditScreen）：人设正文 / 主人称呼 / 自定义设定。
     */
    private void openSettingEditor() {
        Minecraft.getInstance().setScreen(new AISettingEditScreen(this.maid));
    }
    /**
     * U+270E = 设置：站点管理（对应 1.20.1 的 AIChatSettingsHubScreen）。
     *
     * <p>站点的新增 / 编辑 / 删除都在这里面，改完会写回 available_sites.yml。</p>
     */
    private void openConfig() {
        Minecraft.getInstance().setScreen(new AISiteListScreen(this.maid));
    }
    /**
     * 每只女仆的站点 / 模型 / 温度选择（原 ✎ 的用途，现在直接在界面里选）。
     *
     * <p>保留这个入口：右三按钮的弹层已经能改站点与模型，这个界面用于一次性看全部字段。</p>
     */
    private void openMaidConfig() {
        PENDING_SETTING_SCREEN = true;
        GetMaidAIDataPacket.send(this.maid.getId());
    }

    private void togglePopup(PopupType type, int anchorX, int anchorY) {
        if (this.openPopup == type) {
            this.openPopup = null;
            return;
        }
        List<PopupEntry> entries = this.buildEntries(type);
        if (entries.isEmpty()) {
            this.openPopup = null;
            return;
        }
        this.openPopup = type;
        this.popupScroll = 0;
        this.popupX = Math.max(4, Math.min(anchorX, this.width - POPUP_WIDTH - 4));
        this.popupY = anchorY - 6 - Math.min(entries.size(), POPUP_MAX_ROWS) * POPUP_ROW_HEIGHT;
        if (this.popupY < 4) {
            this.popupY = anchorY + BUTTON_SIZE + 6;
        }
    }

    private List<PopupEntry> buildEntries(PopupType type) {
        List<PopupEntry> entries = new ArrayList<>();
        switch (type) {
            case LLM -> CLIENT_CHAT_SITES.forEach((site, models) -> {
                entries.add(new PopupEntry(site, true, null));
                for (String model : models) {
                    entries.add(new PopupEntry("  " + model, false, () -> applySiteModel(site, model)));
                }
            });
            case TTS -> {
                entries.add(new PopupEntry("ai.touhou_little_maid.chat.site.none.name", false,
                        () -> applyTts("", "")));
                CLIENT_TTS_SITES.forEach((site, models) -> {
                    entries.add(new PopupEntry(site, true, null));
                    for (String model : models) {
                        entries.add(new PopupEntry("  " + model, false, () -> applyTts(site, model)));
                    }
                });
            }
            case LANGUAGE -> {
                for (String code : TTS_LANGUAGES) {
                    entries.add(new PopupEntry(code, false, () -> applyLanguage(code)));
                }
            }
        }
        return entries;
    }

    private void applySiteModel(String site, String model) {
        this.openPopup = null;
        MaidAIDataSerializable data = currentData();
        data.setChatSiteName(site);
        data.setChatModel(model);
        SaveMaidAIDataPacket.send(this.maid.getId(), data);
    }

    private void applyTts(String site, String model) {
        this.openPopup = null;
        MaidAIDataSerializable data = currentData();
        data.setTtsSiteName(site);
        data.setTtsModel(model);
        SaveMaidAIDataPacket.send(this.maid.getId(), data);
    }

    private void applyLanguage(String code) {
        this.openPopup = null;
        MaidAIDataSerializable data = currentData();
        data.setTtsLanguage(code);
        SaveMaidAIDataPacket.send(this.maid.getId(), data);
    }

    private MaidAIDataSerializable currentData() {
        if (CLIENT_DATA == null) {
            CLIENT_DATA = new MaidAIDataSerializable();
        }
        return CLIENT_DATA;
    }

    /** 服务端回设置数据时调用，摘要每帧现读 CLIENT_DATA，这里无需额外动作。 */
    public void refreshData() {
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        int xOffset = 5;
        int yOffset = 6;
        fill(poseStack, input.x - xOffset, input.y - yOffset,
                input.x + input.getWidth() + xOffset,
                input.y + input.getHeight() - yOffset, 0x9f_000000);
        input.render(poseStack, mouseX, mouseY, partialTicks);

        if (StringUtils.isEmpty(input.getValue())) {
            MutableComponent text = new TranslatableComponent("ai.touhou_little_maid.chat.input.tip")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            drawCenteredString(poseStack, this.font, text,
                    input.x + input.getInnerWidth() / 2 - xOffset, input.y + 2, 0xFFFFFF);
        }

        renderTokenUsage(poseStack);
        renderSummaries(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTicks);
        renderPopup(poseStack, mouseX, mouseY);
    }

    private void renderTokenUsage(PoseStack poseStack) {
        int current = SyncChatTokensPacket.CLIENT_CURRENT;
        int max = SyncChatTokensPacket.CLIENT_MAX;
        String text = max > 0
                ? String.format("Token: %s / %s (%.1f%%)", formatTokenCount(current),
                formatTokenCount(max), current * 100.0D / max)
                : String.format("Token: %s / \u221E", formatTokenCount(current));
        // 位置：输入条正上方 12px，居中，0.75 缩放（与 1.20.1 的 renderTokenUsage 一致）
        int y = this.input.y - 12;
        poseStack.pushPose();
        poseStack.translate(this.input.x + this.input.getWidth() / 2.0F, y, 0.0F);
        poseStack.scale(0.75F, 0.75F, 1.0F);
        drawCenteredString(poseStack, this.font, new TextComponent(text), 0, 0, 0xFFADADAD);
        poseStack.popPose();
    }

    private void renderSummaries(PoseStack poseStack) {
        MaidAIDataSerializable data = CLIENT_DATA;
        String llmSite = data == null ? "" : StringUtils.defaultString(data.getChatSiteName());
        String llmModel = data == null ? "" : StringUtils.defaultString(data.getChatModel());
        String ttsSite = data == null ? "" : StringUtils.defaultString(data.getTtsSiteName());
        String ttsModel = data == null ? "" : StringUtils.defaultString(data.getTtsModel());
        String ttsLang = data == null ? "" : StringUtils.defaultString(data.getTtsLanguage());

        String llm = new TranslatableComponent("ai.touhou_little_maid.chat.summary.llm",
                joinOrDash(llmSite, llmModel)).getString();
        String tts = new TranslatableComponent("ai.touhou_little_maid.chat.summary.tts",
                joinOrDash(ttsSite, ttsModel), languageName(ttsLang)).getString();

        // 输入条下方 24px，左右贴边，0.5 缩放（1.20.1 的 renderSelectionSummaries 同款）
        int y = this.input.y + 24;
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 1.0F);
        drawString(poseStack, this.font, llm, this.input.x * 2, y * 2, 0xFFADADAD);
        int ttsWidth = this.font.width(tts);
        drawString(poseStack, this.font, tts,
                (this.input.x + this.input.getWidth()) * 2 - ttsWidth, y * 2, 0xFFADADAD);
        poseStack.popPose();
    }

    private static String joinOrDash(String site, String model) {
        return (StringUtils.isBlank(site) ? "-" : site) + " / "
                + (StringUtils.isBlank(model) ? "-" : model);
    }

    /** 语种代码转显示名（en_us → English (US)），读不到就原样显示。 */
    private static String languageName(String code) {
        if (StringUtils.isBlank(code)) {
            return "-";
        }
        Locale locale = Locale.forLanguageTag(code.replace('_', '-'));
        String language = locale.getDisplayLanguage(Locale.ENGLISH);
        if (StringUtils.isBlank(language) || language.equalsIgnoreCase(code)) {
            return code;
        }
        String country = locale.getDisplayCountry(Locale.ENGLISH);
        return StringUtils.isBlank(country) ? language : language + " (" + country + ")";
    }

    private static String formatTokenCount(int count) {
        if (count < 1000) {
            return String.valueOf(count);
        }
        if (count < 1_000_000) {
            return String.format("%.1fK", count / 1000.0D);
        }
        if (count < 1_000_000_000) {
            return String.format("%.1fM", count / 1_000_000.0D);
        }
        return String.format("%.1fG", count / 1_000_000_000.0D);
    }

    private void renderPopup(PoseStack poseStack, int mouseX, int mouseY) {
        if (this.openPopup == null) {
            return;
        }
        List<PopupEntry> entries = this.buildEntries(this.openPopup);
        int visible = Math.min(entries.size(), POPUP_MAX_ROWS);
        fill(poseStack, popupX, popupY, popupX + POPUP_WIDTH,
                popupY + visible * POPUP_ROW_HEIGHT + 2, 0xf0_101010);
        for (int i = 0; i < visible; i++) {
            int index = i + popupScroll;
            if (index >= entries.size()) {
                break;
            }
            PopupEntry entry = entries.get(index);
            int rowY = popupY + 1 + i * POPUP_ROW_HEIGHT;
            boolean hover = mouseX >= popupX && mouseX <= popupX + POPUP_WIDTH
                    && mouseY >= rowY && mouseY < rowY + POPUP_ROW_HEIGHT;
            if (hover && !entry.header()) {
                fill(poseStack, popupX + 1, rowY, popupX + POPUP_WIDTH - 1,
                        rowY + POPUP_ROW_HEIGHT - 1, 0x60ffffff);
            }
            String label = entry.label();
            if (label.startsWith("ai.touhou_little_maid.")) {
                label = new TranslatableComponent(label).getString();
            }
            drawString(poseStack, this.font,
                    this.font.plainSubstrByWidth(label, POPUP_WIDTH - 6),
                    popupX + 3, rowY + 2, entry.header() ? 0xFFD700 : 0xFFFFFF);
        }
    }

    // ---------------------------------------------------------------- 输入

    @Override
    public void resize(Minecraft mc, int pWidth, int pHeight) {
        String chatText = this.input.getValue();
        super.resize(mc, pWidth, pHeight);
        this.input.setValue(chatText);
    }

    @Override
    public void tick() {
        this.input.tick();
        tickCounter++;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.openPopup != null) {
            List<PopupEntry> entries = this.buildEntries(this.openPopup);
            int visible = Math.min(entries.size(), POPUP_MAX_ROWS);
            if (mouseX >= popupX && mouseX <= popupX + POPUP_WIDTH
                    && mouseY >= popupY && mouseY <= popupY + visible * POPUP_ROW_HEIGHT) {
                int index = popupScroll + (int) ((mouseY - popupY - 1) / POPUP_ROW_HEIGHT);
                if (index >= 0 && index < entries.size()) {
                    PopupEntry entry = entries.get(index);
                    if (entry.action() != null) {
                        entry.action().run();
                    }
                    return true;
                }
            }
            this.openPopup = null;
        }
        if (this.input.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.input);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.openPopup != null) {
            int size = this.buildEntries(this.openPopup).size();
            int max = Math.max(0, size - POPUP_MAX_ROWS);
            this.popupScroll = (int) Math.max(0, Math.min(max, this.popupScroll - delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean charTyped(char pCodePoint, int pModifiers) {
        // 界面刚打开的 5 tick 内不接受输入，否则「按 T 打开」的那个 T 会被录入输入框
        if (tickCounter < 5) {
            return false;
        }
        return super.charTyped(pCodePoint, pModifiers);
    }

    @Override
    protected void insertText(String text, boolean overwrite) {
        if (overwrite) {
            this.input.setValue(text);
        } else {
            this.input.insertText(text);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.openPopup != null) {
            this.openPopup = null;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            sendDoneMessage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void sendDoneMessage() {
        String value = input.getValue();
        LocalPlayer player = Minecraft.getInstance().player;
        if (StringUtils.isNotBlank(value) && player != null) {
            LanguageManager languageManager = Minecraft.getInstance().getLanguageManager();
            LanguageInfo info = languageManager.getLanguage(languageManager.getSelected().getCode());
            String language = info != null ? info.toString() : "English (US)";
            // 名字必须在这里取：客户端才有玩家的语言文件，未命名时能解析出模型译名
            SendUserChatPacket.send(this.maid.getId(), value, language, this.maid.getName().getString());
            String name = player.getScoreboardName();
            String format = String.format("<%s> %s", name, value);
            player.sendMessage(new TextComponent(format).withStyle(ChatFormatting.GRAY), Util.NIL_UUID);
        }
        this.onClose();
    }
}