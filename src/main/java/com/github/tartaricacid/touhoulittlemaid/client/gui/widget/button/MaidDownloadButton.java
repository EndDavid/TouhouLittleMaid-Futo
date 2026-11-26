package com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button;

import com.github.tartaricacid.touhoulittlemaid.client.download.InfoGetManager;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.ModelDownloadGui;
import com.github.tartaricacid.touhoulittlemaid.util.TipsHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * 女仆界面左下角的「在线下载模型包」按钮，移植自上游
 * {@code client/gui/widget/button/MaidDownloadButton}。
 *
 * <p>位置与贴图逐像素照抄上游 1.18.2：{@code leftPos+20, topPos+230}，
 * 41×20，取 {@code maid_gui_button.png} 的 {@code (0,86)}，悬停时下移 20。</p>
 *
 * <p><b>角标提示为什么由界面来画：</b>1.18.2 的原版 {@code Button} 没有
 * 「额外文字」这个回调，所以上游把绘制拆成 {@code renderExtraTips} 交给界面调用。
 * 这里保持同样的分工 —— {@code AbstractMaidScreen#render} 在
 * {@code super.render} 之后调一次，位置固定在按钮正上方。</p>
 */
public class MaidDownloadButton extends ImageButton {
    public MaidDownloadButton(int x, int y, ResourceLocation texture, int entityId) {
        super(x, y, 41, 20, 0, 86, 20, texture, b -> {
            // 打开界面即视为「已看过提示」—— 与上游一致
            InfoGetManager.STATUE = InfoGetManager.Statue.NOT_UPDATE;
            Minecraft.getInstance().setScreen(new ModelDownloadGui(entityId));
        });
    }

    /**
     * 画「有新包 / 首次使用」的气泡提示。没有提示时什么都不画。
     *
     * <p>气泡由 {@link TipsHelper} 画：它从 {@code maid_gui_button.png} 取九宫格底图，
     * 并按文字行数选 1/2/3 行的高度。直接画一行文字在这个位置会同时压住
     * home / pick / ride 三个按钮，而且没有任何「这是按钮在说话」的视觉提示。</p>
     */
    public void renderExtraTips(PoseStack poseStack) {
        TipsHelper.renderTips(poseStack, this, this.getTipsText());
    }

    /**
     * 气泡文字。颜色交给 {@link TipsHelper}（统一 {@code 0xFFFF55}），这里不带样式。
     */
    private Component getTipsText() {
        if (InfoGetManager.STATUE == InfoGetManager.Statue.FIRST) {
            return new TranslatableComponent("gui.touhou_little_maid.button.model_download.statue.first");
        }
        if (InfoGetManager.STATUE == InfoGetManager.Statue.UPDATE) {
            return new TranslatableComponent("gui.touhou_little_maid.button.model_download.statue.update");
        }
        return null;
    }
}
