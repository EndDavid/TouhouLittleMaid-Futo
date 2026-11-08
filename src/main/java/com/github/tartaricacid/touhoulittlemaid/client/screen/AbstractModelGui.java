package com.github.tartaricacid.touhoulittlemaid.client.screen;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.CustomModelPack;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import com.github.tartaricacid.touhoulittlemaid.util.ParseI18n;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Widget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 换肤（模型选择）界面基类，逐行移植自上游
 * {@code client/gui/entity/model/AbstractModelGui}。
 *
 * <p>布局数值与上游完全一致，全部集中在这里：</p>
 * <table border="1">
 *     <caption>与上游逐项对应</caption>
 *     <tr><th>项</th><th>上游写法</th></tr>
 *     <tr><td>参考原点</td><td>{@code startX = width/2 + 50}、{@code startY = height/2 + 5}</td></tr>
 *     <tr><td>主底图</td><td>{@code BG} 的 (0,0)，256x180，左上角 {@code (midX-128, midY-80)}</td></tr>
 *     <tr><td>右侧竖条</td><td>{@code SIDE} 的 (0,0)，24x180，{@code (midX+122, midY-80)}</td></tr>
 *     <tr><td>滚动条滑块</td><td>{@code (midX+126, midY-61 + 127*scroll)}，12x15，UV (24,0) / (36,0)</td></tr>
 *     <tr><td>包页签</td><td>底图 {@code (startX-98+28*i, startY-108)} 28x31 UV(116,224)；
 *         图标按钮 {@code (startX-98+28*i, startY-105)} 28x25 UV(116,194)；
 *         图标 16x16 画在 {@code (midX-92+28*i, midY-98)}</td></tr>
 *     <tr><td>翻页按钮</td><td>{@code (startX-119 / startX+99, startY-101)} 20x20，文字 &lt; &gt;</td></tr>
 *     <tr><td>关闭按钮</td><td>{@code (startX+122, startY-97)} 21x17 UV(58,201) 下移 18</td></tr>
 *     <tr><td>模型格</td><td>起点 {@code (-100,-35)}（按钮写成 {@code -108,-61} 因为按钮自带 8/26 内缩），
 *         行距 20x30，一行 11 个</td></tr>
 *     <tr><td>页数文字</td><td>{@code (midX, midY-120)}</td></tr>
 * </table>
 *
 * <p><b>与上游的差异只有一处：</b>上游用 {@code MiscConfig.MODEL_ICON_CACHE} 决定
 * 「画缓存图标还是实时画实体」，本移植版没有图标缓存，所以
 * {@link #drawRightEntity} 永远走实时绘制分支。左上角那个复选框因此也省略。</p>
 */
public abstract class AbstractModelGui<T extends LivingEntity> extends Screen {
    /** 占位点击回调（上游给纯装饰按钮用它）。 */
    public static final Button.OnPress NO_PRESS = b -> {
    };

    protected static final ResourceLocation BG =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/skin_select.png");
    protected static final ResourceLocation SIDE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/skin_select_side.png");

    protected final T entity;
    private final SkinGuiNumber guiNumber;
    private final List<CustomModelPack> modelPackList;

    /**
     * 自己持有的 widget 列表。
     *
     * <p>1.18.2 的 {@code Screen#renderables} 是 private（1.19 才放开），
     * 所以不能像上游那样遍历它来画按钮。这里在 addWidget 里同步记一份，
     * 绘制顺序与上游一致（先按钮后文字）。</p>
     */
    private final List<Widget> ownWidgets = new ArrayList<>();

    protected AbstractModelGui(T entity, List<CustomModelPack> listPack) {
        super(new TextComponent("Custom Model GUI"));
        this.entity = entity;
        this.modelPackList = listPack;
        this.guiNumber = new SkinGuiNumber(modelPackList);
        setPageIndex(Mth.clamp(getPageIndex(), 0, Math.max(0, guiNumber.getPageSize() - 1)));
        setPackIndex(Mth.clamp(getPackIndex(), 0, Math.max(0, guiNumber.getPackSize() - 1)));
        setRowIndex(Mth.clamp(getRowIndex(), 0, guiNumber.getRowSize(getPackIndex())));
    }

    // ------------------------------------------------------------------ 子类实现

    /** 左侧大立绘。 */
    protected abstract void drawLeftEntity(int middleX, int middleY, float mouseX, float mouseY);

    /** 右侧网格里的单个缩略图。 */
    protected abstract void drawRightEntity(PoseStack poseStack, int posX, int posY, MaidModelInfo modelItem);

    /** 悬停提示里追加的额外行（例如「使用声音包」）。 */
    protected abstract void addModelCustomTips(MaidModelInfo modelItem, List<Component> tooltips);

    protected abstract int getPackIndex();

    protected abstract void setPackIndex(int packIndex);

    protected abstract int getRowIndex();

    protected abstract void setRowIndex(int rowIndex);

    protected abstract int getPageIndex();

    protected abstract void setPageIndex(int pageIndex);

    /** 当前选中的模型（用来在网格里画高亮边框）。 */
    protected abstract String getCurrentModelId();

    /** 点了一个格子。 */
    protected abstract void onModelClicked(MaidModelInfo modelItem);

    // ------------------------------------------------------------------ 初始化

    /** 注册 widget 并记进 ownWidgets，保证 drawButton 能画到。 */
    private <W extends Widget & net.minecraft.client.gui.components.events.GuiEventListener> void addOwnedWidget(W widget) {
        this.ownWidgets.add(widget);
        this.addRenderableWidget((net.minecraft.client.gui.components.events.GuiEventListener & Widget & net.minecraft.client.gui.narration.NarratableEntry) widget);
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.ownWidgets.clear();

        int startX = this.width / 2 + 50;
        int startY = this.height / 2 + 5;

        // 包页签：先画一层底图，再叠一个可点击的图标按钮（与上游两层结构一致）
        for (int index = 0; index < 7; index++) {
            addTabButton(startX, startY, index);
        }

        this.addOwnedWidget(new ImageButton(startX + 122, startY - 97, 21, 17,
                58, 201, 18, BG, b -> this.onClose()));

        addPageButton(startX, startY);
        addModelButton(startX, startY);
        addScrollButton(startX, startY);
    }

    private void addTabButton(int startX, int startY, int index) {
        // 底图（不可点）
        this.addOwnedWidget(new ImageButton(startX - 98 + 28 * index, startY - 108,
                28, 31, 116, 224, 0, BG, NO_PRESS));
        // 可点的图标层：只有「当前页里存在这个 tab」时才可见
        ImageButton clickable = new ImageButton(startX - 98 + 28 * index, startY - 105,
                28, 25, 116, 194, 0, BG, b -> {
            setPageIndex(getPageIndex());
            setPackIndex(guiNumber.tabToPackIndex(index, getPageIndex()));
            setRowIndex(0);
            this.init();
        });
        clickable.visible = index < guiNumber.getTabSize(getPackIndex());
        this.addOwnedWidget(clickable);
    }

    private void addPageButton(int startX, int startY) {
        Button prePage = new Button(startX - 119, startY - 101, 20, 20, new TextComponent("<"), b -> {
            if (getPageIndex() > 0) {
                setPageIndex(Mth.clamp(getPageIndex() - 1, 0, guiNumber.getPageSize() - 1));
                setPackIndex(guiNumber.tabToPackIndex(0, getPageIndex()));
                setRowIndex(0);
                this.init();
            }
        });
        Button nextPage = new Button(startX + 99, startY - 101, 20, 20, new TextComponent(">"), b -> {
            if (getPageIndex() < guiNumber.getPageSize() - 1) {
                setPageIndex(Mth.clamp(getPageIndex() + 1, 0, guiNumber.getPageSize() - 1));
                setPackIndex(guiNumber.tabToPackIndex(0, getPageIndex()));
                setRowIndex(0);
                this.init();
            }
        });
        prePage.active = getPageIndex() > 0;
        nextPage.active = getPageIndex() < guiNumber.getPageSize() - 1;
        this.addOwnedWidget(prePage);
        this.addOwnedWidget(nextPage);
    }

    /**
     * 模型格按钮。位置与上游一致：{@code (startX + offsetX - 8, startY + offsetY - 26)}，
     * 起始 {@code offsetX=-100}、{@code offsetY=-35}，所以第一格落在 {@code (-108, -61)}。
     */
    private void addModelButton(int startX, int startY) {
        if (this.modelPackList.isEmpty()) {
            return;
        }
        CustomModelPack pack = this.modelPackList.get(getPackIndex());
        int offsetX = -100;
        int offsetY = -35;
        int fromIndex = guiNumber.modelFromIndex(getRowIndex());
        int toIndex = guiNumber.modelToIndex(getPackIndex(), getRowIndex());
        List<MaidModelInfo> list = pack.getModelList();
        if (fromIndex >= list.size()) {
            return;
        }
        for (MaidModelInfo modelItem : list.subList(fromIndex, toIndex)) {
            this.addOwnedWidget(new ImageButton(startX + offsetX - 8, startY + offsetY - 26,
                    15, 24, 41, 201, 24, BG, b -> this.onModelClicked(modelItem)));
            offsetX += 20;
            if (offsetX > 105) {
                offsetX = -100;
                offsetY += 30;
            }
        }
    }

    private void addScrollButton(int startX, int startY) {
        ImageButton upButton = new ImageButton(startX - 256 / 2 + 253, startY - 73,
                14, 10, 24, 15, 10, SIDE, b -> {
            int row = Mth.clamp(getRowIndex() - 1, 0, guiNumber.getRowSize(getPackIndex()));
            if (row != getRowIndex()) {
                setRowIndex(row);
                this.init();
            }
        });
        ImageButton downButton = new ImageButton(startX - 256 / 2 + 253, startY - 73 + 156,
                14, 10, 38, 15, 10, SIDE, b -> {
            int row = Mth.clamp(getRowIndex() + 1, 0, guiNumber.getRowSize(getPackIndex()));
            if (row != getRowIndex()) {
                setRowIndex(row);
                this.init();
            }
        });
        this.addOwnedWidget(upButton);
        this.addOwnedWidget(downButton);
    }

    // ------------------------------------------------------------------ 渲染

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        poseStack.pushPose();
        poseStack.translate(0, 0, -100);

        int middleX = this.width / 2 + 50;
        int middleY = this.height / 2 + 5;

        this.renderBackground(poseStack);

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, BG);
        blit(poseStack, middleX - 256 / 2, middleY - 80, 0, 0, 256, 180);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, SIDE);
        blit(poseStack, middleX - 256 / 2 + 250, middleY - 80, 0, 0, 24, 180);

        this.drawScrollSide(poseStack, middleX, middleY);
        this.drawButton(poseStack, mouseX, mouseY, partialTicks);
        this.drawTabIcon(poseStack, middleX, middleY);

        // 左侧立绘：用 scissor 裁到面板左半边，避免画到网格上
        double scale = getMinecraft().getWindow().getGuiScale();
        RenderSystem.enableScissor(0, 0,
                (int) ((middleX - 256 / 2) * scale), (int) (this.height * scale));
        drawLeftEntity(middleX, middleY, mouseX, mouseY);
        RenderSystem.disableScissor();

        this.drawEntity(poseStack, middleX, middleY);
        this.drawTooltips(poseStack, mouseX, mouseY, middleX, middleY);

        poseStack.popPose();
    }

    private void drawButton(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        for (Widget button : this.ownWidgets) {
            button.render(poseStack, mouseX, mouseY, partialTicks);
        }
    }

    private void drawScrollSide(PoseStack poseStack, int middleX, int middleY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, SIDE);
        float scroll = guiNumber.getCurrentScroll(getPackIndex(), getRowIndex());
        if (guiNumber.canScroll(getPackIndex(), getRowIndex())) {
            blit(poseStack, middleX - 256 / 2 + 254, middleY - 61 + (int) (127 * scroll), 24, 0, 12, 15);
        } else {
            blit(poseStack, middleX - 256 / 2 + 254, middleY - 61 + (int) (127 * scroll), 36, 0, 12, 15);
        }
    }

    /**
     * 包页签的图标。竖向图集按帧播放，横向图集直接贴。
     *
     * <p>上游靠自定义的 {@code SizeTexture} 读贴图尺寸来判断；本移植版没有那个类，
     * 因此统一按「静态图标」处理（{@code AnimationState.FALSE}）——
     * 表现差异只是动态图标不会动，不影响选择功能。</p>
     */
    private void drawTabIcon(PoseStack poseStack, int middleX, int middleY) {
        for (int index = 0; index < guiNumber.getTabSize(getPackIndex()); index++) {
            CustomModelPack pack = this.modelPackList.get(guiNumber.tabToPackIndex(index, getPageIndex()));
            ResourceLocation icon = pack.getIcon();
            if (icon == null) {
                continue;
            }
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, icon);
            blit(poseStack, middleX - 92 + 28 * index, middleY - 98, 0, 0, 16, 16, 16, 16);
        }
    }

    /** 网格 + 包信息文本。 */
    private void drawEntity(PoseStack poseStack, int middleX, int middleY) {
        if (this.modelPackList.isEmpty()) {
            drawCenteredString(poseStack, this.font,
                    new TranslatableComponent("gui.touhou_little_maid.skin.no_pack"),
                    middleX, middleY, 0xFFFFFF);
            return;
        }
        CustomModelPack pack = this.modelPackList.get(getPackIndex());
        this.drawPackInfoText(poseStack, pack, middleX, middleY);

        int offsetX = -100;
        int offsetY = -38;
        int fromIndex = guiNumber.modelFromIndex(getRowIndex());
        int toIndex = guiNumber.modelToIndex(getPackIndex(), getRowIndex());
        List<MaidModelInfo> list = pack.getModelList();
        if (fromIndex >= list.size()) {
            return;
        }
        String current = this.getCurrentModelId();
        for (MaidModelInfo modelItem : list.subList(fromIndex, toIndex)) {
            int x = middleX + offsetX;
            int y = middleY + offsetY;
            drawRightEntity(poseStack, x, y, modelItem);
            // 当前选中的模型打个黄色底框，方便一眼看到（上游没有，但它本来就靠图标缓存区分）
            if (current != null && current.equals(modelItem.getModelId().toString())) {
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                fill(poseStack, x - 8, y - 24, x + 8, y + 2, 0x60FFD700);
            }
            offsetX += 20;
            if (offsetX > 105) {
                offsetX = -100;
                offsetY += 30;
            }
        }
    }

    /** 左侧的包名 / 描述 / 作者 / 版本 / 日期，以及顶部页数。 */
    private void drawPackInfoText(PoseStack poseStack, CustomModelPack pack, int middleX, int middleY) {
        int offsetY = -80;
        int sideMiddleX = (middleX - 256 / 2) / 2;

        // 包名（可换行）
        MutableComponent packName = ParseI18n.parse(pack.getPackName());
        List<FormattedCharSequence> split = this.font.split(packName, (middleX - 256 / 2) - 20);
        for (FormattedCharSequence line : split) {
            offsetY += 10;
            drawCenteredString(poseStack, this.font, line, sideMiddleX, middleY + offsetY, 0xFFFFFF);
        }

        // 描述
        for (Component str : ParseI18n.parse(pack.getDescription())) {
            for (FormattedCharSequence line : this.font.split(str, (middleX - 256 / 2) - 20)) {
                offsetY += 10;
                drawCenteredString(poseStack, this.font, line, sideMiddleX, middleY + offsetY,
                        ChatFormatting.DARK_GRAY.getColor());
            }
        }

        // 作者：每行两个（与上游 Lists.partition(author, 2) 一致）
        if (!pack.getAuthor().isEmpty()) {
            for (List<String> pair : Lists.partition(pack.getAuthor(), 2)) {
                offsetY += 10;
                drawCenteredString(poseStack, this.font,
                        new TextComponent(pair.toString()).withStyle(ChatFormatting.GOLD),
                        sideMiddleX, middleY + offsetY, 0xFFFFFF);
            }
        }

        // 版本
        if (pack.getVersion() != null) {
            offsetY += 10;
            drawCenteredString(poseStack, this.font,
                    new TranslatableComponent("gui.touhou_little_maid.skin.text.version", pack.getVersion())
                            .withStyle(ChatFormatting.DARK_AQUA),
                    sideMiddleX, middleY + offsetY, 0xFFFFFF);
        }

        // 日期
        if (pack.getDate() != null) {
            offsetY += 10;
            drawCenteredString(poseStack, this.font,
                    new TranslatableComponent("gui.touhou_little_maid.skin.text.date", pack.getDate())
                            .withStyle(ChatFormatting.GREEN),
                    sideMiddleX, middleY + offsetY, 0xFFFFFF);
        }

        // 页数
        drawCenteredString(poseStack, this.font,
                String.format("%s/%s", getPageIndex() + 1, guiNumber.getPageSize()),
                middleX, middleY - 120, 0xFFFFFF);
    }

    /**
     * 悬停提示：模型格、包页签、关闭按钮 —— 三处判定与上游逐条对应。
     */
    private void drawTooltips(PoseStack poseStack, int mouseX, int mouseY, int middleX, int middleY) {
        if (!this.modelPackList.isEmpty()) {
            CustomModelPack pack = this.modelPackList.get(getPackIndex());
            int offsetX = -100;
            int offsetY = -35;
            int fromIndex = guiNumber.modelFromIndex(getRowIndex());
            int toIndex = guiNumber.modelToIndex(getPackIndex(), getRowIndex());
            List<MaidModelInfo> list = pack.getModelList();
            if (fromIndex < list.size()) {
                for (MaidModelInfo modelItem : list.subList(fromIndex, toIndex)) {
                    boolean inX = middleX + offsetX - 8 < mouseX && mouseX < middleX + offsetX + 7;
                    boolean inY = middleY + offsetY - 23 < mouseY && mouseY < middleY + offsetY + 1;
                    if (inX && inY) {
                        List<Component> tooltips = new ArrayList<>();
                        tooltips.add(ParseI18n.parse(modelItem.getName()));
                        tooltips.addAll(ParseI18n.parse(modelItem.getDescription()));
                        addModelCustomTips(modelItem, tooltips);
                        if (getMinecraft().options.advancedItemTooltips) {
                            tooltips.add(new TextComponent(modelItem.getModelId().toString())
                                    .withStyle(ChatFormatting.DARK_GRAY));
                        }
                        tooltips.add(ParseI18n.parse(pack.getPackName()).withStyle(ChatFormatting.BLUE));
                        this.renderComponentTooltip(poseStack, tooltips, mouseX, mouseY);
                    }
                    offsetX += 20;
                    if (offsetX > 105) {
                        offsetX = -100;
                        offsetY += 30;
                    }
                }
            }
        }

        // 包页签提示
        for (int index = 0; index < guiNumber.getTabSize(getPackIndex()); index++) {
            boolean inX = middleX - 98 + 28 * index < mouseX && mouseX < middleX - 98 + 28 * index + 28;
            boolean inY = middleY - 108 < mouseY && mouseY < middleY - 108 + 31;
            if (inX && inY) {
                CustomModelPack hoverPack = this.modelPackList.get(
                        guiNumber.tabToPackIndex(index, getPageIndex()));
                this.renderTooltip(poseStack, ParseI18n.parse(hoverPack.getPackName()), mouseX, mouseY);
            }
        }

        // 关闭按钮提示
        boolean closeX = (middleX + 122) < mouseX && mouseX < (middleX + 143);
        boolean closeY = (middleY - 97) < mouseY && mouseY < (middleY - 80);
        if (closeX && closeY) {
            this.renderTooltip(poseStack,
                    new TranslatableComponent("gui.touhou_little_maid.skin.button.close"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0) {
            int direction = delta > 0 ? 1 : -1;
            int row = Mth.clamp(getRowIndex() - direction, 0, guiNumber.getRowSize(getPackIndex()));
            if (row != getRowIndex()) {
                setRowIndex(row);
                this.init();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    protected Minecraft getMinecraft() {
        return Minecraft.getInstance();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 供子类判断空列表。 */
    protected int packCount() {
        return this.modelPackList.size();
    }

    /** 当前包的模型数量，子类画「当前模型名」时用得上。 */
    protected int modelCount(int packIndex) {
        return guiNumber.getModelSize(packIndex);
    }
}

