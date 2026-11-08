package com.github.tartaricacid.touhoulittlemaid.client.screen;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.resource.MaidModelRegistry;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 换肤 / 换模型界面，移植自上游 {@code client/gui/entity/model/MaidModelGui} 与它的基类
 * {@code AbstractModelGui}。
 *
 * <p><b>与上游的结构差异。</b>上游那套是「模型包（pack）」驱动的：从
 * {@code CustomPackLoader.MAID_MODELS.getPackList()} 取一组 {@code CustomModelPack}，
 * 每个包带名字、图标、简介和一批模型；界面顶部一排包页签、右侧滚动条、一侧包信息栏。
 * 本移植版还没有「包」这一层 —— {@link MaidModelRegistry} 只是一个扁平的
 * {@code Map<ResourceLocation, MaidModelInfo>}。所以这里把包压平成<b>一条排序后的
 * 模型列表</b>，保留上游的<b>网格布局与交互</b>，省掉包页签与滚动条。</p>
 *
 * <p><b>照搬的数值</b>（来自 {@code AbstractModelGui} / {@code SkinGuiNumber}）：</p>
 * <ul>
 *     <li>底图 {@code textures/gui/skin_select.png}、侧图 {@code skin_select_side.png}；</li>
 *     <li>网格：每页 5 行 × 11 列 = 55 格，格间距 20 × 30，起始偏移 {@code (-108, -61)}；</li>
 *     <li>格子按钮 UV {@code (41, 201)}、尺寸 15×24、悬停下移 24；</li>
 *     <li>翻页按钮 {@code (middleX-119 / middleX+99, middleY-101)}、20×20；</li>
 *     <li>关闭按钮 {@code (middleX+122, middleY-97)}、21×17、UV {@code (58, 201)}、下移 18；</li>
 *     <li>缩略图用 {@link InventoryScreen#renderEntityInInventory}，缩放乘 {@code renderItemScale}。</li>
 * </ul>
 *
 * <p><b>缩略图实体只建一次。</b>{@code EntityMaid} 的构造器会建一个 Brain、一份属性表和
 * 两个 {@code ItemStackHandler}，而 {@code GeckoMaidEntity} 的动画缓存又是按实体实例做
 * key 的 —— 每帧新建既慢，又会让动画永远停在第一帧。{@code Entity#level} 是
 * {@code final}，所以只在换世界时重建。</p>
 */
public class MaidModelScreen extends Screen {
    private static final ResourceLocation BG =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/skin_select.png");
    private static final ResourceLocation SIDE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/skin_select_side.png");

    /** 每页 5 行，对应上游 {@code SkinGuiNumber.MODEL_TOTAL_ROW}。 */
    private static final int MODEL_TOTAL_ROW = 5;
    /** 每行 11 个，对应上游 {@code SkinGuiNumber.MODEL_PER_ROW}。 */
    private static final int MODEL_PER_ROW = 11;
    private static final int MODEL_PER_PAGE = MODEL_TOTAL_ROW * MODEL_PER_ROW;

    private static final int GRID_X = -108;
    private static final int GRID_Y = -61;
    private static final int STEP_X = 20;
    private static final int STEP_Y = 30;
    /** 每行最后一格的 X 偏移，用来判断该换行了。 */
    private static final int GRID_LAST_X = GRID_X + STEP_X * (MODEL_PER_ROW - 1);

    private final EntityMaid maid;
    @Nullable
    private final Screen parent;
    /** 排序后的可选模型。 */
    private final List<MaidModelInfo> models;
    /** 画缩略图用的临时实体，界面生命周期内只建一次。 */
    @Nullable
    private EntityMaid previewEntity;
    private int page;

    public MaidModelScreen(EntityMaid maid, @Nullable Screen parent) {
        super(new TranslatableComponent("gui.touhou_little_maid.button.skin"));
        this.maid = maid;
        this.parent = parent;
        this.models = collectModels();
    }

    /**
     * 收集可选模型并按 <b>模型 id</b> 排序。
     *
     * <p>{@code MaidModelRegistry} 内部是 {@code HashMap}，迭代顺序不可依赖 ——
     * 不排序的话每次打开界面格子顺序都不一样。上游按「包名 + 包内顺序」排，
     * 本移植版没有包这一层，就退化成按 id 排。</p>
     */
    private static List<MaidModelInfo> collectModels() {
        List<MaidModelInfo> list = new ArrayList<>();
        for (MaidModelInfo info : MaidModelRegistry.all()) {
            // 只列真正能画出来的：没有几何模型的条目渲染时会回落到默认模型，列出来没意义
            if (info.getModel() != null) {
                list.add(info);
            }
        }
        list.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                a.getModelId().toString(), b.getModelId().toString()));
        return list;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int middleX = this.width / 2;
        int middleY = this.height / 2;

        int maxPage = Math.max(1, (this.models.size() - 1) / MODEL_PER_PAGE + 1);
        // 顺带夹回越界页码：列表长度变化时不会停在空页
        this.page = Math.max(0, Math.min(this.page, maxPage - 1));

        Button prev = new Button(middleX - 119, middleY - 101, 20, 20, new TextComponent("<"), b -> {
            if (this.page > 0) {
                this.page--;
                this.init();
            }
        });
        Button next = new Button(middleX + 99, middleY - 101, 20, 20, new TextComponent(">"), b -> {
            if ((this.page + 1) * MODEL_PER_PAGE < this.models.size()) {
                this.page++;
                this.init();
            }
        });
        prev.active = this.page > 0;
        next.active = this.page < maxPage - 1;
        this.addRenderableWidget(prev);
        this.addRenderableWidget(next);

        this.addRenderableWidget(new ImageButton(middleX + 122, middleY - 97, 21, 17,
                58, 201, 18, BG, b -> this.onClose()));

        this.addModelButtons(middleX, middleY);
    }

    /**
     * 一格一个模型。坐标算法与上游 {@code addModelButton} 相同：向右铺满 11 个后换行。
     */
    private void addModelButtons(int middleX, int middleY) {
        int from = this.page * MODEL_PER_PAGE;
        int to = Math.min(from + MODEL_PER_PAGE, this.models.size());
        int offsetX = GRID_X;
        int offsetY = GRID_Y;
        for (int i = from; i < to; i++) {
            MaidModelInfo info = this.models.get(i);
            this.addRenderableWidget(new ImageButton(middleX + offsetX, middleY + offsetY,
                    15, 24, 41, 201, 24, BG, b -> this.selectModel(info)));
            offsetX += STEP_X;
            if (offsetX > GRID_LAST_X) {
                offsetX = GRID_X;
                offsetY += STEP_Y;
            }
        }
    }

    /**
     * 选中一个模型。
     *
     * <p>{@code ModelId} 是 {@code SynchedEntityData}，本地改了会被服务端下一次同步覆盖，
     * 所以必须发包；但本地先改能让左侧立绘立刻反馈，不用等一个往返。</p>
     *
     * <p>顺手清空手持物品的动画缓存：那对 {@code ItemStack} 是「换手了没」的判据，
     * 不清的话新模型的手部骨骼会沿用上一个模型的姿态。</p>
     */
    private void selectModel(MaidModelInfo info) {
        String modelId = info.getModelId().toString();
        this.maid.setModelId(modelId);
        this.maid.getHandItemsForAnimation()[0] = ItemStack.EMPTY;
        this.maid.getHandItemsForAnimation()[1] = ItemStack.EMPTY;
        MaidClientPacketSender.sendModel(modelId, this.maid.getId());
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        int middleX = this.width / 2;
        int middleY = this.height / 2;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        // 主面板（右侧网格区）：上游 BG 的 (0,0) 起
        RenderSystem.setShaderTexture(0, BG);
        blit(poseStack, middleX - 128, middleY - 110, 0, 0, 256, 220);

        // 左侧立绘区：SIDE 的 (0,0) 128x220
        RenderSystem.setShaderTexture(0, SIDE);
        blit(poseStack, middleX - 132, middleY - 110, 0, 0, 128, 220);

        this.drawSelectedModel(poseStack, middleX, middleY, mouseX, mouseY);
        this.drawThumbnails(poseStack, middleX, middleY);

        // 控件（格子底图与按钮）画在贴图之上，文字最后叠
        super.render(poseStack, mouseX, mouseY, partialTick);

        int maxPage = Math.max(1, (this.models.size() - 1) / MODEL_PER_PAGE + 1);
        drawCenteredString(poseStack, this.font,
                String.format("%d/%d", this.page + 1, maxPage),
                middleX, middleY - 120, 0xFFFFFF);

        drawCenteredString(poseStack, this.font, this.currentModelName(),
                middleX - 64, middleY + 96, 0xAAAAAA);
    }

    /** 当前模型的可显示名称（带花括号的当翻译键处理，与上游 {@code ParseI18n} 同义）。 */
    private Component currentModelName() {
        MaidModelInfo info = MaidModelRegistry.get(this.maid.getModelId());
        if (info == null) {
            return new TextComponent(this.maid.getModelId());
        }
        String name = info.getName();
        if (name != null && name.length() > 2 && name.startsWith("{") && name.endsWith("}")) {
            return new TranslatableComponent(name.substring(1, name.length() - 1));
        }
        return new TextComponent(name == null ? this.maid.getModelId() : name);
    }

    /** 左侧：当前选中模型的大立绘，随鼠标转头（与上游一致）。 */
    private void drawSelectedModel(PoseStack poseStack, int middleX, int middleY, int mouseX, int mouseY) {
        EntityMaid preview = this.previewEntity();
        if (preview == null) {
            return;
        }
        preview.setModelId(this.maid.getModelId());
        MaidModelInfo info = MaidModelRegistry.get(this.maid.getModelId());
        float scale = info == null ? 1.0F : info.getRenderItemScale();
        int x = middleX - 64;
        int y = middleY + 90;
        InventoryScreen.renderEntityInInventory(x, y, (int) (45 * scale),
                x - mouseX, y - 40 - mouseY, preview);
    }

    /**
     * 右侧：本页最多 55 个缩略图，一格一个模型。
     *
     * <p>逐帧改同一个实体的 {@code ModelId} 是上游 {@code drawRightEntity} 的做法，
     * 前提就是复用同一个实例。</p>
     */
    private void drawThumbnails(PoseStack poseStack, int middleX, int middleY) {
        EntityMaid preview = this.previewEntity();
        if (preview == null) {
            return;
        }
        int from = this.page * MODEL_PER_PAGE;
        int to = Math.min(from + MODEL_PER_PAGE, this.models.size());
        int offsetX = GRID_X;
        int offsetY = GRID_Y - 3;
        for (int i = from; i < to; i++) {
            MaidModelInfo info = this.models.get(i);
            preview.setModelId(info.getModelId().toString());
            InventoryScreen.renderEntityInInventory(middleX + offsetX, middleY + offsetY,
                    (int) (12 * info.getRenderItemScale()), -25, -20, preview);
            offsetX += STEP_X;
            if (offsetX > GRID_LAST_X) {
                offsetX = GRID_X;
                offsetY += STEP_Y;
            }
        }
    }

    /**
     * 懒建缩略图实体，并校验它属于当前客户端世界。
     *
     * <p>{@code Entity#level} 是 {@code final}，换世界只能重建；不校验的话切维度后
     * 旧实体会带着上一个世界的引用继续被渲染。</p>
     */
    @Nullable
    private EntityMaid previewEntity() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        if (this.previewEntity == null || this.previewEntity.level != minecraft.level) {
            EntityMaid entity = new EntityMaid(InitEntities.MAID, minecraft.level);
            entity.setSilent(true);
            entity.setNoGravity(true);
            entity.setCustomNameVisible(false);
            this.previewEntity = entity;
        }
        return this.previewEntity;
    }

    @Override
    public void onClose() {
        Minecraft minecraft = Minecraft.getInstance();
        if (this.parent != null) {
            minecraft.setScreen(this.parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 当前可选的模型数量（调试 / 命令用）。 */
    public int getModelCount() {
        return this.models.size();
    }
}

