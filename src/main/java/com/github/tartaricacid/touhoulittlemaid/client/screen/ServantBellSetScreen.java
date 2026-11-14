package com.github.tartaricacid.touhoulittlemaid.client.screen;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.ServantBellSetPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.TranslatableComponent;

/**
 * 用仆从铃右击女仆后弹出的「提示名」输入框（上游
 * {@code client/gui/item/ServantBellSetScreen}）。
 *
 * <p>提示名会直接变成这枚铃的物品名字（金色下划线），所以它就是
 * 玩家给「这条铃唤哪只女仆」起的备注。</p>
 *
 * <h2>为什么这里不持有 {@code EntityMaid} 的引用</h2>
 * <p>上游在构造函数里把 {@code maid.getId()} 与 {@code maid.getUUID()} 取出来存成
 * {@code int} / {@code UUID}，之后只用这两个值。这里照做：界面打开期间那只女仆
 * 完全可能被卸载、被其他玩家带走，缓存实体引用会拿到一个
 * {@code isRemoved()} 的对象。</p>
 *
 * <h2>为什么手写 {@code ClientPlayNetworking}</h2>
 * <p>本移植版把客户端发包集中在 {@code MaidClientPacketSender} 里，是为了让
 * common 侧的网络包类不引用客户端类型。这个界面本身就是客户端类，
 * 直接发即可，不必再绕一层。</p>
 */
@Environment(EnvType.CLIENT)
public class ServantBellSetScreen extends Screen {
    /**
     * 输入框宽度。上游 200，按钮两枚各 98 加 6 格间距正好对上。
     */
    private static final int FIELD_WIDTH = 200;
    private static final int BUTTON_WIDTH = 98;

    private final int maidId;
    private final java.util.UUID maidUuid;
    private EditBox textField;

    private ServantBellSetScreen(EntityMaid maid) {
        super(new TranslatableComponent("item.touhou_little_maid.servant_bell"));
        this.maidId = maid.getId();
        this.maidUuid = maid.getUUID();
    }

    public static void open(EntityMaid maid) {
        Minecraft.getInstance().setScreen(new ServantBellSetScreen(maid));
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int middleX = this.width / 2;
        int middleY = this.height / 2;
        this.textField = new EditBox(this.font, middleX - 99, middleY - 26, FIELD_WIDTH, 20,
                new TranslatableComponent("gui.touhou_little_maid.servant_bell.edit_box"));
        this.addWidget(this.textField);
        this.setInitialFocus(this.textField);
        this.addRenderableWidget(new Button(middleX - 100, middleY + 10, BUTTON_WIDTH, 20,
                new TranslatableComponent("gui.done"), this::sendDoneMessage));
        this.addRenderableWidget(new Button(middleX + 4, middleY + 10, BUTTON_WIDTH, 20,
                new TranslatableComponent("gui.cancel"), b -> this.onClose()));
    }

    /**
     * 窗口尺寸变化时 {@code init()} 会重建控件，输入框里的内容要自己搬过去。
     */
    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String value = this.textField == null ? "" : this.textField.getValue();
        super.resize(minecraft, width, height);
        this.textField.setValue(value);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        int middleX = this.width / 2;
        int middleY = this.height / 2;
        this.renderBackground(poseStack);
        if (this.textField.getValue().isEmpty()) {
            // 占位提示：EditBox 自己没有 placeholder，只能手动画一层灰字
            this.font.draw(poseStack,
                    new TranslatableComponent("gui.touhou_little_maid.servant_bell.edit_box")
                            .withStyle(ChatFormatting.ITALIC),
                    middleX - 94, middleY - 20, ChatFormatting.DARK_GRAY.getColor());
        }
        this.textField.render(poseStack, mouseX, mouseY, partialTicks);
        // 把 UUID 显示在输入框上方：多枚铃同时存在时，这是唯一能分清的办法。
        // 1.18.2 的 drawCenteredString 在 GuiComponent 上，且要显式传 Font
        // （1.19 才把它挪进 Screen 并去掉 Font 参数）。
        drawCenteredString(poseStack, this.font,
                new TranslatableComponent("tooltips.touhou_little_maid.servant_bell.uuid",
                        this.maidUuid.toString()),
                middleX, middleY - 50, 0xFFFFFF);
        // super.render 放在最后：它会画所有 addRenderableWidget 加进去的按钮，
        // 顺序在 EditBox 之后正好让按钮压在背景上而不是被输入框盖住
        super.render(poseStack, mouseX, mouseY, partialTicks);
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

    /**
     * 输入的中文 / 粘贴内容走这里，必须先交给 EditBox，否则打不进去。
     */
    @Override
    protected void insertText(String text, boolean overwrite) {
        if (overwrite) {
            this.textField.setValue(text);
        } else {
            this.textField.insertText(text);
        }
    }

    private void sendDoneMessage(Button button) {
        String tip = this.textField.getValue();
        // 空白就不发包（上游用 StringUtils.isNotBlank，这里等价于 trim 后非空），
        // 但仍然关界面 —— 玩家点了「确定」就该有反应
        if (tip != null && !tip.trim().isEmpty()) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeByte(ServantBellSetPacket.BIND);
            buf.writeInt(this.maidId);
            buf.writeUtf(tip, ServantBellSetPacket.MAX_TIP_LENGTH);
            ClientPlayNetworking.send(ServantBellSetPacket.CHANNEL, buf);
        }
        this.onClose();
    }
}
