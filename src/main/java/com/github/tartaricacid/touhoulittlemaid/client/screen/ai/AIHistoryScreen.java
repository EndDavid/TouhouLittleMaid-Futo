package com.github.tartaricacid.touhoulittlemaid.client.screen.ai;

import com.github.tartaricacid.touhoulittlemaid.network.SyncMaidAIHistoryPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话历史界面，对应 1.20.1 的 HistoryAIChatScreen。
 *
 * <p>1.20.1 的历史界面带动画与自动滚动；本移植版只做「能看、能翻」的最小版本：
 * 每条消息按宽度折行，鼠标滚轮上下翻。数据来自服务端现发的一份快照
 * （{@code GetMaidAIHistoryPacket} → {@code SyncMaidAIHistoryPacket}）。</p>
 */
public class AIHistoryScreen extends Screen {
    private static final int LINE_HEIGHT = 10;
    private static final int PADDING = 24;

    private final List<String> lines = new ArrayList<>();
    private int scroll = 0;

    public AIHistoryScreen() {
        super(new TranslatableComponent("ai.touhou_little_maid.chat.button.history.tip"));
    }

    @Override
    protected void init() {
        this.rebuild();
        this.addRenderableWidget(new Button(this.width / 2 - 60, this.height - 30, 120, 20,
                new TranslatableComponent("gui.done"), b -> this.onClose()));
    }

    /** 收到新快照时重排一次。 */
    public void refresh() {
        this.rebuild();
    }

    private void rebuild() {
        this.lines.clear();
        for (String[] entry : SyncMaidAIHistoryPacket.CLIENT_HISTORY) {
            boolean user = entry.length > 0 && "USER".equals(entry[0]);
            String text = entry.length > 1 ? entry[1] : "";
            String prefix = user ? "> " : "  ";
            // 1.18.2 的 Font 没有 getSplitter()/splitLines，用 List<FormattedText> 版本
            for (net.minecraft.network.chat.FormattedText part :
                    this.font.getSplitter().splitLines(
                            new net.minecraft.network.chat.TextComponent(text),
                            this.width - PADDING * 2 - 12,
                            net.minecraft.network.chat.Style.EMPTY)) {
                this.lines.add(prefix + part.getString());
            }
        }
    }

    private int maxScroll() {
        int visible = (this.height - PADDING * 2 - 40) / LINE_HEIGHT;
        return Math.max(0, this.lines.size() - visible);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        fill(poseStack, 0, 0, this.width, this.height, 0xC0_000000);
        drawCenteredString(poseStack, this.font, this.title, this.width / 2, PADDING - 12, 0xFFFFFF);
        int y = PADDING;
        for (int i = this.scroll; i < this.lines.size(); i++) {
            String line = this.lines.get(i);
            int color = line.startsWith("> ") ? 0x7FE0FF : 0xFFFFFF;
            drawString(poseStack, this.font, line, PADDING, y, color);
            y += LINE_HEIGHT;
            if (y > this.height - 34) {
                break;
            }
        }
        if (this.lines.isEmpty()) {
            drawCenteredString(poseStack, this.font, new TextComponent("-"),
                    this.width / 2, this.height / 2, 0x888888);
        }
        super.render(poseStack, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        this.scroll = (int) Math.max(0, Math.min(this.maxScroll(), this.scroll - delta));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}