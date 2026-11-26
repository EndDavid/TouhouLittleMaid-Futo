package com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.download.pojo.DownloadInfo;
import com.github.tartaricacid.touhoulittlemaid.client.download.pojo.DownloadStatus;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 下载列表里的一行（268×46），移植自上游
 * {@code client/gui/widget/button/PackInfoButton}。
 *
 * <p>一行里画了五样东西：</p>
 * <ol>
 *     <li>底色 —— 选中是道奇蓝，未下载是深灰，已下载不画（保留背景），
 *         上层再叠一层半透明黑做「已安装」的压暗；</li>
 *     <li>下载进度条 —— 状态是 {@code DOWNLOADING} 且进度 ≥ 0 时，按百分比填一段橙色；</li>
 *     <li>「需更新」角标 —— {@code download_background.png} 的 {@code (48,16)}；</li>
 *     <li>类型图标 —— 一个包可能同时是女仆 / 坐垫 / 声音，按<b>有几种</b>选图标：
 *         3 种是一张合集图，2 种是两两组合的三张之一，1 种是单张。
 *         UV 表与上游逐像素一致；</li>
 *     <li>四行文字 —— 包名、版本（绿色）、大小（金色）、作者（青色）、上传时间（灰色）。</li>
 * </ol>
 *
 * <p><b>与上游的两处写法差异</b>（都是 1.18.2 的 API 限制，语义相同）：
 * ①上游覆写 {@code renderString(PoseStack, Font, int)}，而 1.18.2 的 {@code Button}
 * 没有这个方法，所以文字直接在 {@code renderButton} 里画；
 * ②上游用 {@code getFGColor()} 取文字色（1.19+），这里每行文字自带颜色。</p>
 */
public class PackInfoButton extends FlatColorButton {
    private static final ResourceLocation BG =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/download_background.png");

    /** 选中底色 / 未下载底色 / 进度条色 / 已下载压暗层。 */
    private static final int COLOR_SELECTED = 0xFF1E90FF;
    private static final int COLOR_NOT_DOWNLOADED = 0xFF434242;
    private static final int COLOR_PROGRESS = 0xFFFF5733;
    private static final int COLOR_DOWNLOADED_MASK = 0x7F232222;
    private static final int COLOR_HOVER_BORDER = 0xFFF3EFE0;

    private final DownloadInfo info;
    private boolean selected = false;

