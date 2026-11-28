package com.github.tartaricacid.touhoulittlemaid.client.gui.entity;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.download.InfoGetManager;
import com.github.tartaricacid.touhoulittlemaid.client.download.pojo.DownloadInfo;
import com.github.tartaricacid.touhoulittlemaid.client.download.pojo.DownloadStatus;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.FlatColorButton;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.GuiDownloadButton;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.PackInfoButton;
import com.github.tartaricacid.touhoulittlemaid.client.resource.CustomPackLoader;
import com.github.tartaricacid.touhoulittlemaid.client.screen.MaidClientPacketSender;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.TabIndex;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 在线模型包下载界面，移植自上游 {@code client/gui/entity/ModelDownloadGui}
 * （Forge 1.18.2 版）。
 */
public class ModelDownloadGui extends Screen {
    private static final ResourceLocation BG =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/download_background.png");
    private static final String PACK_FILE_SUFFIX = ".zip";
    /** 每页 4 行（上游写死 4，不随窗口高度变化）。 */
    private static final int PACK_PER_PAGE = 4;

    /** CRC32 → 文件名，用于「已下载」判定。 */
    private final java.util.Map<Long, String> crc32Infos = new java.util.HashMap<>();
    private final List<DownloadInfo> showInfos = new ArrayList<>();

    /**
     * 女仆实体 id。关闭界面时用它请求服务端重新打开女仆界面；
     * 为负值（例如没有从女仆界面进来）时关闭就回到世界。
     */
    private final int entityId;

    private Condition condition = Condition.ALL;
    @Nullable
    private EditBox textField;
    private boolean needReload = false;
    private int selectIndex = -1;
    private int currentPage;
    private int x;
    private int y;

    /** 页签按钮与它们对应的提示键（下标一一对应）。 */
    private final List<Button> conditionButtons = new ArrayList<>();
    private final List<String> conditionTipKeys = new ArrayList<>();
    /**
     * 需要画悬停提示的扁平按钮。
     *
     * <p>1.18.2 的 {@code Screen#renderables} 是 <b>private</b>（1.20.1 那边要写 Mixin 才拿得到），
     * 所以这里自己记一份 —— 比自己反射 / 开 Mixin 干净得多。</p>
     */
    private final List<FlatColorButton> tooltipButtons = new ArrayList<>();

    public ModelDownloadGui() {
        this(-1);
    }

    public ModelDownloadGui(int entityId) {
        super(new TextComponent("Model Pack Download"));
        this.entityId = entityId;
        this.getCrc32Infos();
        this.checkDownloadInfo();
    }

    // ------------------------------------------------------------------ 布局

    @Override
    protected void init() {
        // 注意：不调 super.init()。这里所有控件都由下面几次 add* 自行创建，
        // 与上游一致；调 super 只会把默认的空白布局也加一遍。
        this.clearWidgets();
        this.conditionButtons.clear();
        this.conditionTipKeys.clear();
        this.tooltipButtons.clear();
        this.x = (this.width - 420) / 2;
        this.y = (this.height - 240) / 2;
        this.initShowInfos();
        this.addBaseButtons();
        this.addPackButtons();
        this.addPageButtons();
        this.addPackHandleButtons();
        this.addSearchBox();
    }

    private void addSearchBox() {
        String textCache = this.textField == null ? "" : this.textField.getValue();
        boolean focus = this.textField != null && this.textField.isFocused();
        this.textField = new EditBox(this.font, this.x + 273, this.y + 78, 144, 16, TextComponent.EMPTY);
        this.textField.setTextColor(0xF3EFE0);
        this.textField.setMaxLength(64);
        this.textField.setFocus(focus);
        this.textField.setValue(textCache);
        this.textField.moveCursorToEnd();
        // addWidget 只登记事件路由，不参与渲染；渲染在 renderSearchBox 里手动做
        this.addWidget(this.textField);
    }

    private void addPackHandleButtons() {
        if (this.selectIndex < 0 || this.selectIndex >= this.showInfos.size()) {
            return;
        }
        DownloadInfo info = this.showInfos.get(this.selectIndex);
        this.addTooltipWidget(new FlatColorButton(this.x + 272, this.y + 50, 20, 20, TextComponent.EMPTY,
                b -> this.openPackWebsite(info)).setTooltips("gui.touhou_little_maid.resources_download.open_link"));
        this.addRenderableWidget(new GuiDownloadButton(this.x + 294, this.y + 50, 102, 20, info, b -> {
            if (info.getStatus() == DownloadStatus.NOT_DOWNLOAD) {
                info.setStatus(DownloadStatus.DOWNLOADING);
                InfoGetManager.downloadPack(info);
                this.init();
            } else if (info.getStatus() == DownloadStatus.NEED_UPDATE) {
                this.updatePack(info);
            }
        }));
        this.addTooltipWidget(new FlatColorButton(this.x + 398, this.y + 50, 20, 20, TextComponent.EMPTY,
                b -> this.deletePack(info)).setTooltips("gui.touhou_little_maid.resources_download.delete"));
    }

