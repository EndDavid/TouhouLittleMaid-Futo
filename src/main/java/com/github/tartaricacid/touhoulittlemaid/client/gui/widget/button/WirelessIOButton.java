package com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.StateSwitchingButton;

import java.util.function.BiConsumer;

/**
 * 隙间界面上的两个开关按钮（I/O 方向、黑白名单），移植自上游
 * {@code client/gui/widget/button/WirelessIOButton}。
 *
 * <p>与 {@code BeaconEffectButton} 的关键差别：这里的「按下状态」不是<b>选择</b>
 * 而是<b>切换</b>，所以 {@link #onClick} 先把 {@code isStateTriggered} 取反，
 * 再把新的值交给回调 —— 回调负责发包。
 * （庭灯那边是「五个里选一个」，状态由按钮自己管理；这里是两个布尔开关。）</p>
 *
 * <p>提示框在 {@link #renderButton} 里直接画：{@code StateSwitchingButton}
 * 没有「悬浮提示」这条链路，上游也是这么处理的。</p>
 */
public class WirelessIOButton extends StateSwitchingButton {
    private final WirelessIOButton.ITooltip onTooltip;
    private final BiConsumer<Double, Double> onClick;

    public WirelessIOButton(int xIn, int yIn, int widthIn, int heightIn, boolean triggered,
                            BiConsumer<Double, Double> onClick, WirelessIOButton.ITooltip onTooltip) {
        super(xIn, yIn, widthIn, heightIn, triggered);
        this.onClick = onClick;
        this.onTooltip = onTooltip;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.isStateTriggered = !this.isStateTriggered;
        this.onClick.accept(mouseX, mouseY);
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        super.renderButton(poseStack, mouseX, mouseY, partialTicks);
        if (this.isHoveredOrFocused()) {
            this.onTooltip.onTooltip(poseStack, mouseX, mouseY);
        }
    }

    /**
     * 提示框回调。之所以不让按钮直接调 {@code Screen#renderTooltip}，
     * 是因为按钮拿不到所属 Screen；上游用这个内部接口把 Screen 传进来。
     */
    public interface ITooltip {
        void onTooltip(PoseStack poseStack, int x, int y);
    }
}

