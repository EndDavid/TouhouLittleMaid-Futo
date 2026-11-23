package com.github.tartaricacid.touhoulittlemaid.client.screen.ai;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.service.Service;
import com.github.tartaricacid.touhoulittlemaid.ai.service.chat.openai.request.Role;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.FlatColorButton;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.HistoryChatWidget;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.ClearMaidAIChatHistoryPacket;
import com.github.tartaricacid.touhoulittlemaid.network.SyncMaidAIHistoryPacket;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import java.util.List;
/**
 * 「女仆历史 AI 聊天记录」界面，按 TLMO 1.20.1 的 {@code HistoryAIChatScreen} 重做。
 *
 * <h2>版式（与 1.20.1 逐项对齐）</h2>
 * <pre>
 *                     女仆历史 AI 聊天记录
 *   [头像] ┌--------------┐           ┌----------┐
 *          │ 女仆说的话   │           │ 压缩摘要  │
 *          └--------------┘           │          │
 *                        ┌----------┐ │ （正文）  │
 *                        │ 玩家的问 │ └----------┘
 *                        └----------┘ [清除历史聊天记录]
 *   [头像] ┌--------------┐           [   返回   ]
 *          │ 女仆说的话   │
 *          └--------------┘
 * </pre>
 * <p>气泡按时间从旧到新向下排，女仆侧在左（灰底 + 头像），玩家侧在右（蓝底 + 皮肤头），
 * 每条纹一条「x天前 / x小时前」。右侧是压缩摘要面板与两个按钮。</p>
 *
 * <h2>数据来源</h2>
 * <p>1.20.1 把整份聊天管理器同步到客户端，历史直接在本地读；本移植版只在打开界面时
 * 现要一份快照（{@code GetMaidAIHistoryPacket} → {@link SyncMaidAIHistoryPacket}），
 * 因此 {@code init} 里要先建 widget，收到快照后 {@link #refresh()} 重建。</p>
 *
 * <h2>与 1.20.1 的差异</h2>
 * <ul>
 *     <li><b>没有「压缩摘要」的内容</b>：上游的 {@code HistorySummaryManager} 会定期把
 *         长对话压成摘要（另一次 LLM 请求），本移植版还没做，所以这个面板永远显示
 *         「当前还没有压缩摘要」—— 与上游「还没触发压缩」时的长相一致。</li>
 *     <li>没有工具调用气泡（本移植版没有工具调用）。</li>
 *     <li>助手侧的消息要<b>先解 JSON 再显示</b>：历史里存的原始响应是
 *         {@code {"chat_text":"...","tts_text":"..."}}，直接画出来就是一串原始 JSON。
 *         上游 {@code transformMessage} 同样用 {@code new ResponseChat(text).getChatText()}。</li>
 * </ul>
 */
