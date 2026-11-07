package com.github.tartaricacid.touhoulittlemaid.client.screen.widget;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.screen.MaidClientPacketSender;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * 女仆界面顶部的页签按钮，移植自上游 {@code client/gui/widget/button/MaidTabButton}。
 *
 * <p>24x26，贴图是 {@code maid_gui_side.png}：</p>
 * <ul>
 *     <li>{@code (uv, 21)} 24x26：只有「选中」时才画，是那张「已按下」的底图；</li>
 *     <li>{@code (uv, 47)} 16x16，偏移 (4, 6)：图标，永远画。</li>
 * </ul>
 *
 * <p>「选中」沿用上游的取巧写法：把 {@code active} 置 {@code false} ——
 * 于是既画上按下态底图，又天然不会响应点击、也不弹提示，不需要额外状态位。</p>
 */
public class MaidTabButton extends Button {
    private static final ResourceLocation SIDE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/maid_gui_side.png");

    private final int uvLeft;
    private final String labelKey;

    public MaidTabButton(int x, int y, int uvLeft, int tabIndex, int currentTab,
                         int entityId, String labelKey) {
        super(x, y, 24, 26, TextComponent.EMPTY,
                b -> MaidClientPacketSender.sendTab(tabIndex, entityId));
        this.uvLeft = uvLeft;
        this.labelKey = labelKey;
        // 当前页签不响应点击（上游同样用 active 表达「已选中」）
        this.active = tabIndex != currentTab;
    }

    /**
     * 提示文字用的语言键后缀（{@code main} / {@code task_config} / {@code maid_config}）。
     */
    public String getLabelKey() {
        return this.labelKey;
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, SIDE);
        RenderSystem.enableDepthTest();
        if (!this.active) {
            blit(poseStack, this.x, this.y, this.uvLeft, 21, this.width, this.height, 256, 256);
        }
        blit(poseStack, this.x + 4, this.y + 6, this.uvLeft, 47, 16, 16, 256, 256);
    }
}

