package com.github.tartaricacid.touhoulittlemaid.client.gui.item;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.WirelessIOButton;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.other.WirelessIOContainer;
import com.github.tartaricacid.touhoulittlemaid.item.ItemWirelessIO;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 隙间的主界面（9 格过滤清单 + 玩家物品栏 + 三个按钮），移植自上游
 * {@code client/gui/item/WirelessIOContainerGui}。
 *
 * <p>布局取自 {@code textures/gui/wireless_io.png}（256×256 的图，界面区 176×166）：</p>
 * <ul>
 *     <li>左侧 {@code (23,34)} 18×18：I/O 方向开关，<b>按下 = 女仆 → 箱子</b>；</li>
 *     <li>右上 {@code (136,26)} 16×16：黑/白名单开关，<b>按下 = 黑名单</b>；</li>
 *     <li>右侧 {@code (136,44)} 16×16：打开「槽位配置」界面；</li>
 *     <li>中间 {@code (61,15)} 54×55：黑名单时叠一张红色遮罩，
 *         直观地告诉玩家「这里的清单是排除名单」。</li>
 * </ul>
 *
 * <p><b>状态从哪来：</b>构造时从 {@code menu.getWirelessIO()} 读一次 ——
 * 那是服务端在打开菜单时下发的拷贝。点击开关后本地状态立刻改，
 * 同时发包让服务端改 NBT；不立刻改本地状态的话，
 * 按钮会在「点下 → 等服务端」之间闪一下（上游也是先改本地再发包）。</p>
 *
 * <p><b>1.18.2 适配：</b>上游 {@code renderBg} 里先调 {@code renderBackground} 再画底图，
 * 而 1.18.2 的 {@code AbstractContainerScreen#render} 已经调过一次 —— 会重复画一遍模糊底。
 * 这里省掉那一句。</p>
 */
public class WirelessIOContainerGui extends AbstractContainerScreen<WirelessIOContainer> {
    private static final ResourceLocation MAIN =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/wireless_io.png");

    private boolean isMaidToChest;
    private boolean isBlacklist;

    public WirelessIOContainerGui(WirelessIOContainer container, Inventory inv, Component titleIn) {
        super(container, inv, titleIn);
        this.isMaidToChest = ItemWirelessIO.isMaidToChest(container.getWirelessIO());
        this.isBlacklist = ItemWirelessIO.isBlacklist(container.getWirelessIO());
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        WirelessIOButton ioModeToggle = new WirelessIOButton(this.leftPos + 23, this.topPos + 34, 18, 18,
                this.isMaidToChest,
                (x, y) -> {
                    this.isMaidToChest = !this.isMaidToChest;
                    WirelessIOClientPacketSender.sendGuiMode(this.isMaidToChest, this.isBlacklist);
                },
                (poseStack, x, y) -> this.renderTooltip(poseStack,
                        new TranslatableComponent("gui.touhou_little_maid.wireless_io.io_mode"), x, y));
        ioModeToggle.initTextureValues(194, 32, -18, 18, MAIN);

        WirelessIOButton filterModeToggle = new WirelessIOButton(this.leftPos + 136, this.topPos + 26, 16, 16,
                this.isBlacklist,
                (x, y) -> {
                    this.isBlacklist = !this.isBlacklist;
                    WirelessIOClientPacketSender.sendGuiMode(this.isMaidToChest, this.isBlacklist);
                },
                (poseStack, x, y) -> this.renderTooltip(poseStack,
                        new TranslatableComponent("gui.touhou_little_maid.wireless_io.filter_mode"), x, y));
        filterModeToggle.initTextureValues(176, 0, 16, 16, MAIN);

        // 槽位配置是独立的 Screen：它要画 38 个方格，塞不进这个 176×166 的界面
        ImageButton configButton = new ImageButton(this.leftPos + 136, this.topPos + 44, 16, 16,
                208, 0, 16, MAIN, 256, 256,
                buttons -> this.minecraft.setScreen(
                        new WirelessIOConfigSlotGui(this.menu.getWirelessIO())),
                (button, poseStack, x, y) -> this.renderTooltip(poseStack,
                        new TranslatableComponent("gui.touhou_little_maid.wireless_io.config_slot"), x, y),
                TextComponent.EMPTY);

        this.addRenderableWidget(filterModeToggle);
        this.addRenderableWidget(configButton);
        this.addRenderableWidget(ioModeToggle);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        super.render(poseStack, mouseX, mouseY, partialTicks);
        this.renderTooltip(poseStack, mouseX, mouseY);
    }

    @Override
    protected void renderBg(PoseStack poseStack, float partialTicks, int x, int y) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, MAIN);
        this.blit(poseStack, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        if (this.isBlacklist) {
            // 黑名单标记：盖住中间那 3×3 的区域
            this.blit(poseStack, this.leftPos + 61, this.topPos + 15, 0, 166, 54, 55);
        }
    }

    /**
     * 界面不画标题：底图里已经印了名字，再画一行会重叠（上游同样留空）。
     */
    @Override
    protected void renderLabels(PoseStack poseStack, int x, int y) {
    }
}
