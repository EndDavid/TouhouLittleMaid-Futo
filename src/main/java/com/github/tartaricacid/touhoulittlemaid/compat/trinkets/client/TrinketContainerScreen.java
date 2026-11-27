package com.github.tartaricacid.touhoulittlemaid.compat.trinkets.client;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.screen.AbstractMaidScreen;
import com.github.tartaricacid.touhoulittlemaid.compat.trinkets.menu.MaidTrinketSlot;
import com.github.tartaricacid.touhoulittlemaid.compat.trinkets.menu.TrinketContainer;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.TabIndex;
import com.github.tartaricacid.touhoulittlemaid.client.screen.MaidClientPacketSender;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
/**
 * 女仆饰品界面（Trinkets 版），对应上游 {@code compat/curios/client/CuriosContainerScreen}。
 *
 * <p>底图 {@code maid_gui_curios.png} 与上游相同（取自 TLMO Fabric 1.20.1 的 jar）：
 * 护甲/主副手背景在 {@code (0,0) 57x58}，饰品格背景从 {@code (57,0)} 起按行拼。</p>
 *
 * <h2>与上游的两处差异</h2>
 * <ol>
 *     <li>上游这个界面带一个「切回背包」的按钮；本移植版的<b>入口</b>在主界面
 *         （见 {@code MaidMainScreen}），这里放一个「回到主界面」的按钮，
 *         复用同一张 {@code bauble_button.png}。</li>
 *     <li>上游给每个饰品格画槽位图标（Curios 走图集 sprite）。1.18.2 的 Fabric 没有把
 *         Trinkets 的槽位贴图并进图集，画出来只会是紫黑格，所以这里只画格子背景框，
 *         空格子靠<b>悬停提示</b>告诉你它放什么。这条缺口记在 {@code docs_dev/03}。</li>
 * </ol>
 */
public class TrinketContainerScreen extends AbstractMaidScreen<TrinketContainer> {
    private static final ResourceLocation CURIOS_BG =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/maid_gui_curios.png");
    /**
     * 饰品格网格的左下角与列数（与 {@code TrinketContainer} 的常量保持一致）。
     */
    private static final int GRID_X = 142;
    private static final int GRID_Y = 36;
    private static final int COLUMNS = 6;
    private TrinketBackButton backButton;
    public TrinketContainerScreen(TrinketContainer menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 256;
        this.imageHeight = 256;
    }
    /**
     * 这一页不属于顶部三个页签中的任何一个。
     */
    @Override
    protected int getCurrentTab() {
        return -1;
    }
    @Override
    protected void initAdditionWidgets() {
        this.backButton = new TrinketBackButton(this.leftPos + 85, this.topPos + 129, true,
                b -> {
                    if (this.maid != null) {
                        // 与页签切换同一条通道：客户端请服务端换菜单
                        MaidClientPacketSender.sendTab(TabIndex.MAIN, this.maid.getId());
                    }
                });
        this.addRenderableWidget(this.backButton);
    }
    @Override
    protected void renderAddition(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        RenderSystem.setShaderTexture(0, CURIOS_BG);
        // 护甲 / 主副手背景
        this.blit(poseStack, this.leftPos + 85, this.topPos + 36, 0, 0, 57, 58);
        int count = this.menu.getTrinketSlotCount();
        if (count > 0) {
            // 先画满行，再画最后不满的一行 —— 与上游同样的两段式（贴图右侧那块是连续的行）
            int rows = (count - 1) / COLUMNS;
            int width = (count - 1) % COLUMNS * 18 + 18;
            if (rows > 0) {
                int height = rows * 18;
                this.blit(poseStack, this.leftPos + GRID_X, this.topPos + GRID_Y, 57, 0, 108, height);
                this.blit(poseStack, this.leftPos + GRID_X, this.topPos + GRID_Y + height,
                        57, height, width, 18);
            } else {
                this.blit(poseStack, this.leftPos + GRID_X, this.topPos + GRID_Y, 57, 0, width, 18);
            }
        }
        MutableComponent label = new TranslatableComponent("gui.touhou_little_maid.trinkets");
        int labelWidth = this.font.width(label);
        this.font.draw(poseStack, label, this.leftPos + 197 - labelWidth / 2,
                this.topPos + 150, 0x555555);
    }
    /**
     * 空格子的悬停提示：告诉你这一格放什么的（戒指 / 项链 / 手套 …）。
     *
     * <p>上游在 {@code renderAdditionTransTooltip} 里做同样的事。本移植版的
     * {@code AbstractMaidScreen#renderButtonTooltips} 是 private、覆写不了，
     * 所以在 {@code super.render(...)} 之后补一次 —— 效果相同（都画在最上层）。</p>
     */
    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        super.render(poseStack, mouseX, mouseY, partialTick);
        if (this.backButton != null && this.backButton.isHovered(mouseX, mouseY)) {
            this.renderComponentTooltip(poseStack, this.backButton.getTooltip(), mouseX, mouseY);
            return;
        }
        Slot hovered = this.hoveredSlot;
        if (hovered instanceof MaidTrinketSlot trinket && !hovered.hasItem()) {
            this.renderComponentTooltip(poseStack, java.util.List.of(trinket.getSlotName()), mouseX, mouseY);
        }
    }
}
