package com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button;

import net.minecraft.client.gui.components.StateSwitchingButton;

/**
 * 槽位配置界面上的一个小方格，移植自上游
 * {@code client/gui/widget/button/WirelessIOSlotButton}。
 *
 * <p>它只做一件事：点一下把它负责的那一位取反。真正的提交在界面的
 * 「确认」按钮里（把整个 {@code boolean[]} 打包发出去），
 * 这样中途关掉界面就等于取消 —— 与上游一致。</p>
 */
public class WirelessIOSlotButton extends StateSwitchingButton {
    private final int index;
    private final boolean[] config;

    public WirelessIOSlotButton(int index, int xIn, int yIn, int widthIn, int heightIn, boolean[] config) {
        super(xIn, yIn, widthIn, heightIn, config[index]);
        this.index = index;
        this.config = config;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.isStateTriggered = !this.isStateTriggered;
        this.config[this.index] = this.isStateTriggered;
    }
}

