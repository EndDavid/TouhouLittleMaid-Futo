package com.github.tartaricacid.touhoulittlemaid.client.screen;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.TabIndex;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.MaidMainContainer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 主界面页签（物品栏 / 护甲 / 双手 / 背包）。
 *
 * <p>它只比基类多两件事：页签下标是 {@link TabIndex#MAIN}、多画一块背包区域。
 * 其余（立绘 / 状态条 / 页签 / 任务列表 / 玩家物品栏）全部在
 * {@link AbstractMaidScreen} 里，三个页签共用。</p>
 */
public class MaidMainScreen extends AbstractMaidScreen<MaidMainContainer> {
    private static final ResourceLocation BACKPACK =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/maid_gui_backpack.png");

    /**
     * 未解锁背包行的遮罩色（上游 {@code 0xaa222222}）。
     */
    private static final int LOCKED_MASK = 0xAA222222;

    public MaidMainScreen(MaidMainContainer menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    /**
     * 界面左上角的绝对 X 坐标（{@code leftPos} 是 {@code AbstractContainerScreen} 的 protected 字段）。
     *
     * <p>给 REI 的「排除区」用：REI 要在容器界面旁边画物品面板，需要知道本界面占了多大的屏幕区域，
     * 才不会把自己的面板压在我们的立绘 / 槽位上。1.18.2 的原版没有 {@code getGuiLeft()} /
     * {@code getGuiTop()}（那两个是 1.19.3 才加的），所以只能在这里开个口子。</p>
     */
    public int futo$guiLeft() {
        return this.leftPos;
    }

    /** 界面左上角的绝对 Y 坐标。见 {@link #futo$guiLeft()}。 */
    public int futo$guiTop() {
        return this.topPos;
    }

    @Override
    protected int getCurrentTab() {
        return TabIndex.MAIN;
    }

    /**
     * 背包区域：底图 + 未解锁行遮罩 + 锁图标（上游 {@code EmptyBackpackContainerScreen}）。
     *
     * <p>上游把「未解锁的 3 组行」硬编码成三段 fill + 三个锁；这里按行处理，
     * 换任何背包都能正确显示 —— 遮罩与锁的 y 直接取自槽位行的真实纵坐标。</p>
     */
    @Override
    protected void renderAddition(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, BACKPACK);
        this.blit(poseStack, this.leftPos + 85, this.topPos + 36, 0, 0, 165, 128);

        int unlocked = this.menu.getBackpackRows();
        int[] rowY = MaidMainContainer.getRowY();
        for (int row = unlocked; row < rowY.length; row++) {
            int top = this.topPos + rowY[row] - 1;
            // 遮罩盖住整行 6 格
            fill(poseStack, this.leftPos + 142, top, this.leftPos + 250, top + 18, LOCKED_MASK);
            // 锁图标居中偏左（上游放在 190）
            this.blit(poseStack, this.leftPos + 190, this.topPos + rowY[row] + 4, 165, 0, 11, 11);
        }
    }
}

