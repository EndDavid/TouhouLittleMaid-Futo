package com.github.tartaricacid.touhoulittlemaid.client.screen;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.resource.MaidModelRegistry;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import com.github.tartaricacid.touhoulittlemaid.client.screen.widget.DirectButton;
import com.github.tartaricacid.touhoulittlemaid.client.screen.widget.ImageButtonWithId;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.network.SaveSwitcherDataPacket;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityModelSwitcher;
import com.github.tartaricacid.touhoulittlemaid.util.ParseI18n;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 模型切换器的主界面，移植自上游 {@code client/gui/block/ModelSwitcherGui}。
 *
 * <p>左边是「当前选中模式」的编辑区（女仆立绘 + 选皮肤 + 朝向 + 头顶文字），
 * 右边是模式列表（每页 6 行，可翻页、可增删）。</p>
 *
 * <p>它<b>不是</b> {@code AbstractModelGui} 的子类：那个类是「模型选择器」，
 * 而这里是「模式列表编辑器」，只有点「皮肤」按钮之后才进入
 * {@link ModelSwitcherModelGui}（那个才是 {@code AbstractModelGui} 的子类）。</p>
 *
 * <h2>什么时候保存</h2>
 * <p>改的东西都在客户端这份 {@link TileEntityModelSwitcher#getInfoList()} 拷贝上，
 * 所以要发两次 {@link SaveSwitcherDataPacket}：</p>
 * <ul>
 *     <li>点「保存」按钮 —— 不关界面也能落盘；</li>
 *     <li>{@link #onClose()} —— 按 ESC / 走远被关掉时兜底。</li>
 * </ul>
 *
 * <h2>与上游的差异</h2>
 * <ol>
 *     <li>{@code render} 要自己调 {@code renderBackground}（1.18.2 的 {@code Screen#render} 不代劳）。</li>
 *     <li>1.18.2 没有 {@code GuiGraphics}，文字/贴图都走 {@code PoseStack} 的老 API；
 *         实体预览用 {@code InventoryScreen#renderEntityInInventory}（6 参，没有 1.20 那个 {@code yOffset}）。</li>
 *     <li>模型名从 {@link MaidModelRegistry} 取（上游是 {@code CustomPackLoader.MAID_MODELS.getInfo}）。</li>
 *     <li>预览实体用 {@link InitEntities#MAID} 显式构造，并持有到界面关闭 ——
 *         {@code GeckoMaidEntity} 的动画缓存按实体实例做 key，每次 init 都新建会让它一直停在第一帧。</li>
 * </ol>
 */
public class ModelSwitcherGui extends Screen {
    private static final ResourceLocation BG =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/model_switcher.png");
    private static final ResourceLocation DEFAULT_MODEL_ID =
            new ResourceLocation("touhou_little_maid:hakurei_reimu");

    /** 每页 6 行，与上游 {@code maxRow} 一致（列表区 6*19=114 高，正好放得下）。 */
    private static final int MAX_ROW = 6;

    private static final int IMAGE_WIDTH = 256;
    private static final int IMAGE_HEIGHT = 166;

    private final List<TileEntityModelSwitcher.ModeInfo> infoList;
    private final BlockPos pos;
    @Nullable
    private final UUID bindUuid;

    private int leftPos;
    private int topPos;
    private int selectedIndex = -1;
    private int page;

    /**
     * 预览用的假女仆。只在构造时建一次，界面内复用。
     */
    @Nullable
    private EntityMaid maid;

    @Nullable
    private EditBox description;

    public ModelSwitcherGui(TileEntityModelSwitcher switcher) {
        super(new TextComponent("Model Switcher GUI"));
        this.infoList = switcher.getInfoList();
        this.pos = switcher.getBlockPos();
        this.bindUuid = switcher.getUuid();
        // 上游同样在这里 new 一个：它只是立绘用的假实体，不进世界
        net.minecraft.world.level.Level level = Minecraft.getInstance().level;
        if (level != null) {
            // InitEntities.MAID 是 EntityType<EntityMaid>，create 直接给 EntityMaid；null 时退回构造器
            EntityMaid created = InitEntities.MAID.create(level);
            this.maid = created != null ? created : new EntityMaid(InitEntities.MAID, level);
        }
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.description = null;

        this.leftPos = (this.width - IMAGE_WIDTH) / 2;
        this.topPos = (this.height - IMAGE_HEIGHT) / 2;

        // 上/下界都要夹：删掉最后一行之后 selectedIndex 可能已经越界
        this.selectedIndex = this.selectedIndex < this.infoList.size() ? this.selectedIndex : -1;
        int pageCount = Math.max(1, (this.infoList.size() + MAX_ROW - 1) / MAX_ROW);
        this.page = this.page < pageCount ? this.page : 0;

        this.addListChangeButton();
        this.addPageButton();
        this.addListButton();
        if (this.selectedIndex >= 0) {
            this.addEditButton();
        }
    }

    // ------------------------------------------------------------------ 左侧编辑区

    private void addEditButton() {
        if (this.maid == null) {
            return;
        }
        TileEntityModelSwitcher.ModeInfo info = this.infoList.get(this.selectedIndex);
        if (info.getModelId() != null) {
            this.maid.setModelId(info.getModelId().toString());
        }

        // 「皮肤」：进入模型选择器
        this.addRenderableWidget(new Button(this.leftPos + 55, this.topPos + 15, 76, 20,
                new TranslatableComponent("gui.touhou_little_maid.button.skin"),
                b -> Minecraft.getInstance().setScreen(
                        new ModelSwitcherModelGui(this.maid, info, this))));

        // 朝向（四态循环）
        this.addRenderableWidget(new DirectButton(this.leftPos + 55, this.topPos + 38, 76, 20,
                info.getDirection(), b -> info.setDirection(((DirectButton) b).getDirection())));

        // 保存
        this.addRenderableWidget(new Button(this.leftPos + 12, this.topPos + 135, 121, 20,
                new TranslatableComponent("selectWorld.edit.save"),
                b -> SaveSwitcherDataPacket.send(this.pos, this.infoList)));

        // 头顶文字
        this.description = new EditBox(this.font, this.leftPos + 12, this.topPos + 65, 119, 20,
                new TranslatableComponent("gui.touhou_little_maid.name_tag.edit_box"));
        this.description.setValue(info.getText() == null ? "" : info.getText());
        this.addWidget(this.description);
        this.setInitialFocus(this.description);
    }

    // ------------------------------------------------------------------ 右侧列表

    private void addListButton() {
        int startOffsetY = this.topPos + 24;
        for (int i = this.page * MAX_ROW; i < Math.min(this.infoList.size(), (this.page + 1) * MAX_ROW); i++) {
            ImageButtonWithId button;
            if (i != this.selectedIndex) {
                // 未选中：普通底图（UV 0,166），点一下选中
                button = new ImageButtonWithId(i, this.leftPos + 141, startOffsetY,
                        108, 19, 0, 166, 19, BG, b -> {
                    this.selectedIndex = ((ImageButtonWithId) b).getIndex();
                    this.init();
                });
            } else {
                // 已选中：按下底图（UV 108,166），再点一下取消选中
                button = new ImageButtonWithId(i, this.leftPos + 141, startOffsetY,
                        108, 19, 108, 166, 0, BG, b -> {
                    this.selectedIndex = -1;
                    this.init();
                });
            }
            this.addRenderableWidget(button);
            startOffsetY += 19;
        }
    }

    private void addPageButton() {
        this.addRenderableWidget(new ImageButton(this.leftPos + 141, this.topPos + 7, 13, 16,
                0, 204, 16, BG, b -> {
            if (this.page > 0) {
                this.page = this.page - 1;
                this.init();
            }
        }));
        this.addRenderableWidget(new ImageButton(this.leftPos + 236, this.topPos + 7, 13, 16,
                13, 204, 16, BG, b -> {
            if ((this.page + 1) <= (this.infoList.size() - 1) / MAX_ROW) {
                this.page = this.page + 1;
                this.init();
            }
        }));
    }

    private void addListChangeButton() {
        this.addRenderableWidget(new Button(this.leftPos + 141, this.topPos + 139, 53, 20,
                new TranslatableComponent("gui.touhou_little_maid.model_switcher.list.add"),
                b -> {
                    this.infoList.add(new TileEntityModelSwitcher.ModeInfo(
                            DEFAULT_MODEL_ID, "", Direction.NORTH));
                    this.init();
                }));

        this.addRenderableWidget(new Button(this.leftPos + 196, this.topPos + 139, 53, 20,
                new TranslatableComponent("selectWorld.deleteButton"),
                b -> {
                    if (-1 < this.selectedIndex && this.selectedIndex < this.infoList.size()) {
                        this.infoList.remove(this.selectedIndex);
                        this.selectedIndex = -1;
                        this.init();
                    }
                }));
    }

    // ------------------------------------------------------------------ 渲染

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        if (this.maid == null) {
            return;
        }
        // 1.18.2 的 Screen#render 不代劳压暗世界，必须自己来
        this.renderBackground(poseStack);

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, BG);
        blit(poseStack, this.leftPos, this.topPos, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

        // 顶部：绑定的女仆 UUID（或「未绑定」提示）
        if (this.bindUuid != null) {
            drawCenteredString(poseStack, this.font, this.bindUuid.toString(),
                    this.leftPos + 128, this.topPos - 10, 0xFFFFFF);
        } else {
            drawCenteredString(poseStack, this.font,
                    new TranslatableComponent("gui.touhou_little_maid.model_switcher.uuid.empty"),
                    this.leftPos + 128, this.topPos - 10, 0xFFFFFF);
        }

        // 页数
        int pageCount = Math.max(1, (this.infoList.size() - 1) / MAX_ROW + 1);
        drawCenteredString(poseStack, this.font, String.format("%d/%d", this.page + 1, pageCount),
                this.leftPos + 193, this.topPos + 12, 0xFFFFFF);

        // 立绘与文字框只在「选中了某一行」时出现
        if (this.description != null) {
            InventoryScreen.renderEntityInInventory(this.leftPos + 30, this.topPos + 60, 25,
                    (float) this.leftPos + 30 - mouseX, (float) this.topPos + 60 - 40 - mouseY, this.maid);
            this.description.render(poseStack, mouseX, mouseY, partialTick);
        }

        super.render(poseStack, mouseX, mouseY, partialTick);
        this.renderListButtonName(poseStack);
    }

    /**
     * 列表每一行的模型名。
     *
     * <p>必须在 {@code super.render} 之后画 —— 按钮的底图会盖住先画的东西。</p>
     *
     * <p>取不到模型信息（模型包还没加载完 / 玩家写了一个不存在的 id）时那一行就只显示底图，
     * 不抛异常：上游同样有 {@code isPresent()} 判断。</p>
     */
    private void renderListButtonName(PoseStack poseStack) {
        int startOffsetY = this.topPos + 29;
        for (int i = this.page * MAX_ROW; i < Math.min(this.infoList.size(), (this.page + 1) * MAX_ROW); i++) {
            ResourceLocation modelId = this.infoList.get(i).getModelId();
            MaidModelInfo info = MaidModelRegistry.get(modelId);
            if (info != null) {
                MutableComponent component = ParseI18n.parse(info.getName());
                drawCenteredString(poseStack, this.font, component, this.leftPos + 193, startOffsetY, 0xFFFFFF);
            }
            startOffsetY += 19;
        }
    }

    // ------------------------------------------------------------------ 输入

    @Override
    public void tick() {
        if (this.description != null) {
            this.description.tick();
            if (0 <= this.selectedIndex && this.selectedIndex < this.infoList.size()) {
                this.infoList.get(this.selectedIndex).setText(this.description.getValue());
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.description != null && this.description.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.description);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 把打字内容转给文字框。
     *
     * <p>1.18.2 的 {@code EditBox} 通常自己就能收到 {@code keyPressed}，但它的
     * {@code charTyped} 只在「被聚焦」时才走 {@code Screen} 的默认派发；
     * 上游显式转发了一次，这里保持一致，避免聚焦状态因为重开 {@code init()}
     * 而在某些路径上丢掉后无法输入。</p>
     */
    @Override
    protected void insertText(String text, boolean overwrite) {
        if (this.description != null) {
            if (overwrite) {
                this.description.setValue(text);
            } else {
                this.description.insertText(text);
            }
        }
    }

    @Override
    public void onClose() {
        // 关界面时兜底保存一次：玩家改完直接按 ESC 是最常见的操作
        SaveSwitcherDataPacket.send(this.pos, this.infoList);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