    /**
     * 加一个「需要自己画悬停提示」的扁平按钮。
     */
    private void addTooltipWidget(FlatColorButton button) {
        this.addRenderableWidget(button);
        this.tooltipButtons.add(button);
    }

    private void addPageButtons() {
        this.addRenderableWidget(new FlatColorButton(this.x, this.y + 218, 40, 20, new TextComponent("<"), b -> {
            if (this.currentPage > 0) {
                this.currentPage--;
                this.init();
            }
        }));
        this.addRenderableWidget(new FlatColorButton(this.x + 228, this.y + 218, 40, 20, new TextComponent(">"), b -> {
            if ((this.currentPage + 1) * PACK_PER_PAGE < this.showInfos.size()) {
                this.currentPage++;
                this.init();
            }
        }));
    }

    private void addPackButtons() {
        int startIndex = this.currentPage * PACK_PER_PAGE;
        for (int i = startIndex; i < startIndex + PACK_PER_PAGE; i++) {
            if (i >= this.showInfos.size()) {
                break;
            }
            DownloadInfo info = this.showInfos.get(i);
            int yOffset = this.y + 26 + (i - startIndex) * 48;
            final int index = i;
            PackInfoButton button = new PackInfoButton(this.x, yOffset, info, b -> {
                this.selectIndex = index;
                this.init();
            });
            if (this.selectIndex == i) {
                button.setSelect(true);
            }
            this.addRenderableWidget(button);
        }
    }

    private void addBaseButtons() {
        int i = 0;
        for (Condition c : Condition.values()) {
            int buttonWidth = 52;
            int xPos = this.x + (buttonWidth + 2) * i;
            String key = c.name().toLowerCase(Locale.US);
            String nameKey = "gui.touhou_little_maid.resources_download." + key;
            String descKey = nameKey + ".tips";
            Button button = new Button(xPos, this.y + 3, buttonWidth, 20,
                    new TranslatableComponent(nameKey), b -> this.setCondition(c));
            if (this.condition.equals(c)) {
                // 与上游一致：当前页签置灰，既表示选中也不再响应点击
                button.active = false;
            }
            this.conditionButtons.add(button);
            this.conditionTipKeys.add(descKey);
            this.addRenderableWidget(button);
            i++;
        }
        this.addTooltipWidget(new FlatColorButton(this.x + 400, this.y + 2, 20, 20, TextComponent.EMPTY,
                b -> this.closeToMaidGui()).setTooltips("gui.touhou_little_maid.skin.button.close"));
        this.addRenderableWidget(new Button(this.x + 270, this.y + 218, 150, 20,
                new TranslatableComponent("gui.touhou_little_maid.resources_download.open_folder"),
                b -> CustomPackLoader.openPackFolder()));
    }

    /**
     * 按当前页签 + 搜索词算出要显示的列表。
     */
    private void initShowInfos() {
        this.showInfos.clear();
        switch (this.condition) {
            case MAID -> this.showInfos.addAll(InfoGetManager.getTypedDownloadInfoList(DownloadInfo.TypeEnum.MAID));
            case CHAIR -> this.showInfos.addAll(InfoGetManager.getTypedDownloadInfoList(DownloadInfo.TypeEnum.CHAIR));
            case SOUND -> this.showInfos.addAll(InfoGetManager.getTypedDownloadInfoList(DownloadInfo.TypeEnum.SOUND));
            case UPDATE -> {
                for (DownloadInfo info : InfoGetManager.DOWNLOAD_INFO_LIST_ALL) {
                    if (info.getStatus() == DownloadStatus.NEED_UPDATE) {
                        this.showInfos.add(info);
                    }
                }
            }
            default -> this.showInfos.addAll(InfoGetManager.DOWNLOAD_INFO_LIST_ALL);
        }
        if (this.textField != null && !this.textField.getValue().isBlank()) {
            String search = this.textField.getValue().toLowerCase(Locale.US);
            this.showInfos.removeIf(info -> !info.getKeyword().contains(search));
        }
        // 列表变短之后，选中的下标可能越界（换页签 / 搜索时最常见）
        if (this.selectIndex >= this.showInfos.size()) {
            this.selectIndex = -1;
        }
    }

