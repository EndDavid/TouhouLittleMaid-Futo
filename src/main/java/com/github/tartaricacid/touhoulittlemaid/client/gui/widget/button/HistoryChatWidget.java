package com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * 历史对话里的一条消息气泡，移植自 1.20.1 的 {@code client/gui/widget/button/HistoryChatWidget}。
 *
 * <h2>贴图布局（{@code textures/gui/maid_history_chat.png}，256×256）</h2>
 * <ul>
 *     <li>{@code (0,0)} 起 100×16：玩家侧气泡（蓝底 + 黑边）；</li>
 *     <li>{@code (0,16)} 起 100×16：女仆侧气泡（灰底 + 黑边）；</li>
 *     <li>{@code (100,0)} / {@code (100,16)} 起 6×14：两个气泡的尾巴；</li>
 *     <li>{@code (0,32)} 起 32×32：女仆头像，压到 16×16 画在气泡左侧。</li>
 * </ul>
 * <p>贴图直接取自 TLMO 1.20.1 的 jar，未改一像素。</p>
 *
 * <h2>与 1.20.1 的差异</h2>
 * <ul>
 *     <li>1.18.2 没有 {@code GuiGraphics}，所以九宫格与贴图都手写
 *         （{@code blit} + {@code innerBlit}），并且必须先
 *         {@code RenderSystem.setShaderTexture} —— 1.18.2 的
 *         {@code GuiComponent#blit} 不会自己绑贴图。</li>
 *     <li>1.18.2 没有 {@code PlayerFaceRenderer}，玩家头像改为直接从皮肤贴图上
 *         取头部正面那 8×8 像素放大两倍。</li>
 *     <li>没有「工具调用」气泡：本移植版没有工具调用，{@code isTool} 分支省略。</li>
 * </ul>
 */
public class HistoryChatWidget extends AbstractWidget {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/maid_history_chat.png");
    private static final int TEXTURE_SIZE = 256;
    /** 气泡主体：贴图左上 100×16，两条（v = 0 玩家 / v = 16 女仆）。 */
    private static final int BODY_U = 0;
    private static final int BODY_WIDTH = 100;
    private static final int BODY_HEIGHT = 16;
    /** 九宫格边距：1 像素黑边决定不能切太小。 */
    private static final int SLICE_X = 8;
    private static final int SLICE_Y = 4;
    /** 尾巴：u = 100 起，6×14。 */
    private static final int TAIL_U = 100;
    private static final int TAIL_WIDTH = 6;
    private static final int TAIL_HEIGHT = 14;
    /** 女仆头像：贴图 (0,32) 起 32×32，画成 16×16。 */
    private static final int AVATAR_U = 0;
    private static final int AVATAR_V = 32;
    private static final int AVATAR_SOURCE = 32;
    private static final int AVATAR_SIZE = 16;
    private static final int AVATAR_OFFSET = 6;
    /** 玩家皮肤纹理头上那 8×8 的正面。 */
    private static final int SKIN_HEAD_U = 8;
    private static final int SKIN_HEAD_V = 8;
    private static final int SKIN_HEAD_SIZE = 8;
    private static final int SKIN_TEXTURE_SIZE = 64;
    /** 内边距：气泡里的文字离边框 5 像素（与上游一致）。 */
    private static final int PADDING = 5;
    /**
     * 时间换算：1 天 = 24000 刻，1 小时 = 1000 刻。
     *
     * <p>这两个常量是上游的定义 —— 注意「小时」并不是 24000 / 24 = 1000 的巧合，
     * 而是模型自己定的「1000 刻算一小时」，所以一天只有 24 小时的其中 24 段
     * （24000 / 1000 = 24），换算与游戏内时间一致。</p>
     */
    private static final long TICKS_PER_DAY = 24000;
    private static final long TICKS_PER_HOUR = 1000;

    /**
     * {@code true} = 女仆侧（气泡在左，灰色底 + 深色字）；{@code false} = 玩家侧。
     */
    private final boolean isLeft;
    private final ResourceLocation playerSkin;
    private final Component time;

    public HistoryChatWidget(int x, int y, int width, int height, Component message,
                             ResourceLocation playerSkin, long gameTime, boolean isLeft) {
        super(x, y, width, height, message);
        this.isLeft = isLeft;
        this.playerSkin = playerSkin;
        this.time = convertGameTime(gameTime);
    }

    /**
     * 把「游戏刻」换算成「几天前 / 几小时前 / 刚刚」。
     *
     * <p>与存档时间戳无关：历史记录只在内存里，跨存档没有意义，
     * 所以上游用的是世界刻而不是系统时间。</p>
     */
    private static Component convertGameTime(long inputGameTime) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return new TranslatableComponent("gui.touhou_little_maid.button.maid_ai_chat_config.history_chat.just_now");
        }
        long diff = level.getGameTime() - inputGameTime;
        if (diff < 0) {
            return new TranslatableComponent("gui.touhou_little_maid.button.maid_ai_chat_config.history_chat.just_now");
        }
        long days = diff / TICKS_PER_DAY;
        long hours = (diff % TICKS_PER_DAY) / TICKS_PER_HOUR;
        if (days > 0) {
            return new TranslatableComponent("gui.touhou_little_maid.button.maid_ai_chat_config.history_chat.days", days);
        }
        if (hours > 0) {
            return new TranslatableComponent("gui.touhou_little_maid.button.maid_ai_chat_config.history_chat.hours", hours);
        }
        return new TranslatableComponent("gui.touhou_little_maid.button.maid_ai_chat_config.history_chat.just_now");
    }

    /**
     * 一条消息占多高：{@code 10 + 行数 x 9}。
     *
     * <p>那 10 像素是「上方 5 + 下方 5」的内边距，与上游 {@code getHistoryLineHeight} 一致。</p>
     */
    public static int getLineHeight(Font font, Component message, int textWidth) {
        int lineCount = font.split(message, textWidth).size();
        return 10 + lineCount * font.lineHeight;
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        // 气泡贴图带 alpha，必须开混合；画完再恢复，免得影响后面的文字
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TEXTURE);
        this.drawBackground(poseStack);
        this.drawAvatar(poseStack);
        RenderSystem.disableBlend();
        this.drawMessage(poseStack, font);
        this.drawTime(poseStack, font);
    }

    /** 九宫格拉伸：四角原样、四边单向、中间双向（对应 1.20.1 的 {@code blitNineSliced}）。 */
    private void drawBackground(PoseStack poseStack) {
        int v = this.isLeft ? BODY_HEIGHT : 0;
        int left = Math.min(SLICE_X, this.width / 2);
        int right = Math.min(SLICE_X, this.width / 2);
        int top = Math.min(SLICE_Y, this.height / 2);
        int bottom = Math.min(SLICE_Y, this.height / 2);
        int srcMiddleWidth = BODY_WIDTH - left - right;
        int srcMiddleHeight = BODY_HEIGHT - top - bottom;
        int dstMiddleWidth = this.width - left - right;
        int dstMiddleHeight = this.height - top - bottom;
        int x = this.x;
        int y = this.y;
        // 四角
        blit(poseStack, x, y, left, top, BODY_U, v, left, top);
        blit(poseStack, x + this.width - right, y, right, top, BODY_U + BODY_WIDTH - right, v, right, top);
        blit(poseStack, x, y + this.height - bottom, left, bottom,
                BODY_U, v + BODY_HEIGHT - bottom, left, bottom);
        blit(poseStack, x + this.width - right, y + this.height - bottom, right, bottom,
                BODY_U + BODY_WIDTH - right, v + BODY_HEIGHT - bottom, right, bottom);
        // 上下边
        blit(poseStack, x + left, y, dstMiddleWidth, top,
                BODY_U + left, v, srcMiddleWidth, top);
        blit(poseStack, x + left, y + this.height - bottom, dstMiddleWidth, bottom,
                BODY_U + left, v + BODY_HEIGHT - bottom, srcMiddleWidth, bottom);
        // 左右边
        blit(poseStack, x, y + top, left, dstMiddleHeight,
                BODY_U, v + top, left, srcMiddleHeight);
        blit(poseStack, x + this.width - right, y + top, right, dstMiddleHeight,
                BODY_U + BODY_WIDTH - right, v + top, right, srcMiddleHeight);
        // 中央
        blit(poseStack, x + left, y + top, dstMiddleWidth, dstMiddleHeight,
                BODY_U + left, v + top, srcMiddleWidth, srcMiddleHeight);
        // 尾巴：女仆侧从左边伸出，玩家侧从右边伸出（与上游同一组坐标）
        int tailY = y + (this.height - TAIL_HEIGHT) / 2;
        if (this.isLeft) {
            blit(poseStack, x - 4, tailY, TAIL_WIDTH, TAIL_HEIGHT, TAIL_U, BODY_HEIGHT, TAIL_WIDTH, TAIL_HEIGHT);
        } else {
            blit(poseStack, x + this.width - 2, tailY, TAIL_WIDTH, TAIL_HEIGHT,
                    TAIL_U, 0, TAIL_WIDTH, TAIL_HEIGHT);
        }
    }

    private void drawAvatar(PoseStack poseStack) {
        int avatarY = this.y + (this.height - AVATAR_SIZE) / 2;
        if (this.isLeft) {
            // 女仆头像：32×32 源压成 16×16
            blit(poseStack, this.x - AVATAR_SIZE - AVATAR_OFFSET, avatarY,
                    AVATAR_SIZE, AVATAR_SIZE, AVATAR_U, AVATAR_V, AVATAR_SOURCE, AVATAR_SOURCE);
        } else if (this.playerSkin != null) {
            // 玩家头像：皮肤贴图里头上正面那 8×8，放大两倍
            RenderSystem.setShaderTexture(0, this.playerSkin);
            blit(poseStack, this.x + this.width + AVATAR_OFFSET, avatarY,
                    AVATAR_SIZE, AVATAR_SIZE, SKIN_HEAD_U, SKIN_HEAD_V,
                    SKIN_HEAD_SIZE, SKIN_HEAD_SIZE, SKIN_TEXTURE_SIZE, SKIN_TEXTURE_SIZE);
            // 后面的文字还要用气泡贴图，这里换回来
            RenderSystem.setShaderTexture(0, TEXTURE);
        }
    }

    private void drawMessage(PoseStack poseStack, Font font) {
        int color = this.isLeft ? 0x555555 : 0xFFFFFF;
        List<FormattedCharSequence> lines = font.split(this.getMessage(), this.width - PADDING * 2);
        for (int i = 0; i < lines.size(); i++) {
            GuiComponent.drawString(poseStack, font, lines.get(i),
                    this.x + PADDING, this.y + PADDING + i * font.lineHeight, color);
        }
    }

    /** 时间戳画在气泡上方，0.5 缩放（与上游 {@code renderString} 一致）。 */
    private void drawTime(PoseStack poseStack, Font font) {
        float scale = 0.5F;
        poseStack.pushPose();
        poseStack.scale(scale, scale, 1.0F);
        if (this.isLeft) {
            GuiComponent.drawString(poseStack, font, this.time.getVisualOrderText(),
                    (int) ((this.x + 2) / scale), (int) ((this.y - 5) / scale), 0x999999);
        } else {
            float width = font.width(this.time) * scale;
            GuiComponent.drawString(poseStack, font, this.time.getVisualOrderText(),
                    (int) ((this.x + this.width - width - 2) / scale),
                    (int) ((this.y - 5) / scale), 0x999999);
        }
        poseStack.popPose();
    }

    /**
     * 把贴图上 {@code srcWidth × srcHeight} 的区域拉伸画成 {@code width × height}。
     *
     * <p>1.18.2 的 {@code GuiComponent#blit} 不会自己绑贴图，调用前必须先
     * {@code RenderSystem.setShaderTexture}。</p>
     */
    private static void blit(PoseStack poseStack, int x, int y, int width, int height,
                             int u, int v, int srcWidth, int srcHeight) {
        blit(poseStack, x, y, width, height, u, v, srcWidth, srcHeight, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private static void blit(PoseStack poseStack, int x, int y, int width, int height,
                             int u, int v, int srcWidth, int srcHeight,
                             int textureWidth, int textureHeight) {
        GuiComponent.blit(poseStack, x, y, width, height, u, v, srcWidth, srcHeight,
                textureWidth, textureHeight);
    }

    @Override
    public void updateNarration(NarrationElementOutput output) {
        // 纯展示控件，不参与旁白
    }
}

