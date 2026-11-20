package com.github.tartaricacid.touhoulittlemaid.client.overlay;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * 「你一个人骑在扫帚上飞不起来」的提示，移植自上游 {@code client/overlay/BroomTipsOverlay}。
 *
 * <p>条件是：<b>玩家正骑着一把扫帚，而扫帚上<b>没有</b>女仆</b>。
 * 这时在屏幕中央偏上画一个图标 + 折行后的提示。</p>
 *
 * <p><b>与上游的差异（两处，都是框架差异）：</b></p>
 * <ol>
 *     <li>上游订阅 Forge 的 {@code RenderGameOverlayEvent.Text}，并且用一个
 *         {@code minecraft.gui instanceof ForgeIngameGui} 的判断过滤掉别的 GUI
 *         实现（Forge 允许替换 HUD）。Fabric 用的是
 *         {@code HudRenderCallback.EVENT}，没有「GUI 实现」这个概念，那个判断整个不需要。</li>
 *     <li>上游从事件里取 {@code MatrixStack}（{@code event.getMatrixStack()}），
 *         Fabric 的回调直接给 {@code PoseStack} —— 1.18.2 里两者是同一个东西。</li>
 * </ol>
 *
 * <p>排版与上游一致：图标在「屏幕中心上方 5 像素再按行数上抬」处，正文每行 10 像素。</p>
 */
public final class BroomTipsOverlay {
    private static final ResourceLocation BG =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/download_background.png");
    private static final int MAX_TEXT_WIDTH = 150;

    private BroomTipsOverlay() {
    }

    public static void init() {
        HudRenderCallback.EVENT.register((poseStack, tickDelta) -> render(poseStack));
    }

    private static void render(PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof EntityBroom broom) || broom.hasPassenger(e -> e instanceof EntityMaid)) {
            return;
        }

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        Component tip = new TranslatableComponent("message.touhou_little_maid.broom.unable_fly");
        List<FormattedCharSequence> split = minecraft.font.split(tip, MAX_TEXT_WIDTH);
        int offset = (screenHeight / 2 - 5) - split.size() * 10;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, BG);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        GuiComponent.blit(poseStack, screenWidth / 2 - 8, offset - 2, 48, 16, 16, 16, 256, 256);
        RenderSystem.disableBlend();

        offset += 18;
        for (FormattedCharSequence sequence : split) {
            int width = minecraft.font.width(sequence);
            minecraft.font.drawShadow(poseStack, sequence, (screenWidth - width) / 2f, offset, 0xFFFFFF);
            offset += 10;
        }
    }
}

