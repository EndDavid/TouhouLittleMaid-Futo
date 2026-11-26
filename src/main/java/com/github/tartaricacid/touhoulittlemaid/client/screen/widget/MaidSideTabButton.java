package com.github.tartaricacid.touhoulittlemaid.client.screen.widget;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 女仆界面<b>右侧</b>的侧边栏按钮（任务手册 / 全局配置两个槽位），移植自上游
 * {@code client/gui/widget/button/MaidSideTabButton}。
 *
 * <p><b>位置与贴图（{@code maid_gui_side.png}，256x256）：</b></p>
 * <pre>
 * 外框      (235,107) 21x50   两个 21x24 的圆角格，由界面统一画在 leftPos+256 / topPos+37
 * 按钮图标  (193,107+25*i+4) 16x16   偏移 (6, 4)，即 leftPos+257 / topPos+41+25*i
 * 选中底图  (209,107+25*i) 26x24     仅 active=false 时画（本移植版用不到，留作上游行为）
 * </pre>
 *
 * <p>1.18.2 的原版 {@link Button} 没有 tooltip 回调，所以提示行直接作为构造参数传进来，
 * 由 {@code AbstractMaidScreen} 在鼠标悬停时绘制 —— 与 {@link TaskButton} 的处理方式一致。</p>
 */
public class MaidSideTabButton extends Button {
    private static final ResourceLocation SIDE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/maid_gui_side.png");
    /**
     * 第一个槽位的贴图 V 坐标；每个槽位间隔 25 像素（与上游 {@code SPACING} 一致）。
     */
    private static final int V_OFFSET = 107;
    private static final int SPACING = 25;

    private final int top;
    private final List<Component> tooltip;

    /**
     * @param x       绝对坐标（界面里传 {@code leftPos + 251}）
     * @param y       绝对坐标（界面里传 {@code topPos + 37}）
     * @param index   第几个槽位，0 起
     * @param tooltip 悬停提示（两行：标题 + 说明）
     * @param onPress 点击回调
     */
    public MaidSideTabButton(int x, int y, int index, List<Component> tooltip, OnPress onPress) {
        super(x, y, 26, 24, TextComponent.EMPTY, onPress);
        this.top = V_OFFSET + index * SPACING;
        this.tooltip = tooltip;
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, SIDE);
        RenderSystem.enableDepthTest();
        if (!this.active) {
            // 上游语义：只有「不可点」时才画那张按下态底图
            blit(poseStack, this.x + 2, this.y, 209, this.top, this.width, this.height, 256, 256);
        }
        blit(poseStack, this.x + 6, this.y + 4, 193, this.top + 4, 16, 16, 256, 256);
    }

    @Nullable
    public List<Component> getTooltip() {
        return this.tooltip.isEmpty() ? null : this.tooltip;
    }
}
