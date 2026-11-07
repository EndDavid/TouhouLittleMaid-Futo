package com.github.tartaricacid.touhoulittlemaid.client.screen;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * 命名牌改名界面，移植自上游 {@code client/gui/item/NameTagGui}。
 *
 * <p>布局与上游逐像素一致：输入框居中偏左（176 宽），下面是「完成 / 取消」按钮，
 * 输入框右侧那个小按钮切换「名字是否常显」—— 图标直接取自原版信标的 GUI
 * （{@code textures/gui/container/beacon.png} 右下角那对勾 / 叉），
 * 因此完全不需要额外的贴图资源。</p>
 *
 * <p><b>为什么只用一座 {@link Screen} 而不是容器界面：</b>改名不涉及任何物品槽位，
 * 打开一个 {@code AbstractContainerMenu} 只会多出一堆同步代码。上游也是纯 {@code Screen}。</p>
 *
 * <p><b>为什么提交走网络包：</b>{@code setCustomName} 不是同步数据，客户端改了会被
 * 服务端下一帧的实体同步覆盖；而且改名要消耗命名牌。所以这里只发意图，
 * 由 {@code MaidConfigPacket#handleNameTag} 在服务端复核并写入。</p>
 */
public class NameTagScreen extends Screen {
    /**
     * 原版信标的 GUI 贴图，上游用的也是这一张（勾 / 叉图标在 y=220 那一行）。
     */
    private static final ResourceLocation TEXTURES =
            new ResourceLocation("textures/gui/container/beacon.png");

    private final EntityMaid maid;
    private EditBox textField;
    private Button alwaysShowButton;
    /**
     * 名字是否常显。默认关闭：原版命名牌改完名后名字只在准星对准时显示，
     * 这里保持同样的默认值。
     */
    private boolean alwaysShow = false;

    public NameTagScreen(EntityMaid maid) {
        super(TextComponent.EMPTY);
        this.maid = maid;
    }

    @Override
    protected void init() {
        // 让输入框能收到按住不放的重复字符，否则长按删除键只删一个字符
        this.minecraft.keyboardHandler.setSendRepeatsToGui(true);
        this.clearWidgets();

        int middleX = this.width / 2;
        int middleY = this.height / 2;

        this.textField = new EditBox(this.font, middleX - 99, middleY - 26, 176, 20,
                new TranslatableComponent("gui.touhou_little_maid.name_tag.edit_box"));
        this.textField.setMaxLength(32);
        this.addWidget(this.textField);
        this.setInitialFocus(this.textField);

        this.addRenderableWidget(new Button(middleX - 100, middleY, 98, 20,
                new TranslatableComponent("gui.done"), button -> this.sendDoneMessage()));
        this.addRenderableWidget(new Button(middleX + 2, middleY, 98, 20,
                new TranslatableComponent("gui.cancel"), button -> this.onClose()));

        this.alwaysShowButton = new Button(middleX + 80, middleY - 26, 20, 20,
                TextComponent.EMPTY, button -> this.alwaysShow = !this.alwaysShow);
        this.addRenderableWidget(this.alwaysShowButton);
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String value = this.textField == null ? "" : this.textField.getValue();
        super.resize(minecraft, width, height);
        if (this.textField != null) {
            this.textField.setValue(value);
        }
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        int middleX = this.width / 2;
        int middleY = this.height / 2;
        this.renderBackground(poseStack);

        if (this.textField != null) {
            this.textField.render(poseStack, mouseX, mouseY, partialTicks);
        }
        super.render(poseStack, mouseX, mouseY, partialTicks);

        // 勾 / 叉图标画在按钮之上：alwaysShow ? 勾(88) : 叉(110)
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TEXTURES);
        blit(poseStack, middleX + 80, middleY - 26, this.alwaysShow ? 88 : 110, 220, 20, 20);

        if (this.alwaysShowButton != null && this.alwaysShowButton.isHoveredOrFocused()) {
            this.renderTooltip(poseStack,
                    new TranslatableComponent("gui.touhou_little_maid.tag.always_show"), mouseX, mouseY);
        }
    }

    @Override
    public void tick() {
        if (this.textField != null) {
            this.textField.tick();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.textField != null && this.textField.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.textField);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void insertText(String text, boolean overwrite) {
        if (this.textField == null) {
            return;
        }
        if (overwrite) {
            this.textField.setValue(text);
        } else {
            this.textField.insertText(text);
        }
    }

    /**
     * 「完成」：只发名字，不改本地实体。
     *
     * <p>空白名字直接关闭界面而不发包 —— 服务端也会丢弃空名字，
     * 但让玩家以为「改成了空名字」更糟，所以这里干脆什么都不发。</p>
     */
    private void sendDoneMessage() {
        String value = this.textField == null ? "" : this.textField.getValue().trim();
        if (!value.isEmpty()) {
            MaidClientPacketSender.sendNameTag(value, this.alwaysShow, this.maid.getId());
        }
        this.onClose();
    }

    @Override
    public void onClose() {
        // 关界面时把「按住重复字符」还回去，否则回到游戏后按键会一直重复
        this.minecraft.keyboardHandler.setSendRepeatsToGui(false);
        super.onClose();
    }

    /**
     * 不显示背景模糊：改名界面要能看清女仆，糊一片反而不好定位。
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public Component getNarrationMessage() {
        return new TranslatableComponent("gui.touhou_little_maid.name_tag.edit_box");
    }
}

