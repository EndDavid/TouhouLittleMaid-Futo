package com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * 纯色扁平按钮，移植自上游 {@code client/gui/widget/button/FlatColorButton}。
 *
 * <p>上游的界面（狐狸卷轴、河童罗盘、日程检查等）都不画贴图，只用色块 + 文字，
 * 这样新界面不必先准备九宫格底图。选中态是蓝色，未选是深灰，悬停时描一圈白边。</p>
 *
 * <h2>与上游的差异</h2>
 * <ul>
 *     <li>1.18.2 的 {@code AbstractWidget} 没有 {@code getFGColor()}（那是 1.19+ 才有的），
 *         这里直接用白色；即便有，上游那个写法最后也只是白色。</li>
 *     <li>文字改用静态的 {@code drawCenteredString}：1.18.2 的 {@code Button}
 *         没有 {@code renderString} 这个可覆写方法。</li>
 *     <li>{@code setTooltips} 的触发方式不同：1.20.1 的 {@code GuiGraphics} 能在
 *         {@code renderWidget} 里直接画提示，1.18.2 的 {@code PoseStack} 不携带
 *         鼠标坐标，所以改由界面在 {@code render} 末尾统一调
 *         {@link #renderToolTip(PoseStack, Screen, int, int)} —— 这也是 1.18.2 里
 *         官方界面（如 {@code AbstractContainerScreen}）画提示的时机。</li>
 * </ul>
 */
public class FlatColorButton extends Button {
    /**
     * 选中态底色（道奇蓝）与未选中底色（深灰）。
     */
    private static final int COLOR_SELECTED = 0xFF1E90FF;
    private static final int COLOR_NORMAL = 0xFF434242;
    /**
     * 悬停描边色。
     */
    private static final int COLOR_HOVER_BORDER = 0xFFF3EFE0;
    /**
     * 文字色。上游从 {@code getFGColor()} 取，1.18.2 没有这个方法，直接给白色。
     */
    private static final int COLOR_TEXT = 0xFFF3EFE0;

    private boolean selected = false;
    /**
     * 悬停时显示的提示行；{@code null} = 不显示（与上游同名同义）。
     */
    @Nullable
    private List<Component> tooltips;

    public FlatColorButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress);
    }

    /**
     * 设置提示（翻译键），对应上游 {@code FlatColorButton#setTooltips(String)}。
     */
    public FlatColorButton setTooltips(String key) {
        this.tooltips = Collections.singletonList(new TranslatableComponent(key));
        return this;
    }

    public FlatColorButton setTooltips(List<Component> tooltips) {
        this.tooltips = tooltips;
        return this;
    }

    public void setSelect(boolean select) {
        this.selected = select;
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        fillGradient(poseStack, this.x, this.y, this.x + this.width, this.y + this.height,
                this.selected ? COLOR_SELECTED : COLOR_NORMAL,
                this.selected ? COLOR_SELECTED : COLOR_NORMAL);
        if (this.isHoveredOrFocused()) {
            // 四条边各画一像素：宽度比整块填充更细，压在文字下面不会挡住字
            fillGradient(poseStack, this.x, this.y + 1, this.x + 1, this.y + this.height - 1,
                    COLOR_HOVER_BORDER, COLOR_HOVER_BORDER);
            fillGradient(poseStack, this.x, this.y, this.x + this.width, this.y + 1,
                    COLOR_HOVER_BORDER, COLOR_HOVER_BORDER);
            fillGradient(poseStack, this.x + this.width - 1, this.y + 1,
                    this.x + this.width, this.y + this.height - 1,
                    COLOR_HOVER_BORDER, COLOR_HOVER_BORDER);
            fillGradient(poseStack, this.x, this.y + this.height - 1,
                    this.x + this.width, this.y + this.height,
                    COLOR_HOVER_BORDER, COLOR_HOVER_BORDER);
        }
        drawCenteredString(poseStack, Minecraft.getInstance().font, this.getMessage(),
                this.x + this.width / 2, this.y + (this.height - 8) / 2, COLOR_TEXT);
    }

    /**
     * 画悬停提示。必须在「本帧所有控件都渲染完之后」调用（见类的注释），
     * 否则提示框会被后画的控件盖住。
     */
    public void renderToolTip(PoseStack poseStack, Screen screen, int mouseX, int mouseY) {
        if (this.isHovered && tooltips != null) {
            screen.renderComponentTooltip(poseStack, tooltips, mouseX, mouseY);
        }
    }
}
