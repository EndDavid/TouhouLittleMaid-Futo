package com.github.tartaricacid.touhoulittlemaid.client.screen.widget;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.ChatFormatting;

/**
 * 女仆配置界面里的一行，移植自上游 {@code client/gui/widget/button/MaidConfigButton}。
 *
 * <p>164x13，贴图 {@code maid_gui_button.png}：正常态 {@code (63,128)}、
 * 悬停态 {@code (63,141)}。左侧 5px 内缩写标题，右侧 142px 处居中写当前值。</p>
 *
 * <p><b>点击区域是分开的：</b>只有 {@code x+120 .. x+130}（左箭头）与
 * {@code x+154 .. x+164}（右箭头）这两小段会触发，中间一大片是死区 ——
 * 这样「点标题文字」不会意外改值。布尔项两个箭头绑同一个回调，
 * 数值 / 枚举项绑「减 / 加」两个回调。</p>
 */
public class MaidConfigButton extends Button {
    private static final ResourceLocation ICON =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/maid_gui_button.png");

    private final OnPress leftPress;
    private final OnPress rightPress;
    private boolean leftClicked = false;
    private Component value;

    public MaidConfigButton(int x, int y, Component title, Component value,
                            OnPress onLeftPress, OnPress onRightPress) {
        super(x, y, 164, 13, title, b -> {
        });
        this.leftPress = onLeftPress;
        this.rightPress = onRightPress;
        this.value = value;
    }

    public MaidConfigButton(int x, int y, Component title, Component value, OnPress onPress) {
        this(x, y, title, value, onPress, onPress);
    }

    /**
     * 换掉右侧显示的值（点击后由调用方更新，不读实体以保持与按键响应同步）。
     */
    public void setValue(Component value) {
        this.value = value;
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, ICON);
        RenderSystem.enableDepthTest();
        if (this.isHovered) {
            blit(poseStack, this.x, this.y, 63, 141, this.width, this.height, 256, 256);
        } else {
            blit(poseStack, this.x, this.y, 63, 128, this.width, this.height, 256, 256);
        }
        minecraft.font.draw(poseStack, this.getMessage(), this.x + 5, this.y + 3, 0x444444);
        this.drawCenteredStringWithoutShadow(poseStack, minecraft.font, this.value,
                this.x + 142, this.y + 3, ChatFormatting.GREEN.getColor());
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        if (!this.active || !this.visible) {
            return false;
        }
        boolean clickY = this.y <= mouseY && mouseY <= (this.y + this.getHeight());
        boolean leftZone = (this.x + 120) <= mouseX && mouseX <= (this.x + 130);
        boolean rightZone = (this.x + 154) <= mouseX && mouseX <= (this.x + 164);
        if (leftZone && clickY) {
            this.leftClicked = true;
            return true;
        }
        if (rightZone && clickY) {
            this.leftClicked = false;
            return true;
        }
        return false;
    }

    @Override
    public void onPress() {
        if (this.leftClicked) {
            this.leftPress.onPress(this);
        } else {
            this.rightPress.onPress(this);
        }
    }

    private void drawCenteredStringWithoutShadow(PoseStack poseStack, Font font, Component text,
                                                 int centerX, int y, int color) {
        FormattedCharSequence sequence = text.getVisualOrderText();
        drawString(poseStack, font, sequence, centerX - font.width(sequence) / 2, y, color);
    }

    /**
     * 点击回调（左 / 右箭头各一个）。
     */
    public interface OnPress {
        void onPress(MaidConfigButton button);
    }
}

