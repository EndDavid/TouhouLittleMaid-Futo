package com.github.tartaricacid.touhoulittlemaid.client.screen.ai;

import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.FlatColorButton;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.GetMaidAIDataPacket;
import com.github.tartaricacid.touhoulittlemaid.network.SendUserChatPacket;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;

/**
 * AI 聊天输入界面，移植自上游 client/gui/entity/maid/ai/AIChatScreen。
 *
 * <p>它只是一个输入框：写一句话、按回车，然后由服务端发起请求并把回复
 * 画到女仆头顶的气泡上（同时给主人发一条聊天栏消息）。
 * 界面上不显示历史，也不显示回复。
 * 上游也是这么做的，理由很实际：回复是异步的，最长可能二十秒，
 * 界面早就被玩家关掉了。
 *
 * <h2>两个静态表</h2>
 * <p>CLIENT_CHAT_SITES 与 CLIENT_TTS_SITES 由 SyncAiSettingPacket 填充，
 * 只有站点名与模型列表，不含密钥。它们同时被设置界面用来做下拉选项。
 *
 * <h2>与上游的差异</h2>
 * <ul>
 *     <li>configButton 不设 tooltip：本移植版的 FlatColorButton 没有 setTooltips，
 *         那套挂起 tooltip 的渲染还没移植。按钮内容仍是同一个铅笔符号。</li>
 *     <li>不再调用 NetworkHandler.CHANNEL，改用 SendUserChatPacket / GetMaidAIDataPacket。</li>
 * </ul>
 */
public class AIChatScreen extends Screen {
    public static final Map<String, List<String>> CLIENT_CHAT_SITES = Maps.newLinkedHashMap();
    public static final Map<String, List<String>> CLIENT_TTS_SITES = Maps.newLinkedHashMap();

    private final EntityMaid maid;
    private EditBox input;
    private FlatColorButton configButton;
    private int tickCounter = 0;

    public AIChatScreen(EntityMaid maid) {
        super(new TextComponent("Maid AI Chat Screen"));
        this.maid = maid;
    }

    @Override
    protected void init() {
        int posX = this.width / 2;
        int posY = this.height / 2;

        this.input = new EditBox(Minecraft.getInstance().font, posX - 165, posY + 64,
                300, 20, new TranslatableComponent("chat.editBox"));
        // 128 与服务端那一侧的裁剪上限一致：界面能打多少，服务端就收多少
        this.input.setMaxLength(128);
        this.input.setBordered(false);
        this.input.setValue("");
        this.input.setCanLoseFocus(false);
        this.addWidget(this.input);
        this.setInitialFocus(this.input);

        this.configButton = new FlatColorButton(posX + 142, posY + 58, 20, 20, new TextComponent("✎"),
                b -> GetMaidAIDataPacket.send(this.maid.getId()));
        this.addRenderableWidget(this.configButton);
    }

    @Override
    public void resize(Minecraft mc, int pWidth, int pHeight) {
        String chatText = this.input.getValue();
        super.resize(mc, pWidth, pHeight);
        this.input.setValue(chatText);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        int xOffset = 5;
        int yOffset = 6;
        fill(poseStack, input.x - xOffset,
                input.y - yOffset,
                input.x + input.getInnerWidth() + xOffset,
                input.y + input.getHeight() - yOffset,
                0x9f_000000);
        input.render(poseStack, mouseX, mouseY, partialTicks);

        if (StringUtils.isEmpty(input.getValue())) {
            MutableComponent text = new TranslatableComponent("ai.touhou_little_maid.chat.input.tip")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            int xPos = input.x + input.getInnerWidth() / 2 - xOffset;
            drawCenteredString(poseStack, font, text, xPos, input.y, 0xFFFFFF);
        }

        super.render(poseStack, mouseX, mouseY, partialTicks);
    }

    @Override
    public void tick() {
        this.input.tick();
        tickCounter++;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.input.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.input);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char pCodePoint, int pModifiers) {
        // 界面刚打开的 5 tick 内不接受输入，否则「按 T 打开」的那个 T 会被录入输入框
        if (tickCounter < 5) {
            return false;
        }
        return super.charTyped(pCodePoint, pModifiers);
    }

    @Override
    protected void insertText(String text, boolean overwrite) {
        if (overwrite) {
            this.input.setValue(text);
        } else {
            this.input.insertText(text);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            sendDoneMessage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void sendDoneMessage() {
        String value = input.getValue();
        LocalPlayer player = Minecraft.getInstance().player;
        if (StringUtils.isNotBlank(value) && player != null) {
            // 语言用客户端当前语言的显示名（如 English (US) / 简体中文），
            // 它会被拼进 prompt，所以只能由客户端提供
            LanguageManager languageManager = Minecraft.getInstance().getLanguageManager();
            LanguageInfo info = languageManager.getLanguage(languageManager.getSelected().getCode());
            String language = info != null ? info.toString() : "English (US)";
            SendUserChatPacket.send(this.maid.getId(), value, language);
            // 本地立刻回显自己说的话：等到 AI 回复再显示会让玩家怀疑没发出去
            String name = player.getScoreboardName();
            String format = String.format("<%s> %s", name, value);
            player.sendMessage(new TextComponent(format).withStyle(ChatFormatting.GRAY), Util.NIL_UUID);
        }
        this.onClose();
    }
}