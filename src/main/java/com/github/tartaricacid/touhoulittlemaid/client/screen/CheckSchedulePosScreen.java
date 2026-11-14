package com.github.tartaricacid.touhoulittlemaid.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * 「作息坐标检查未通过」的提示界面，移植自上游
 * {@code client/gui/entity/maid/other/CheckSchedulePosGui}。
 *
 * <p>玩家在女仆界面上点 Home 开关时，服务端可能拒绝（跨维度 / 距离 32 格以上）。
 * 拒绝是无声的 —— 开关会弹回原状 —— 所以服务端补发一条
 * {@code CheckSchedulePosPacket}，客户端盖这层界面把原因说清楚。</p>
 *
 * <h2>为什么叠在女仆界面上而不是替换它</h2>
 * <p>「返回」按钮要回到原来那个女仆界面，所以必须持有它的引用。
 * 直接 {@code setScreen(new ...)} 替换掉的话，玩家就得重新右击女仆、重新找标签页，
 * 而这条提示本来就只是「一句话」。上游同样保留父界面引用。</p>
 *
 * <p>注意父界面被保留期间不会 {@code init()} 重新执行，但 vanilla 的
 * {@code Screen#resize} 会在窗口大小变化时补一次，所以放大窗口后返回也不会错位。</p>
 */
@Environment(EnvType.CLIENT)
public class CheckSchedulePosScreen extends Screen {
    /**
     * 提示文字的最大宽度。上游是 300：超过就自动换行，
     * 因为 lang 里这句提示本身带着 {@code \n\n} 和两个分段。
     */
    private static final int TIPS_WIDTH = 300;
    private static final int LINE_SPACING = 3;
    private static final int BUTTON_WIDTH = 200;

    private final Screen parent;
    private final Component tips;
    private int middleX;
    private int middleY;

    public CheckSchedulePosScreen(Screen parent, Component tips) {
        super(new TextComponent("Check Schedule Pos Screen"));
        this.parent = parent;
        this.tips = tips;
    }

    @Override
    protected void init() {
        this.middleX = this.width / 2;
        this.middleY = this.height / 2;
        this.addRenderableWidget(new Button(this.middleX - BUTTON_WIDTH / 2, this.middleY + 10,
                BUTTON_WIDTH, 20, new TranslatableComponent("button.touhou_little_maid.maid.return"),
                b -> Minecraft.getInstance().setScreen(this.parent)));
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(poseStack);
        List<FormattedCharSequence> split = this.font.split(this.tips, TIPS_WIDTH);
        // 首行往上抬，让整段文字的中心落在屏幕中心偏上一点：
        // 下面还要留出「返回」按钮的位置，否则长提示会压到按钮上
        int startY = this.middleY - 10 - split.size() * (this.font.lineHeight + LINE_SPACING);
        for (FormattedCharSequence text : split) {
            drawCenteredString(poseStack, this.font, text, this.middleX, startY, 0xFFFFFF);
            startY += this.font.lineHeight + LINE_SPACING;
        }
        // super.render 最后调：它负责画那个返回按钮，压在文字之上
        super.render(poseStack, mouseX, mouseY, partialTicks);
    }

    /**
     * 与上游一致：这不是一个「暂停」界面，单人游戏里女仆应继续行动。
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