    public PackInfoButton(int x, int y, DownloadInfo info, OnPress onPress) {
        super(x, y, 268, 46, TextComponent.EMPTY, onPress);
        this.info = info;
        // 工具提示：描述 + 协议。上游用同款两行
        this.setTooltips(List.of(
                new TranslatableComponent(info.getDesc()).withStyle(ChatFormatting.GRAY),
                new TranslatableComponent("gui.touhou_little_maid.resources_download.license", info.getLicense())
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC)));
    }

    public void setSelect(boolean select) {
        this.selected = select;
    }

    public DownloadInfo getInfo() {
        return this.info;
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, BG);

        // 1. 底色
        if (this.selected) {
            fillGradient(poseStack, this.x, this.y, this.x + this.width, this.y + this.height,
                    COLOR_SELECTED, COLOR_SELECTED);
        } else if (this.info.getStatus() != DownloadStatus.DOWNLOADED) {
            fillGradient(poseStack, this.x, this.y, this.x + this.width, this.y + this.height,
                    COLOR_NOT_DOWNLOADED, COLOR_NOT_DOWNLOADED);
        }

        // 2. 下载进度条（宽度按百分比，右边缘跟着走）
        int progress = this.info.getDownloadProgress();
        if (this.info.getStatus() == DownloadStatus.DOWNLOADING && progress >= 0) {
            int widthProgress = this.width * Math.min(progress, 100) / 100;
            fillGradient(poseStack, this.x, this.y, this.x + widthProgress, this.y + this.height,
                    COLOR_PROGRESS, COLOR_PROGRESS);
        }

        // 3. 「需更新」角标
        if (this.info.getStatus() == DownloadStatus.NEED_UPDATE) {
            this.blit(poseStack, this.x + 240, this.y + 15, 48, 16, 16, 16);
        }

        // 4. 悬浮时描一圈白边（1 像素，压在文字下面不挡字）
        if (this.isHoveredOrFocused()) {
            fillGradient(poseStack, this.x, this.y + 1, this.x + 1, this.y + this.height - 1,
                    COLOR_HOVER_BORDER, COLOR_HOVER_BORDER);
            fillGradient(poseStack, this.x, this.y, this.x + this.width, this.y + 1,
                    COLOR_HOVER_BORDER, COLOR_HOVER_BORDER);
            fillGradient(poseStack, this.x + this.width - 1, this.y + 1, this.x + this.width,
                    this.y + this.height - 1, COLOR_HOVER_BORDER, COLOR_HOVER_BORDER);
            fillGradient(poseStack, this.x, this.y + this.height - 1, this.x + this.width,
                    this.y + this.height, COLOR_HOVER_BORDER, COLOR_HOVER_BORDER);
        }

        // 5. 类型图标：按「包里有几种类型」选一张 32×32 的图
        this.drawTypeIcon(poseStack);

        // 6. 四行文字
        this.drawText(poseStack, minecraft);

        // 7. 已下载：压暗一层，与未下载的深灰区分开
        if (this.info.getStatus() == DownloadStatus.DOWNLOADED) {
            fillGradient(poseStack, this.x, this.y, this.x + this.width, this.y + this.height,
                    COLOR_DOWNLOADED_MASK, COLOR_DOWNLOADED_MASK);
        }
    }

    /**
     * 类型图标。UV 表照抄上游：单类型在 {@code y=32}，两两组合在 {@code y=64}，
     * 三种合集在 {@code (0,96)}。
     */
    private void drawTypeIcon(PoseStack poseStack) {
        int count = this.info.getTypeCount();
        if (count >= 3) {
            this.blit(poseStack, this.x + 7, this.y + 7, 0, 96, 32, 32);
        } else if (count == 2) {
            if (!this.info.hasType(DownloadInfo.TypeEnum.MAID)) {
                // 坐垫 + 声音
                this.blit(poseStack, this.x + 7, this.y + 7, 64, 64, 32, 32);
            } else if (!this.info.hasType(DownloadInfo.TypeEnum.CHAIR)) {
                // 女仆 + 声音
                this.blit(poseStack, this.x + 7, this.y + 7, 32, 64, 32, 32);
            } else {
                // 女仆 + 坐垫
                this.blit(poseStack, this.x + 7, this.y + 7, 0, 64, 32, 32);
            }
        } else if (this.info.hasType(DownloadInfo.TypeEnum.MAID)) {
            this.blit(poseStack, this.x + 7, this.y + 7, 0, 32, 32, 32);
        } else if (this.info.hasType(DownloadInfo.TypeEnum.CHAIR)) {
            this.blit(poseStack, this.x + 7, this.y + 7, 32, 32, 32, 32);
        } else {
            this.blit(poseStack, this.x + 7, this.y + 7, 64, 32, 32, 32);
        }
    }

    private void drawText(PoseStack poseStack, Minecraft minecraft) {
        int startX = this.x + 50;
        int startY = this.y + 4;

        Component packName = new TranslatableComponent(this.info.getName());
        drawString(poseStack, minecraft.font, packName, startX, startY, ChatFormatting.WHITE.getColor());
        drawString(poseStack, minecraft.font, this.versionText(),
                startX + 5 + minecraft.font.width(packName), startY, ChatFormatting.GREEN.getColor());
        drawString(poseStack, minecraft.font, this.fileSizeText(), startX, startY + 10,
                ChatFormatting.GOLD.getColor());
        drawString(poseStack, minecraft.font, this.authorText(), startX, startY + 20,
                ChatFormatting.AQUA.getColor());
        drawString(poseStack, minecraft.font, this.uploadTimeText(), startX, startY + 30,
                ChatFormatting.GRAY.getColor());
    }

    /** {@code §n} 是「下划线」样式，上游同样写在字符串里。 */
    private String versionText() {
        return "\u00A7nv" + this.info.getVersion();
    }

    private MutableComponent authorText() {
        String delimiter = I18n.get("gui.touhou_little_maid.resources_download.author.delimiter");
        return new TranslatableComponent("gui.touhou_little_maid.resources_download.author",
                String.join(delimiter, this.info.getAuthor()));
    }

    private MutableComponent fileSizeText() {
        return new TranslatableComponent("gui.touhou_little_maid.resources_download.file_size",
                this.info.getFormatFileSize());
    }

    private MutableComponent uploadTimeText() {
        return new TranslatableComponent("gui.touhou_little_maid.resources_download.upload_time",
                this.info.getFormatData());
    }
}

