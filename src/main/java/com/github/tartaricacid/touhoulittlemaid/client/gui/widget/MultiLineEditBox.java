package com.github.tartaricacid.touhoulittlemaid.client.gui.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 多行文本框，替代 1.20.1 的 {@code MultiLineEditBox}（1.18.2 没有那个类）。
 *
 * <p>上游的角色设定界面用它装 {@code 自定义设定}（上限 4096 字、自动折行、可以换行），
 * 而 1.18.2 只有单行的 {@code EditBox} —— 单行框装不下一段人设，所以这里手写一个最小可用版：</p>
 * <ul>
 *     <li>按宽度自动折行（尽量在空格处断，中文按字断），保留原有的换行符；</li>
 *     <li>光标、Home / End、上下左右、Backspace / Delete、Ctrl+A / C / V / X；</li>
 *     <li>内容超出高度时按行滚动，滚动位置跟随光标；</li>
 *     <li>空内容且未激活时显示占位提示。</li>
 * </ul>
 *
 * <h2>为什么不接原版焦点系统</h2>
 * <p>界面里同时有一个 {@code EditBox}（主人称呼）和一个本控件。1.18.2 的
 * {@code Screen} 只有一个 {@code focused} 子控件，两个都要抢焦点反而容易出错；
 * 因此由界面显式把「当前活动输入框」转给本控件（{@link #setActive(boolean)}），
 * 输入事件由界面先转给本控件、返回 {@code false} 才交给 {@code super}。</p>
 */
public class MultiLineEditBox extends AbstractWidget {
    /** 文本区左右各留 4 像素，光标不会贴边框。 */
    private static final int PADDING = 4;
    private static final int SCROLLBAR_WIDTH = 3;
    /** 光标闪烁周期（毫秒的一半，亮/灭各占一半）。 */
    private static final long BLINK_MILLIS = 300L;

    private final Font font;
    private final Component placeholder;
    /** 每一折行在 {@link #value} 里的区间：{@code [start, end)}。 */
    private final List<int[]> lines = new ArrayList<>();

    private String value = "";
    private int cursor = 0;
    private int scroll = 0;
    private int charLimit = 4096;
    private boolean active = false;
    /** 值或宽度变了就要重新折行。 */
    private boolean dirty = true;
    private Consumer<String> valueListener;

    public MultiLineEditBox(Font font, int x, int y, int width, int height,
                            Component placeholder, Component narration) {
        super(x, y, width, height, narration);
        this.font = font;
        this.placeholder = placeholder;
    }

    // ---------------------------------------------------------------- 值

    public String getValue() {
        return this.value;
    }

    public void setValue(String value) {
        this.value = value == null ? "" : value;
        if (this.value.length() > this.charLimit) {
            this.value = this.value.substring(0, this.charLimit);
        }
        this.cursor = Math.min(this.cursor, this.value.length());
        this.dirty = true;
    }

    public void setCharacterLimit(int charLimit) {
        this.charLimit = Math.max(1, charLimit);
    }

    public void setValueListener(Consumer<String> valueListener) {
        this.valueListener = valueListener;
    }

    private void onValueChanged() {
        this.dirty = true;
        if (this.valueListener != null) {
            this.valueListener.accept(this.value);
        }
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
        if (active) {
            this.scrollCursorIntoView();
        }
    }

    // ---------------------------------------------------------------- 折行

    /**
     * 重新计算折行。
     *
     * <p>先按 {@code '\n'} 切段，再逐段用 {@code plainSubstrByWidth} 取「这一行最多能放几个字」。
     * 折在这里而不是折在空格处只有一个原因：中文没有空格，而玩家写的设定几乎全是中文或
     * 「英文 + 中文」混排，按字断更可控；英文里若断点落在单词中间，会回退到最近的空格。</p>
     */
    private void rewrap() {
        this.lines.clear();
        int innerWidth = Math.max(1, this.width - PADDING * 2 - SCROLLBAR_WIDTH);
        int paragraphStart = 0;
        while (true) {
            int paragraphEnd = this.value.indexOf('\n', paragraphStart);
            boolean last = paragraphEnd < 0;
            if (last) {
                paragraphEnd = this.value.length();
            }
            this.wrapParagraph(paragraphStart, paragraphEnd, innerWidth);
            if (last) {
                break;
            }
            paragraphStart = paragraphEnd + 1;
        }
        if (this.lines.isEmpty()) {
            this.lines.add(new int[]{0, 0});
        }
        this.dirty = false;
    }

    private void wrapParagraph(int start, int end, int innerWidth) {
        if (start == end) {
            this.lines.add(new int[]{start, start});
            return;
        }
        int p = start;
        while (p < end) {
            String remaining = this.value.substring(p, end);
            String fit = this.font.plainSubstrByWidth(remaining, innerWidth);
            int cut = fit.length();
            if (cut == 0) {
                // 连一个字都放不下（窗口极窄）：至少吃一个字，避免死循环
                cut = 1;
            } else if (cut < remaining.length()) {
                int space = fit.lastIndexOf(' ');
                if (space > 0) {
                    cut = space + 1;
                }
            }
            this.lines.add(new int[]{p, p + cut});
            p += cut;
        }
    }

    /** 光标在第几行（0 基）。 */
    private int cursorLine() {
        for (int i = 0; i < this.lines.size(); i++) {
            int[] line = this.lines.get(i);
            if (this.cursor <= line[1]) {
                return i;
            }
        }
        return Math.max(0, this.lines.size() - 1);
    }

    private int visibleLineCount() {
        return Math.max(1, (this.height - PADDING * 2) / this.font.lineHeight);
    }

    private void scrollCursorIntoView() {
        if (this.dirty) {
            return;
        }
        int line = this.cursorLine();
        int visible = this.visibleLineCount();
        if (line < this.scroll) {
            this.scroll = line;
        } else if (line >= this.scroll + visible) {
            this.scroll = line - visible + 1;
        }
        int maxScroll = Math.max(0, this.lines.size() - visible);
        this.scroll = Math.max(0, Math.min(maxScroll, this.scroll));
    }

    // ---------------------------------------------------------------- 编辑

    private void insert(String text) {
        if (this.value.length() + text.length() > this.charLimit) {
            return;
        }
        this.value = this.value.substring(0, this.cursor) + text + this.value.substring(this.cursor);
        this.cursor += text.length();
        this.onValueChanged();
    }

    private void deleteRange(int from, int to) {
        if (from >= to) {
            return;
        }
        this.value = this.value.substring(0, from) + this.value.substring(to);
        this.cursor = from;
        this.onValueChanged();
    }

    public boolean charTyped(char codePoint) {
        if (!this.active) {
            return false;
        }
        // 换行由 keyPressed 的 Enter 负责；其余控制字符一律忽略
        if (codePoint < 32 || codePoint == 127) {
            return false;
        }
        this.insert(String.valueOf(codePoint));
        this.scrollCursorIntoView();
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.active) {
            return false;
        }
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (this.cursor > 0) {
                    this.deleteRange(this.cursor - 1, this.cursor);
                }
            }
            case GLFW.GLFW_KEY_DELETE -> this.deleteRange(this.cursor, Math.min(this.cursor + 1, this.value.length()));
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> this.insert("\n");
            case GLFW.GLFW_KEY_LEFT -> this.cursor = Math.max(0, this.cursor - 1);
            case GLFW.GLFW_KEY_RIGHT -> this.cursor = Math.min(this.value.length(), this.cursor + 1);
            case GLFW.GLFW_KEY_UP -> this.moveVertical(-1);
            case GLFW.GLFW_KEY_DOWN -> this.moveVertical(1);
            case GLFW.GLFW_KEY_HOME -> this.cursor = this.lines.get(this.cursorLine())[0];
            case GLFW.GLFW_KEY_END -> this.cursor = this.lines.get(this.cursorLine())[1];
            case GLFW.GLFW_KEY_A -> {
                if (!ctrl) {
                    return false;
                }
                this.cursor = this.value.length();
            }
            case GLFW.GLFW_KEY_C -> {
                if (!ctrl) {
                    return false;
                }
                Minecraft.getInstance().keyboardHandler.setClipboard(this.value);
            }
            case GLFW.GLFW_KEY_X -> {
                if (!ctrl) {
                    return false;
                }
                Minecraft.getInstance().keyboardHandler.setClipboard(this.value);
                this.deleteRange(0, this.value.length());
            }
            case GLFW.GLFW_KEY_V -> {
                if (!ctrl) {
                    return false;
                }
                this.insert(Minecraft.getInstance().keyboardHandler.getClipboard());
            }
            default -> {
                return false;
            }
        }
        this.scrollCursorIntoView();
        return true;
    }

    /** 上下移动：记住当前列（以像素算），落到目标行对应的字符位置。 */
    private void moveVertical(int delta) {
        int line = this.cursorLine();
        int target = line + delta;
        if (target < 0 || target >= this.lines.size()) {
            return;
        }
        int[] currentLine = this.lines.get(line);
        String prefix = this.value.substring(currentLine[0], this.cursor);
        int column = this.font.width(prefix);
        int[] targetLine = this.lines.get(target);
        String targetText = this.value.substring(targetLine[0], targetLine[1]);
        this.cursor = targetLine[0] + this.font.plainSubstrByWidth(targetText, column).length();
    }

    /** 点击：把光标放到点到的位置（行按 y、列按 x 估算，够用即可）。 */
    public boolean mouseClickedInBox(double mouseX, double mouseY) {
        if (!this.visible || !this.active) {
            return false;
        }
        if (mouseX < this.x || mouseX > this.x + this.width || mouseY < this.y || mouseY > this.y + this.height) {
            return false;
        }
        if (this.dirty) {
            this.rewrap();
        }
        int line = this.scroll + (int) ((mouseY - this.y - PADDING) / this.font.lineHeight);
        line = Math.max(0, Math.min(this.lines.size() - 1, line));
        int[] range = this.lines.get(line);
        String text = this.value.substring(range[0], range[1]);
        int column = (int) (mouseX - this.x - PADDING);
        this.cursor = range[0] + this.font.plainSubstrByWidth(text, Math.max(0, column)).length();
        this.scrollCursorIntoView();
        return true;
    }

    // ---------------------------------------------------------------- 绘制

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        if (this.dirty) {
            this.rewrap();
            this.scrollCursorIntoView();
        }
        // 底与边框：1.18.2 的 EditBox 是纯黑底 + 灰边，这里保持一致
        fill(poseStack, this.x, this.y, this.x + this.width, this.y + this.height, 0xFF000000);
        int border = this.active ? 0xFFFFFFFF : 0xFFA0A0A0;
        fill(poseStack, this.x, this.y, this.x + this.width, this.y + 1, border);
        fill(poseStack, this.x, this.y + this.height - 1, this.x + this.width, this.y + this.height, border);
        fill(poseStack, this.x, this.y, this.x + 1, this.y + this.height, border);
        fill(poseStack, this.x + this.width - 1, this.y, this.x + this.width, this.y + this.height, border);

        if (this.value.isEmpty()) {
            // 与 1.20.1 一致：只要内容为空就显示占位提示（激活时也显示），
            // 玩家不会把「空框」误认为控件没加载出来
            GuiComponent.drawString(poseStack, this.font, this.placeholder,
                    this.x + PADDING, this.y + PADDING, active ? 0xFFA0A0A0 : 0xFF808080);
            this.renderCounter(poseStack);
            return;
        }

        int visible = this.visibleLineCount();
        for (int i = 0; i < visible; i++) {
            int index = this.scroll + i;
            if (index >= this.lines.size()) {
                break;
            }
            int[] range = this.lines.get(index);
            String text = this.value.substring(range[0], range[1]);
            GuiComponent.drawString(poseStack, this.font, text,
                    this.x + PADDING, this.y + PADDING + i * this.font.lineHeight, 0xFFE0E0E0);
        }

        // 滚动条：只在内容超出时画，位置按「已滚动行数 / 总行数」
        if (this.lines.size() > visible) {
            int barX = this.x + this.width - SCROLLBAR_WIDTH - 1;
            int trackTop = this.y + 2;
            int trackHeight = this.height - 4;
            int thumbHeight = Math.max(8, trackHeight * visible / this.lines.size());
            int maxScroll = this.lines.size() - visible;
            int offset = maxScroll <= 0 ? 0 : (trackHeight - thumbHeight) * this.scroll / maxScroll;
            fill(poseStack, barX, trackTop, barX + SCROLLBAR_WIDTH, trackTop + trackHeight, 0x40FFFFFF);
            fill(poseStack, barX, trackTop + offset, barX + SCROLLBAR_WIDTH,
                    trackTop + offset + thumbHeight, 0xC0FFFFFF);
        }

        this.renderCounter(poseStack);
        this.renderCursor(poseStack);
    }
    /**
     * 右下角的字数统计（{@code 397/4096}），对应 1.20.1 {@code MultiLineEditBox} 里那个
     * 由 {@code setCharacterLimit} 驱动的计数器。
     */
    private void renderCounter(PoseStack poseStack) {
        String counter = this.value.length() + "/" + this.charLimit;
        int color = this.value.length() >= this.charLimit ? 0xFFFF5555 : 0xFF808080;
        int counterX = this.x + this.width - SCROLLBAR_WIDTH - 2 - this.font.width(counter);
        int counterY = this.y + this.height - this.font.lineHeight - 1;
        GuiComponent.drawString(poseStack, this.font, counter, counterX, counterY, color);
    }

    private void renderCursor(PoseStack poseStack) {
        if (!this.active) {
            return;
        }
        // 闪烁：3 秒周期里亮一半
        if ((System.currentTimeMillis() / BLINK_MILLIS) % 2 != 0) {
            return;
        }
        int line = this.cursorLine();
        int visibleLine = line - this.scroll;
        if (visibleLine < 0 || visibleLine >= this.visibleLineCount()) {
            return;
        }
        int[] range = this.lines.get(line);
        String prefix = this.value.substring(range[0], Math.min(this.cursor, range[1]));
        int cursorX = this.x + PADDING + this.font.width(prefix);
        int cursorY = this.y + PADDING + visibleLine * this.font.lineHeight;
        fill(poseStack, cursorX, cursorY, cursorX + 1, cursorY + this.font.lineHeight, 0xFFFFFFFF);
    }

    @Override
    public void updateNarration(NarrationElementOutput output) {
        // 输入控件由界面自行播报，这里留空
    }
}

