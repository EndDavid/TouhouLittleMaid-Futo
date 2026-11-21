package com.github.tartaricacid.touhoulittlemaid.client.gui.item;

import com.github.tartaricacid.touhoulittlemaid.inventory.container.other.PicnicBasketContainer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 野餐篮的界面，移植自上游 {@code client/gui/item/PicnicBasketContainerScreen}。
 *
 * <p>没有任何专属贴图 —— 它借的是原版双层箱子底图
 * {@code textures/gui/container/generic_54.png}，但只取「上半截」：
 * 前 35 像素画 3 行（标题 + 9 个篮子格），从第 126 像素再取 96 高画玩家物品栏，
 * 拼成一个 176×132 的界面（原版箱子是 176×168/222）。</p>
 *
 * <p><b>1.18.2 适配：</b>上游 1.20 的 {@code AbstractContainerScreen#render} 会自己调
 * {@code renderBackground} 压暗世界；<b>1.18.2 不会</b>（那是 1.19 才改的），
 * 所以这里必须在 {@code render} 开头补一次，否则界面直接叠在亮着的世界上。
 * 同一个坑在 {@code WirelessIOContainerGui} 里也踩过一次。</p>
 */
public class PicnicBasketContainerScreen extends AbstractContainerScreen<PicnicBasketContainer> {
    private static final ResourceLocation CONTAINER_BACKGROUND =
            new ResourceLocation("textures/gui/container/generic_54.png");

    public PicnicBasketContainerScreen(PicnicBasketContainer container, Inventory inv, Component titleIn) {
        super(container, inv, titleIn);
        this.imageHeight = 132;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        // 世界压暗层：1.18.2 的 AbstractContainerScreen 不会自己调，必须在这里补
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        this.renderTooltip(poseStack, mouseX, mouseY);
    }

    @Override
    protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
        int middleX = (this.width - this.imageWidth) / 2;
        int middleY = (this.height - this.imageHeight) / 2;
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, CONTAINER_BACKGROUND);
        // 上半截：标题条 + 3 行格子（原图 y=0..34）
        this.blit(poseStack, middleX, middleY, 0, 0, this.imageWidth, 35);
        // 下半截：玩家物品栏那一段（原图 y=126..221，取 96 高正好接上）
        this.blit(poseStack, middleX, middleY + 35, 0, 126, this.imageWidth, 96);
    }
}

