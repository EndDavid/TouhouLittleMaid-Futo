package com.github.tartaricacid.touhoulittlemaid.client.tooltip;

import com.github.tartaricacid.touhoulittlemaid.inventory.tooltip.ItemContainerTooltip;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;

/**
 * 把 {@link ItemContainerTooltip} 画成一排物品图标，移植自上游
 * {@code client/tooltip/ClientItemContainerTooltip}。
 *
 * <p>1.18.2 与 1.20 的差别只有 {@code renderImage} 的签名：</p>
 * <ul>
 *     <li>1.18.2：{@code (Font, int x, int y, PoseStack, ItemRenderer, int blitOffset)}；</li>
 *     <li>1.20：{@code (Font, int x, int y, GuiGraphics)}。</li>
 * </ul>
 * <p>所以这里要用 {@code poseStack} 自己压栈 + {@code font.drawShadow}，
 * 而不是 1.20 那种 {@code guiGraphics.drawString}。</p>
 *
 * <h2>为什么构造时就把空格子滤掉</h2>
 * <p>{@code getWidth} / {@code getHeight} / {@code renderImage} 会各自被调用，
 * 每帧都遍历一遍 9 格没有必要；而且宽度必须是「非空格数 × 20」，
 * 否则空着 3 格时 tooltip 会多出一段空白。</p>
 */
public class ClientItemContainerTooltip implements ClientTooltipComponent {
    private static final int SLOT_SIZE = 20;

    private final NonNullList<ItemStack> items = NonNullList.create();
    /**
     * 全空时显示的那一句「空」。为 {@code null} 表示有东西要画。
     */
    @Nullable
    private final MutableComponent emptyTip;

    public ClientItemContainerTooltip(ItemContainerTooltip containerTooltip) {
        IItemHandler handler = containerTooltip.handler();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                this.items.add(stack);
            }
        }
        this.emptyTip = this.items.isEmpty()
                ? new TranslatableComponent("tooltips.touhou_little_maid.item_container.empty")
                : null;
    }

    @Override
    public int getHeight() {
        // 有物品时按一行 20 像素（图标 16 + 两枚角标），空的时候就是一行字
        return this.emptyTip != null ? 10 : SLOT_SIZE;
    }

    @Override
    public int getWidth(Font font) {
        return this.emptyTip != null ? font.width(this.emptyTip) : this.items.size() * SLOT_SIZE;
    }

    @Override
    public void renderImage(Font font, int pX, int pY, PoseStack poseStack, ItemRenderer itemRenderer, int blitOffset) {
        if (this.emptyTip != null) {
            poseStack.pushPose();
            poseStack.translate(0, 0, blitOffset);
            font.drawShadow(poseStack, this.emptyTip, pX, pY, ChatFormatting.GRAY.getColor());
            poseStack.popPose();
            return;
        }
        int i = 0;
        for (ItemStack stack : this.items) {
            int xOffset = pX + i * SLOT_SIZE;
            itemRenderer.renderGuiItem(stack, xOffset, pY);
            itemRenderer.renderGuiItemDecorations(font, stack, xOffset, pY);
            i++;
        }
    }
}