    // ------------------------------------------------------------------ 绘制

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBase(poseStack);
        this.renderSearchBox(poseStack, mouseX, mouseY, partialTick);
        this.renderPageNumber(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        this.renderBaseButtons(poseStack);
        this.renderPackHandleButtons(poseStack);
        this.renderNoDataTips(poseStack);
        this.renderConditionTips(poseStack, mouseX, mouseY);
        // FlatColorButton 的提示必须最后画，否则会被后画的控件盖住
        for (FlatColorButton button : this.tooltipButtons) {
            button.renderToolTip(poseStack, this, mouseX, mouseY);
        }
    }

    /**
     * 背景：整屏压暗 + 右侧两块深色面板。
     */
    private void renderBase(PoseStack poseStack) {
        fillGradient(poseStack, 0, 0, this.width, this.height, 0xE2000000, 0xE2000000);
        fillGradient(poseStack, this.x + 270, this.y + 26, this.x + 420, this.y + 72, 0xFF232221, 0xFF232221);
        fillGradient(poseStack, this.x + 270, this.y + 74, this.x + 420, this.y + 216, 0xFF232221, 0xFF232221);
    }

    private void renderNoDataTips(PoseStack poseStack) {
        if (!InfoGetManager.DOWNLOAD_INFO_LIST_ALL.isEmpty()) {
            return;
        }
        List<FormattedCharSequence> split = this.font.split(
                new TranslatableComponent("gui.touhou_little_maid.resources_download.fail"), 200);
        int yOffset = this.y + 100;
        for (FormattedCharSequence sequence : split) {
            drawCenteredString(poseStack, this.font, sequence, this.x + 134, yOffset,
                    ChatFormatting.RED.getColor());
            yOffset += 12;
        }
    }

    private void renderPageNumber(PoseStack poseStack) {
        int maxPage = this.showInfos.isEmpty() ? 0 : (this.showInfos.size() - 1) / PACK_PER_PAGE;
        String pageInfo = String.format("%d/%d", this.currentPage + 1, maxPage + 1);
        drawString(poseStack, this.font, pageInfo,
                this.x + 134 - this.font.width(pageInfo) / 2,
                this.y + 227 - this.font.lineHeight / 2, 0xF3EFE0);
    }

