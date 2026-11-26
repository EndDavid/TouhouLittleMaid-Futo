package com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button;

import com.github.tartaricacid.touhoulittlemaid.client.download.pojo.DownloadInfo;
import com.github.tartaricacid.touhoulittlemaid.client.download.pojo.DownloadStatus;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TranslatableComponent;
import org.jetbrains.annotations.Nullable;

/**
 * 下载界面右侧那个「下载 / 已下载 / 需更新」按钮，移植自上游
 * {@code client/gui/widget/button/GuiDownloadButton}。
 *
 * <p>文案与可点状态都由 {@link DownloadInfo#getStatus()} 决定：只有
 * 「未下载」与「需更新」可点，其余置成 {@code active = false}（原版会把不可点的按钮
 * 画成灰色，正好就是上游想要的效果，所以不需要自己画）。</p>
 */
public class GuiDownloadButton extends Button {
    private final DownloadInfo info;

    public GuiDownloadButton(int x, int y, int width, int height, DownloadInfo info, OnPress onPress) {
        super(x, y, width, height, new TranslatableComponent("gui.touhou_little_maid.resources_download.not_download"),
                onPress);
        this.info = info;
        if (!DownloadStatus.canDownload(info.getStatus())) {
            this.active = false;
        }
    }

    /**
     * 每次绘制时按当前状态取文案。
     *
     * <p><b>为什么覆写 {@code getMessage} 而不是在构造时算一次：</b>下载是异步的，
     * 同一个按钮实例在「未下载 → 下载中 → 已下载」之间会变；构造时定死文案就永远停在
     * 「未下载」。上游也是覆写这个方法。</p>
     */
    @Override
    public Component getMessage() {
        if (this.info == null) {
            return super.getMessage();
        }
        MutableComponent text;
        switch (this.info.getStatus()) {
            case DOWNLOADED -> text = new TranslatableComponent("gui.touhou_little_maid.resources_download.downloaded");
            case DOWNLOADING -> text = new TranslatableComponent("gui.touhou_little_maid.resources_download.downloading");
            case NEED_UPDATE -> text = new TranslatableComponent("gui.touhou_little_maid.resources_download.need_update");
            default -> text = new TranslatableComponent("gui.touhou_little_maid.resources_download.not_download");
        }
        return text;
    }

    @Nullable
    public DownloadInfo getInfo() {
        return this.info;
    }
}