public class AIHistoryScreen extends Screen {
    private static final MutableComponent HISTORY_TITLE =
            new TranslatableComponent("gui.touhou_little_maid.button.maid_ai_chat_config.history_chat.title");
    private static final MutableComponent HISTORY_EMPTY =
            new TranslatableComponent("gui.touhou_little_maid.button.maid_ai_chat_config.history_chat_is_empty");
    private static final MutableComponent SUMMARY_TITLE =
            new TranslatableComponent("gui.touhou_little_maid.button.maid_ai_chat_config.history_chat.summary_title");
    private static final MutableComponent SUMMARY_EMPTY =
            new TranslatableComponent("gui.touhou_little_maid.button.maid_ai_chat_config.history_chat.summary_empty");
    /** 气泡最大文字宽度；气泡整体宽度 = min(文字宽, 它) + 10。 */
    private static final int CHAT_TEXT_WIDTH = 140;
    /** 右侧摘要面板宽度。 */
    private static final int SUMMARY_WIDTH = 120;
    private static final float SUMMARY_TEXT_SCALE = 0.5F;
    private static final int SUMMARY_TOP = 24;
    private static final int RIGHT_COLUMN_BOTTOM_MARGIN = 24;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;
    private static final int SUMMARY_BUTTON_GAP = 12;
    private static final int SUMMARY_MIN_HEIGHT = 48;
    private final EntityMaid maid;
    @Nullable
    private final Screen parent;
    private final ResourceLocation playerSkin;
    /** 排好序、已解析的展示用消息。 */
    private final List<SyncMaidAIHistoryPacket.Entry> history = Lists.newArrayList();
    private final List<HistoryChatWidget> historyWidgets = Lists.newArrayList();
    private double scroll = 0;
    private int maxHeight = 0;
    private int posX = 0;
    private int summaryTop = 0;
    private int summaryBottom = 0;
    private int historyTop = 0;
    private int historyBottom = 0;
    public AIHistoryScreen(EntityMaid maid) {
        this(null, maid);
    }
    public AIHistoryScreen(@Nullable Screen parent, EntityMaid maid) {
        super(new TextComponent("Maid History AI Chat Screen"));
        this.parent = parent;
        this.maid = maid;
        this.playerSkin = getPlayerSkin();
        this.transformMessage();
    }
    /** 把服务端发来的原始记录整理成「能直接画」的列表。 */
    private void transformMessage() {
        this.history.clear();
        for (SyncMaidAIHistoryPacket.Entry entry : SyncMaidAIHistoryPacket.CLIENT_HISTORY) {
            Role role = parseRole(entry.role());
            if (role == Role.USER) {
                this.history.add(entry);
                continue;
            }
            if (role != Role.ASSISTANT) {
                // SYSTEM / TOOL 不进历史界面（上游同样只显示 USER 与 ASSISTANT）
                continue;
            }
            // 历史里存的是模型的原始 JSON 响应，气泡里只该显示 chat_text
            String text = extractChatText(entry.message());
            if (StringUtils.isNotBlank(text)) {
                this.history.add(new SyncMaidAIHistoryPacket.Entry(Role.ASSISTANT.name(), text, entry.gameTime()));
            }
        }
    }
    private static Role parseRole(String name) {
        for (Role role : Role.values()) {
            if (role.name().equals(name)) {
                return role;
            }
        }
        return Role.SYSTEM;
    }
    /**
     * 从一条助手消息里取出要显示的文字。
     *
     * <p>解析失败（旧版本存的纯文本、或模型没按 JSON 输出）时原样显示 —— 宁可显示一段
     * 奇怪的文字，也不要让历史界面丢消息。</p>
     */
    private static String extractChatText(String raw) {
        if (StringUtils.isBlank(raw)) {
            return StringUtils.EMPTY;
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{")) {
            return raw;
        }
        try {
            ResponseChat responseChat = Service.GSON.fromJson(trimmed, ResponseChat.class);
            if (responseChat != null && StringUtils.isNotBlank(responseChat.getChatText())) {
                return responseChat.getChatText();
            }
        } catch (Exception ignored) {
            // 不是合法 JSON：原样显示
        }
        return raw;
    }
    @Override
    protected void init() {
        this.clearWidgets();
        this.historyWidgets.clear();
        this.transformMessage();
        this.posX = this.width / 2 - 75;
        this.summaryTop = SUMMARY_TOP;
        this.summaryBottom = this.summaryTop + this.getSummaryPanelHeight();
        this.historyTop = SUMMARY_TOP;
        this.historyBottom = this.height - 5;
        this.maxHeight = this.historyTop;
        for (SyncMaidAIHistoryPacket.Entry entry : this.history) {
            this.addHistoryWidget(entry);
        }
        this.addButtons();
        this.resetScroll();
    }
    /**
     * 滚动位置：内容比窗口矮时居中显示，否则贴住底部（最新的消息在下面）。
     * 与上游 {@code init} 里的那段算式一致。
     */
    private void resetScroll() {
        int visibleHeight = this.historyBottom - this.historyTop;
        int contentHeight = Math.max(0, this.maxHeight - this.historyTop);
        if (contentHeight < visibleHeight) {
            this.scroll = (visibleHeight - contentHeight) / 2D;
            return;
        }
        if (this.scroll > this.historyTop) {
            this.scroll = this.historyTop;
        }
        if (this.historyBottom > this.scroll + this.maxHeight) {
            this.scroll = this.historyBottom - this.maxHeight;
        }
    }
    private void addButtons() {
        MutableComponent clearName =
                new TranslatableComponent("gui.touhou_little_maid.button.maid_ai_chat_config.clear_history_chat");
        MutableComponent clearMsg =
                new TranslatableComponent("gui.touhou_little_maid.button.maid_ai_chat_config.clear_history_chat.confirm");
        this.addRenderableWidget(new FlatColorButton(this.getRightColumnLeft(), this.getClearButtonY(),
                SUMMARY_WIDTH, BUTTON_HEIGHT, clearName, b -> this.confirmClear(clearName, clearMsg)));
        this.addRenderableWidget(new FlatColorButton(this.getRightColumnLeft(), this.getBackButtonY(),
                SUMMARY_WIDTH, BUTTON_HEIGHT, CommonComponents.GUI_BACK, b -> this.onClose()));
    }
    /**
     * 清除历史前先确认 —— 这是不可撤销的服务端操作，与上游同样弹 {@link ConfirmScreen}。
     */
    private void confirmClear(MutableComponent title, MutableComponent message) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                ClearMaidAIChatHistoryPacket.send(this.maid.getId());
                SyncMaidAIHistoryPacket.CLIENT_HISTORY.clear();
                this.transformMessage();
                this.init();
            }
            minecraft.setScreen(this);
        }, title, message));
    }
    private void addHistoryWidget(SyncMaidAIHistoryPacket.Entry entry) {
        boolean isLeft = !Role.USER.name().equals(entry.role());
        Component message = new TextComponent(entry.message());
        int lineHeight = HistoryChatWidget.getLineHeight(this.font, message, CHAT_TEXT_WIDTH);
        int width = Math.min(this.font.width(message), CHAT_TEXT_WIDTH) + 10;
        if (isLeft) {
            this.historyWidgets.add(new HistoryChatWidget(this.posX - 100, this.maxHeight,
                    width, lineHeight, message, this.playerSkin, entry.gameTime(), true));
        } else {
            this.historyWidgets.add(new HistoryChatWidget(this.posX + 100 - width, this.maxHeight,
                    width, lineHeight, message, this.playerSkin, entry.gameTime(), false));
        }
        this.maxHeight += lineHeight + 5;
    }
    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTicks);
        drawCenteredString(poseStack, this.font, HISTORY_TITLE, this.posX + 210, 8, 0xFFFFFF);
        this.renderSummaryPanel(poseStack);
        if (this.historyWidgets.isEmpty()) {
            List<FormattedCharSequence> split = this.font.split(HISTORY_EMPTY, 150);
            for (int i = 0; i < split.size(); i++) {
                drawCenteredString(poseStack, this.font, split.get(i),
                        this.posX, this.historyTop + 15 + i * this.font.lineHeight, 0xFF5555);
            }
            return;
        }
        // 1.18.2 没有 GuiGraphics，裁剪只能用 RenderSystem.enableScissor，
        // 而且它吃「帧缓冲像素 + 左下角原点」：Y 要用屏幕高度减、宽高要乘 GUI 缩放。
        // 公式与 ClientMaidTooltip / AbstractMaidScreen 里那几处保持一致。
        Window window = Minecraft.getInstance().getWindow();
        double guiScale = window.getGuiScale();
        int scissorX = (int) ((this.posX - 128) * guiScale);
        int scissorY = (int) (window.getHeight() - this.historyBottom * guiScale);
        int scissorW = (int) (256 * guiScale);
        int scissorH = (int) ((this.historyBottom - this.historyTop) * guiScale);
        boolean scissor = scissorW > 0 && scissorH > 0;
        if (scissor) {
            RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);
        }
        poseStack.pushPose();
        poseStack.translate(0, this.scroll, 0);
        for (HistoryChatWidget widget : this.historyWidgets) {
            widget.render(poseStack, mouseX, mouseY, partialTicks);
        }
        poseStack.popPose();
        if (scissor) {
            RenderSystem.disableScissor();
        }
    }
    private void renderSummaryPanel(PoseStack poseStack) {
        int left = this.getRightColumnLeft();
        int right = left + SUMMARY_WIDTH;
        fill(poseStack, left, this.summaryTop, right, this.summaryBottom, 0xAA111111);
        fill(poseStack, left, this.summaryTop, right, this.summaryTop + 1, 0x66FFFFFF);
        fill(poseStack, left, this.summaryBottom - 1, right, this.summaryBottom, 0x66FFFFFF);
        drawCenteredString(poseStack, this.font, SUMMARY_TITLE,
                left + SUMMARY_WIDTH / 2, this.summaryTop + 6, 0xFFFFFF);
        // 摘要本体：本移植版还没有压缩摘要，固定显示占位文本（与上游「尚未压缩」时一致）
        poseStack.pushPose();
        poseStack.scale(SUMMARY_TEXT_SCALE, SUMMARY_TEXT_SCALE, 1.0F);
        int x = (int) ((left + 6) / SUMMARY_TEXT_SCALE);
        int y = (int) ((this.summaryTop + 22) / SUMMARY_TEXT_SCALE);
        int wrapWidth = Math.max(1, (int) ((SUMMARY_WIDTH - 12) / SUMMARY_TEXT_SCALE));
        for (FormattedCharSequence line : this.font.split(SUMMARY_EMPTY, wrapWidth)) {
            drawString(poseStack, this.font, line, x, y, 0x999999);
            y += this.font.lineHeight;
        }
        poseStack.popPose();
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0) {
            if (delta < 0 && this.historyBottom < this.scroll + this.maxHeight) {
                this.scroll = Math.min(this.scroll + delta * 15, this.historyBottom - this.maxHeight);
            }
            if (delta > 0 && this.scroll < this.historyTop) {
                this.scroll = Math.min(this.scroll + delta * 15, this.historyTop);
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
    /**
     * 收到新的历史快照：数据已经写进 {@link SyncMaidAIHistoryPacket#CLIENT_HISTORY}，
     * 这里按当前窗口尺寸重建一遍。
     */
    public void refresh() {
        this.init();
    }
    private int getSummaryPanelHeight() {
        return Math.max(SUMMARY_MIN_HEIGHT, this.getClearButtonY() - SUMMARY_BUTTON_GAP - this.summaryTop);
    }
    private int getRightColumnLeft() {
        return this.posX + 150;
    }
    private int getClearButtonY() {
        return this.height - RIGHT_COLUMN_BOTTOM_MARGIN - BUTTON_HEIGHT * 2 - BUTTON_GAP;
    }
    private int getBackButtonY() {
        return this.height - RIGHT_COLUMN_BOTTOM_MARGIN - BUTTON_HEIGHT;
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
    private static ResourceLocation getPlayerSkin() {
        Minecraft minecraft = Minecraft.getInstance();
        AbstractClientPlayer player = minecraft.player;
        if (player == null) {
            return DefaultPlayerSkin.getDefaultSkin(Util.NIL_UUID);
        }
        // 1.18.2 的 SkinManager 只有 getInsecureSkinInformation（返回 Map），
        // 拿现成的 ResourceLocation 得走 AbstractClientPlayer#getSkinTextureLocation
        return player.getSkinTextureLocation();
    }
}
