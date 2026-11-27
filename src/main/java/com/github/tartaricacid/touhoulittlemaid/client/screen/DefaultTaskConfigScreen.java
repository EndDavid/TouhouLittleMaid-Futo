package com.github.tartaricacid.touhoulittlemaid.client.screen;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.task.DefaultTaskConfigContainer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.anti_ad.mc.ipn.api.IPNButton;
import org.anti_ad.mc.ipn.api.IPNGuiHint;
import org.anti_ad.mc.ipn.api.IPNPlayerSideOnly;

import java.util.List;

/**
 * 「当前工作任务没有任何配置项」页，移植自上游 {@code DefaultMaidTaskConfigGui}。
 *
 * <p>底图 {@code default_task_config.png} 画在 {@code leftPos+80, topPos+28}，
 * 高度只取 137（上半张是提示文字与示意图，下半张是玩家物品栏的位置）。</p>
 *
 * <p>IPN 的五个 {@code @IPNGuiHint} 与 {@code @IPNPlayerSideOnly} 与上游
 * {@code DefaultMaidTaskConfigGui} 逐字相同（说明见 {@code MaidConfigScreen} 的类注释）。</p>
 */
@IPNPlayerSideOnly
@IPNGuiHint(button = IPNButton.SORT, horizontalOffset = -36, bottom = -12)
@IPNGuiHint(button = IPNButton.SORT_COLUMNS, horizontalOffset = -24, bottom = -24)
@IPNGuiHint(button = IPNButton.SORT_ROWS, horizontalOffset = -12, bottom = -36)
@IPNGuiHint(button = IPNButton.SHOW_EDITOR, horizontalOffset = -5)
@IPNGuiHint(button = IPNButton.SETTINGS, horizontalOffset = -5)
public class DefaultTaskConfigScreen extends MaidTaskConfigScreen<DefaultTaskConfigContainer> {
    private static final ResourceLocation BG =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/default_task_config.png");
    /**
     * 这张图只用上半部分，下半部分留给玩家物品栏。
     */
    private static final int BG_HEIGHT = 137;

    public DefaultTaskConfigScreen(DefaultTaskConfigContainer menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void renderAddition(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, BG);
        this.blit(poseStack, this.leftPos + 80, this.topPos + 28, 0, 0, this.imageWidth, BG_HEIGHT);

        // 提示文字，按 160 像素宽自动折行（上游同样处理）
        List<FormattedCharSequence> split = this.font.split(
                new TranslatableComponent("gui.touhou_little_maid.default_task_config.title"), 160);
        int yOffset = this.topPos + 38;
        for (FormattedCharSequence line : split) {
            this.font.draw(poseStack, line, this.leftPos + 88, yOffset, ChatFormatting.DARK_GRAY.getColor());
            yOffset += this.font.lineHeight;
        }
    }
}