    /**
     * 右上角：选中的包名 + 两个小图标的底（打开主页 / 删除）。
     */
    private void renderPackHandleButtons(PoseStack poseStack) {
        if (this.selectIndex < 0 || this.selectIndex >= this.showInfos.size()) {
            return;
        }
        DownloadInfo info = this.showInfos.get(this.selectIndex);
        drawCenteredString(poseStack, this.font, new TranslatableComponent(info.getName()),
                this.x + 345, this.y + 34, 0xFFFFFF);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, BG);
        blit(poseStack, this.x + 400, this.y + 52, 0, 16, 16, 16);
        blit(poseStack, this.x + 274, this.y + 52, 16, 16, 16, 16);
    }

    private void renderBaseButtons(PoseStack poseStack) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, BG);
        blit(poseStack, this.x + 402, this.y + 4, 32, 16, 16, 16);
    }

    private void renderSearchBox(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        drawString(poseStack, this.font,
                new TranslatableComponent("gui.touhou_little_maid.resources_download.hot_search"),
                this.x + 274, this.y + 102, 0xFFFFFF);
        this.font.drawWordWrap(new TranslatableComponent("gui.touhou_little_maid.resources_download.hot_search_key"),
                this.x + 274, this.y + 115, 146, ChatFormatting.GRAY.getColor());
        if (this.textField == null) {
            return;
        }
        this.textField.render(poseStack, mouseX, mouseY, partialTick);
        if (this.textField.getValue().isEmpty() && !this.textField.isFocused()) {
            drawString(poseStack, this.font,
                    new TranslatableComponent("gui.touhou_little_maid.resources_download.search")
                            .withStyle(ChatFormatting.ITALIC),
                    this.x + 277, this.y + 83, 0x777777);
        }
    }

    /**
     * 页签的悬停提示。用鼠标坐标判断，而不是 {@code isHoveredOrFocused()} ——
     * 那个值只在 {@code render} 里刷新，重建控件之后可能还是上一帧的值。
     */
    private void renderConditionTips(PoseStack poseStack, int mouseX, int mouseY) {
        for (int i = 0; i < this.conditionButtons.size(); i++) {
            Button button = this.conditionButtons.get(i);
            if (!button.visible) {
                continue;
            }
            if (mouseX >= button.x && mouseX < button.x + button.getWidth()
                && mouseY >= button.y && mouseY < button.y + button.getHeight()) {
                this.renderTooltip(poseStack, new TranslatableComponent(this.conditionTipKeys.get(i)), mouseX, mouseY);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ 输入

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String value = this.textField == null ? "" : this.textField.getValue();
        super.resize(minecraft, width, height);
        if (this.textField != null) {
            this.textField.setValue(value);
        }
    }

    @Override
    public void tick() {
        if (this.textField != null) {
            this.textField.tick();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.textField != null && this.textField.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.textField);
            return true;
        } else if (this.textField != null && this.textField.isFocused()) {
            this.textField.setFocus(false);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.textField == null) {
            return false;
        }
        String perText = this.textField.getValue();
        if (this.textField.charTyped(codePoint, modifiers)) {
            if (!Objects.equals(perText, this.textField.getValue())) {
                // 搜索词变了就回到第一页，否则会停在一个可能已经不存在的页码上
                this.currentPage = 0;
                this.init();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.textField == null) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        // ESC 交给 super，这样走的是 onClose（回到世界；关闭按钮才回女仆界面）
        if (keyCode == 256) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        String preText = this.textField.getValue();
        if (this.textField.keyPressed(keyCode, scanCode, modifiers)) {
            if (!Objects.equals(preText, this.textField.getValue())) {
                this.currentPage = 0;
                this.init();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void insertText(String text, boolean overwrite) {
        if (this.textField == null) {
            return;
        }
        if (overwrite) {
            this.textField.setValue(text);
        } else {
            this.textField.insertText(text);
        }
    }

    /**
     * 点右上角的关闭按钮：请求服务端重新打开女仆界面。
     *
     * <p><b>与上游 1.20.1 一致，这里只发包、不 {@code setScreen}</b>：服务端会关掉当前菜单
     * 并按主界面页签重开一个，客户端收到 {@code OpenScreen} 后自然用女仆界面覆盖掉这一页。
     * 自己先 {@code setScreen(null)} 会闪一帧空屏，而且与服务端的菜单状态不同步。</p>
     *
     * <p>没有携带实体 id（不是从女仆界面进来的）时退回 {@link #onClose()}，
     * 也就是回到世界 —— 那种情况下没有「该回到哪个女仆」可言。</p>
     */
    private void closeToMaidGui() {
        if (this.entityId >= 0) {
            MaidClientPacketSender.sendTab(TabIndex.MAIN, this.entityId);
        } else {
            this.onClose();
        }
    }

    /**
     * 关闭界面。
     *
     * <p>下载 / 删除过包时把「需要重载」的提示打到屏幕标题与聊天栏上 ——
     * 本移植版在装包之后已经自动重载了，这条提示留着是因为<b>贴图缓存</b>
     * （模型包里的图）在某些显卡上要等一次资源重载才完全生效，属于兜底说明。</p>
     *
     * <p><b>ESC 的语义是「回到世界」</b>，所以这里不发重开女仆界面的包 ——
     * 那是关闭按钮独有的行为（见 {@link #closeToMaidGui()}）。</p>
     */
    @Override
    public void onClose() {
        if (this.needReload && this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.gui.setTitle(
                    new TranslatableComponent("gui.touhou_little_maid.resources_download.need_reload.title"));
            this.minecraft.gui.setSubtitle(
                    new TranslatableComponent("gui.touhou_little_maid.resources_download.need_reload.subtitle"));
            this.minecraft.player.sendMessage(
                    new TranslatableComponent("gui.touhou_little_maid.resources_download.need_reload.subtitle"),
                    Util.NIL_UUID);
        }
        super.onClose();
    }

    // ------------------------------------------------------------------ 状态

    /**
     * 扫描 {@code tlm_custom_pack} 下的 zip，算出它们的 CRC32。
     */
    private void getCrc32Infos() {
        this.crc32Infos.clear();
        Path folder = CustomPackLoader.getPackFolder();
        if (!Files.isDirectory(folder)) {
            return;
        }
        try {
            Files.walkFileTree(folder, EnumSet.noneOf(FileVisitOption.class), 1, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().endsWith(PACK_FILE_SUFFIX)) {
                        long crc32 = InfoGetManager.checksumCrc32(file.toFile());
                        if (crc32 >= 0) {
                            crc32Infos.put(crc32, file.toFile().getName());
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            TouhouLittleMaid.LOGGER.warn("[Futo] failed to scan {} for CRC32", folder, e);
        }
    }

    /**
     * 用 CRC32 反推每个包的状态。
     *
     * <p>顺序是「先看是否等于当前 {@code checksum}，再看是否落在 {@code old_version} 里」：
     * 同一个包的新旧 CRC 理论上不会相同，但先判命中当前版本更符合直觉。</p>
     */
    private void checkDownloadInfo() {
        for (DownloadInfo info : InfoGetManager.DOWNLOAD_INFO_LIST_ALL) {
            if (info.getStatus() != DownloadStatus.DOWNLOADING) {
                info.setStatus(DownloadStatus.NOT_DOWNLOAD);
            }
            for (Long crc32 : this.crc32Infos.keySet()) {
                if (crc32.equals(info.getChecksum())) {
                    info.setStatus(DownloadStatus.DOWNLOADED);
                    break;
                }
                if (info.getOldVersion().contains(crc32)) {
                    info.setStatus(DownloadStatus.NEED_UPDATE);
                    break;
                }
            }
        }
    }

    private void setCondition(Condition condition) {
        if (this.condition != condition) {
            this.condition = condition;
            this.currentPage = 0;
            this.selectIndex = -1;
            this.init();
        }
    }

    private void openPackWebsite(DownloadInfo info) {
        String website = info.getWebsite();
        if (website == null || website.isBlank() || this.minecraft == null) {
            return;
        }
        this.minecraft.setScreen(new ConfirmLinkScreen(yes -> {
            if (yes) {
                Util.getPlatform().openUri(website);
            }
            this.minecraft.setScreen(this);
        }, website, false));
    }

    private void deletePack(DownloadInfo info) {
        if (info.getStatus() != DownloadStatus.DOWNLOADED && info.getStatus() != DownloadStatus.NEED_UPDATE) {
            return;
        }
        Set<String> deleteFiles = this.getDeleteFiles(info);
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.setScreen(new ConfirmScreen(yes -> this.deleteFilesAndReload(yes, deleteFiles),
                new TranslatableComponent("gui.touhou_little_maid.resources_download.delete.confirm"),
                new TranslatableComponent(info.getName())));
    }

    /**
     * 更新 = 删掉旧版本（含 {@code old_version} 对应的那些文件名）再下载。
     */
    private void updatePack(DownloadInfo info) {
        Set<String> deleteFiles = this.getDeleteFiles(info);
        this.deleteFiles(deleteFiles);
        info.setStatus(DownloadStatus.DOWNLOADING);
        InfoGetManager.downloadPack(info);
        this.needReload = true;
        this.getCrc32Infos();
        this.checkDownloadInfo();
        this.init();
    }

    /**
     * 要删掉哪些文件：包名本身，加上所有「CRC32 命中 {@code old_version}」的本地文件。
     *
     * <p>后者是必要的：同一个包的历史版本文件名可能不同（作者改过文件名），
     * 只删当前文件名会留下一堆占着位置的旧 zip。</p>
     */
    private Set<String> getDeleteFiles(DownloadInfo info) {
        Set<String> deleteFiles = new LinkedHashSet<>();
        deleteFiles.add(info.getFileName());
        for (Long version : info.getOldVersion()) {
            String fileName = this.crc32Infos.get(version);
            if (fileName != null) {
                deleteFiles.add(fileName);
            }
        }
        return deleteFiles;
    }

    private void deleteFiles(Set<String> deleteFiles) {
        for (String fileName : deleteFiles) {
            try {
                Path file = CustomPackLoader.getPackFolder().resolve(fileName);
                if (Files.isRegularFile(file)) {
                    Files.delete(file);
                }
            } catch (IOException e) {
                TouhouLittleMaid.LOGGER.warn("[Futo] failed to delete {}", fileName, e);
            }
        }
    }

    /**
     * 确认删除之后：删文件 → 重载 → 重扫 CRC32 → 重建界面。
     *
     * <p>重载放在删文件之后、重建之前 —— 否则界面里的模型列表还带着已删掉的包。</p>
     */
    private void deleteFilesAndReload(boolean yes, Set<String> deleteFiles) {
        if (yes) {
            this.deleteFiles(deleteFiles);
            this.needReload = true;
            CustomPackLoader.reloadPacks();
            this.getCrc32Infos();
            this.checkDownloadInfo();
            this.init();
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(this);
        }
    }

    /**
     * 顶部页签。
     */
    public enum Condition {
        /** 全部 */
        ALL,
        /** 女仆模型包 */
        MAID,
        /** 坐垫模型包 */
        CHAIR,
        /** 声音包 */
        SOUND,
        /** 需要更新 */
        UPDATE
    }
}

