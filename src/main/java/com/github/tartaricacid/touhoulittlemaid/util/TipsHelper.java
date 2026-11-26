package com.github.tartaricacid.touhoulittlemaid.util;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * 悬在按钮上方的小气泡提示，移植自上游 {@code util/TipsHelper}。
 *
 * <h2>为什么要它而不是直接画一行字</h2>
 * <p>「点击此处下载更多模型包」这类提示有 20~40 个汉字，直接画会在按钮上糊成一片、
 * 还会盖住旁边的按钮。上游用 {@code maid_gui_button.png} 里三套九宫格
 * （1/2/3 行高）拼一个带尾巴的气泡，并把它的尾巴对准按钮右下角 ——
 * 视觉上明确是「这个按钮在说话」。</p>
 *
 * <h2>九宫格贴图（{@code maid_gui_button.png}）</h2>
 * <pre>
 * 1 行  v = 128..147   左边 10 宽 / 中间 20 宽可平铺 / 右边 10 宽
 * 2 行  v = 149..178
 * 3 行  v = 180..219
 * 尾巴  (0,42) 16x16   画在气泡右下角
 * </pre>
 * <p>逐像素核对过：这三块都是「左上角 1 像素圆角」的实心矩形，
 * 底部左下角有一个斜切三角作为气泡尾巴的根部。</p>
 *
 * <h2>与上游的两处差异</h2>
 * <ol>
 *     <li><b>修掉一个 {@code PoseStack} 泄漏。</b>上游在 {@code pushPose()} 与
 *         {@code translate} 之后才判断「文字是空的就 return」，那条路径没有
 *         {@code popPose()} —— 每次调用都会给全局矩阵栈留一层。这里把判断提到最前。</li>
 *     <li>参数类型用 {@link AbstractWidget} 而不是 {@code Button}：本移植版里
 *         {@code MaidDownloadButton} 继承 {@code ImageButton}，它也是 {@code AbstractWidget}；
 *         用父类型就不必为了这个方法把签名写死成 {@code Button}。</li>
 * </ol>
 */
public final class TipsHelper {
    private static final ResourceLocation BUTTON =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/maid_gui_button.png");

    /**
     * 气泡在 z 轴上的抬高量。要高于界面本体的所有元素，
     * 否则尾巴会被按钮底图盖掉一半。
     */
    private static final int BLIT_OFFSET = 450;
    /**
     * 气泡最宽多少像素后开始折行（上游的 124）。
     */
    private static final int MAX_TEXT_WIDTH = 124;
    /** 尾巴的贴图位置（{@code maid_gui_button.png} 的 (0,42)，16x16）。 */
    private static final int TAIL_U = 0;
    private static final int TAIL_V = 42;
    private static final int TAIL_SIZE = 16;

    private TipsHelper() {
    }

    /**
     * 在 {@code widget} 的右上方画一个带尾巴的气泡。
     *
     * @param text 为空（{@code TextComponent.EMPTY} 或 {@link Component#getString()} 为空白）时什么都不画
     */
    public static void renderTips(PoseStack poseStack, AbstractWidget widget, Component text) {
        // 先判空再 pushPose：上游在这里漏了 popPose，每帧泄漏一层矩阵
        if (text == null || text.getString().isBlank()) {
            return;
        }

        poseStack.pushPose();

        // 气泡尾巴尖对准按钮右下角偏里 8 像素
        int xOffset = widget.x + widget.getWidth() - 8;
        int yOffset = widget.y - 12;
        // 每 400 ms 上下跳 1 像素，提示「这里可以点」
        if ((System.currentTimeMillis() / 400) % 2 == 1) {
            yOffset += 1;
        }

        Font font = Minecraft.getInstance().font;
        List<FormattedCharSequence> split = font.split(text, MAX_TEXT_WIDTH);
        int lines = split.size();

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, BUTTON);
        // 关掉深度测试：气泡要压在按钮 / 槽位之上，而它们的 z 各不相同
        RenderSystem.disableDepthTest();

        if (lines == 1) {
            drawBubble(poseStack, xOffset, yOffset, font.width(text), 128, 20, 0);
            font.draw(poseStack, text, xOffset + 5, yOffset + 4, 0xFFFF55);
        } else if (lines == 2) {
            yOffset -= 10;
            drawBubble(poseStack, xOffset, yOffset, font.width(split.get(0)), 149, 30, 5);
            font.draw(poseStack, split.get(0), xOffset + 5, yOffset + 4, 0xFFFF55);
            font.draw(poseStack, split.get(1), xOffset + 5, yOffset + 14, 0xFFFF55);
        } else {
            // 超过 2 行一律按 3 行画，多出来的行不显示 —— 上游同样的取舍
            yOffset -= 20;
            drawBubble(poseStack, xOffset, yOffset, font.width(split.get(0)), 180, 40, 10);
            font.draw(poseStack, split.get(0), xOffset + 5, yOffset + 4, 0xFFFF55);
            font.draw(poseStack, split.get(1), xOffset + 5, yOffset + 14, 0xFFFF55);
            font.draw(poseStack, split.get(2), xOffset + 5, yOffset + 24, 0xFFFF55);
        }

        // 恢复深度测试：本方法不是渲染管线的最后一步（之后还有 tooltip 之类），
        // 关着不放会让后续绘制全部失去深度关系。上游这里没有恢复，是个疏漏。
        RenderSystem.enableDepthTest();
        poseStack.popPose();
    }

    /**
     * 画一个三段的九宫格气泡：左端 10 宽 → 中间按 20 宽平铺 → 右端 10 宽，最后补尾巴。
     *
     * @param textWidth      第一行文字的像素宽（决定气泡总宽）
     * @param v              三段贴图的起始 V（1 行 128 / 2 行 149 / 3 行 180）
     * @param height         气泡高度（20 / 30 / 40）
     * @param tailOffsetDown 尾巴相对气泡底部的下移量（1 行 0 / 2 行 5 / 3 行 10）
     */
    private static void drawBubble(PoseStack poseStack, int x, int y, int textWidth,
                                   int v, int height, int tailOffsetDown) {
        // 总宽 = 文字 + 左右各 10 的内边距 + 尾巴 16；夹在 [40, 150] 之间
        int bubbleWidth = Mth.clamp(textWidth + 26, 40, 150);

        // 左端 10 宽
        GuiComponent.blit(poseStack, x, y, BLIT_OFFSET, 0, v, 10, height, 256, 256);
        // 中间按 20 宽平铺
        int times = (bubbleWidth - 20) / 20;
        int startX = x + 10;
        for (int i = 0; i < times; i++) {
            GuiComponent.blit(poseStack, startX, y, BLIT_OFFSET, 10, v, 20, height, 256, 256);
            startX += 20;
        }
        // 剩下不足 20 的部分裁一段出来，保证右端严格对齐 bubbleWidth
        int last = bubbleWidth - times * 20 - 20;
        if (last > 0) {
            GuiComponent.blit(poseStack, startX, y, BLIT_OFFSET, 10, v, last, height, 256, 256);
        }
        // 右端 10 宽
        GuiComponent.blit(poseStack, x + bubbleWidth - 10, y, BLIT_OFFSET, 30, v, 10, height, 256, 256);
        // 尾巴：16x16，挂在右下角，向下错开 tailOffsetDown
        GuiComponent.blit(poseStack, x + bubbleWidth - 20, y + tailOffsetDown, BLIT_OFFSET,
                TAIL_U, TAIL_V, TAIL_SIZE, TAIL_SIZE, 256, 256);
    }
}

