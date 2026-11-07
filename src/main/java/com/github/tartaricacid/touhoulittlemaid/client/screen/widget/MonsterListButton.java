package com.github.tartaricacid.touhoulittlemaid.client.screen.widget;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.screen.AttackTaskConfigScreen;
import com.github.tartaricacid.touhoulittlemaid.entity.misc.MonsterType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 攻击名单里的一行，移植自上游 {@code client/gui/widget/button/MonsterListButton}。
 *
 * <p>164x13，贴图 {@code attack_task_config.png} 里的三段底图：</p>
 * <ul>
 *     <li>{@code (0,137)}：正常；</li>
 *     <li>{@code (0,150)}：鼠标停在左右箭头上；</li>
 *     <li>{@code (0,163)}：鼠标停在删除区（{@code x+107 .. x+120}）。</li>
 * </ul>
 *
 * <p>三个热区互不重叠，与配置行按钮的写法一致：删除 / 上一个 / 下一个。</p>
 */
public class MonsterListButton extends Button {
    private static final ResourceLocation ICON =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/attack_task_config.png");

    private final AttackTaskConfigScreen parents;
    private final ResourceLocation entityId;

    public MonsterListButton(Component entityName, int x, int y, ResourceLocation entityId,
                             AttackTaskConfigScreen parents) {
        super(x, y, 164, 13, entityName, b -> {
        });
        this.parents = parents;
        this.entityId = entityId;
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, ICON);
        RenderSystem.enableDepthTest();
        if (this.deleteClick(mouseX, mouseY)) {
            blit(poseStack, this.x, this.y, 0, 163, this.width, this.height, 256, 256);
        } else if (this.leftClick(mouseX, mouseY) || this.rightClick(mouseX, mouseY)) {
            blit(poseStack, this.x, this.y, 0, 150, this.width, this.height, 256, 256);
        } else {
            blit(poseStack, this.x, this.y, 0, 137, this.width, this.height, 256, 256);
        }
        minecraft.font.draw(poseStack, this.getMessage(), this.x + 5, this.y + 3, 0x444444);
        MonsterType type = this.parents.getAttackList().attackGroups().get(this.entityId);
        if (type != null) {
            drawCenteredString(poseStack, minecraft.font, type.getComponent(),
                    this.x + 142, this.y + 3, 0xFFFFFF);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (this.deleteClick(mouseX, mouseY)) {
            this.parents.removeMonsterType(this.entityId);
        } else if (this.leftClick(mouseX, mouseY)) {
            this.parents.getAttackList().attackGroups()
                    .computeIfPresent(this.entityId, (k, type) -> type.getPrevious());
        } else if (this.rightClick(mouseX, mouseY)) {
            this.parents.getAttackList().attackGroups()
                    .computeIfPresent(this.entityId, (k, type) -> type.getNext());
        }
    }

    private boolean deleteClick(double mouseX, double mouseY) {
        boolean clickY = this.y <= mouseY && mouseY <= (this.y + this.getHeight());
        boolean zoneX = (this.x + 107) <= mouseX && mouseX <= (this.x + 120);
        return clickY && zoneX;
    }

    private boolean leftClick(double mouseX, double mouseY) {
        boolean clickY = this.y <= mouseY && mouseY <= (this.y + this.getHeight());
        boolean zoneX = (this.x + 120) <= mouseX && mouseX <= (this.x + 130);
        return clickY && zoneX;
    }

    private boolean rightClick(double mouseX, double mouseY) {
        boolean clickY = this.y <= mouseY && mouseY <= (this.y + this.getHeight());
        boolean zoneX = (this.x + 154) <= mouseX && mouseX <= (this.x + 164);
        return clickY && zoneX;
    }
}

