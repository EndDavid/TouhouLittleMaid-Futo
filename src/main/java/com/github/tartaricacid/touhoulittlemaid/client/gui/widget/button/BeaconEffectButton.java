package com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.gui.block.MaidBeaconClientPacketSender;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityMaidBeacon;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

/**
 * 神社庭灯 GUI 上的「效果」按钮，移植自上游 {@code client/gui/widget/button/BeaconEffectButton}。
 *
 * <p>是个 {@link StateSwitchingButton}（原版信标界面那种带按下状态的按钮），
 * 图标直接从<b>状态效果图集</b>里取（{@code Minecraft#getMobEffectTextures()}），
 * 所以不需要为五种效果各画一张贴图。</p>
 *
 * <p>点击只是「切换本地状态 + 通知服务端」，界面重建由回调负责 ——
 * 与上游一致（上游在回调里调 {@code MaidBeaconGui#init()} 重建按钮）。</p>
 */
public class BeaconEffectButton extends StateSwitchingButton {
    /**
     * 按钮底图与 GUI 是同一张：{@code textures/gui/maid_beacon.png}，
     * 未按下取 (0,111)，按下取 (22,111)，尺寸 22×22。
     */
    private static final ResourceLocation BG =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/maid_beacon.png");

    private final TextureAtlasSprite sprite;
    private final Component tooltips;
    private final int potionIndex;
    private final BlockPos pos;
    private final Consumer<Boolean> onClick;

    public BeaconEffectButton(TileEntityMaidBeacon.BeaconEffect effect, int xIn, int yIn, int potionIndex,
                              TileEntityMaidBeacon beacon, Consumer<Boolean> onClick) {
        super(xIn, yIn, 22, 22, potionIndex == effect.ordinal());
        this.initTextureValues(0, 111, 22, 22, BG);
        this.sprite = Minecraft.getInstance().getMobEffectTextures().get(effect.getEffect());
        this.tooltips = effect.getEffect().getDisplayName();
        this.potionIndex = effect.ordinal();
        this.pos = beacon.getBlockPos();
        this.onClick = onClick;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.isStateTriggered = !this.isStateTriggered;
        MaidBeaconClientPacketSender.sendPotion(this.pos, this.isStateTriggered ? this.potionIndex : -1);
        this.onClick.accept(this.isStateTriggered);
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        super.renderButton(poseStack, mouseX, mouseY, partialTicks);
        // 效果图标在独立图集里，所以要单独切 shader + 贴图
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, this.sprite.atlas().location());
        blit(poseStack, this.x + 2, this.y + 2, this.getBlitOffset(), 18, 18, this.sprite);
    }

    public void renderToolTip(PoseStack poseStack, Screen screen, int pMouseX, int pMouseY) {
        if (this.isHovered) {
            screen.renderTooltip(poseStack, this.tooltips, pMouseX, pMouseY);
        }
    }
}
