package com.github.tartaricacid.touhoulittlemaid.client.gui.mod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TranslatableComponent;
import org.apache.commons.lang3.StringUtils;

/**
 * 「检测到 OptiFine」的警告页，移植自上游 1.18.2
 * {@code client/gui/mod/OptifineScreen}（上游注释指明参考自 TwilightForest 的同名界面）。
 *
 * <h2>它什么时候出现</h2>
 * <p>由 {@code client/event/ShowOptifineScreen} 在<b>第一次</b>进入标题画面时决定：
 * 装了 OptiFine、且 {@code MiscConfig#CLOSE_OPTIFINE_WARNING} 为假，就顶掉标题画面。</p>
 *
 * <h2>为什么「继续」按钮要等 10 秒</h2>
 * <p>上游就是这么设计的：{@code ticksUntilEnable} 从 200 tick 倒数，倒完才允许点。
 * 目的是逼玩家把上面那段话读完 —— 光速点掉警告等于没警告。
 * ESC 同样要等到倒计时结束才生效（{@link #shouldCloseOnEsc()}）。</p>
 *
 * <p>按钮用的是 1.18.2 的 {@code new Button(...)} 构造函数写法
 * （1.19.3 之后改成 {@code Button.builder(...)}），与本仓库其它界面保持一致。</p>
 */
public class OptifineScreen extends Screen {
    public final Screen lastScreen;
    private final MutableComponent text = new TranslatableComponent("gui.touhou_little_maid.optifine_warning.text");
    private final String embeddiumUrl = "https://www.curseforge.com/minecraft/mc-mods/embeddium";
    private final String oculusUrl = "https://www.curseforge.com/minecraft/mc-mods/oculus";
    private Button exitButton;
    private int ticksUntilEnable = 20 * 10;
    private MultiLineLabel message = MultiLineLabel.EMPTY;

    public OptifineScreen(Screen lastScreen) {
        super(new TranslatableComponent("gui.touhou_little_maid.optifine_warning.title")
                .withStyle(ChatFormatting.DARK_RED).withStyle(ChatFormatting.UNDERLINE));
        this.lastScreen = lastScreen;
    }

    @Override
    public Component getNarrationMessage() {
        return CommonComponents.joinForNarration(super.getNarrationMessage(), text);
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(new Button(this.width / 2 - 155, this.height * 3 / 4 - 15, 150, 20,
                new TranslatableComponent("gui.touhou_little_maid.optifine_warning.embeddium"),
                b -> openUrl(embeddiumUrl)));
        this.addRenderableWidget(new Button(this.width / 2 + 5, this.height * 3 / 4 - 15, 150, 20,
                new TranslatableComponent("gui.touhou_little_maid.optifine_warning.oculus"),
                b -> openUrl(oculusUrl)));
        this.exitButton = this.addRenderableWidget(new Button(this.width / 2 - 75, this.height * 3 / 4 + 25, 150, 20,
                CommonComponents.GUI_PROCEED,
                pressed -> Minecraft.getInstance().setScreen(this.lastScreen)));
        this.exitButton.active = false;
        this.message = MultiLineLabel.create(this.font, text, this.width - 50);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(poseStack);
        drawCenteredString(poseStack, this.font, this.title, this.width / 2, 30, 16777215);
        this.message.renderCentered(poseStack, this.width / 2, 70);
        super.render(poseStack, mouseX, mouseY, partialTicks);
    }

    @Override
    public void tick() {
        super.tick();
        if (--this.ticksUntilEnable <= 0) {
            this.exitButton.active = true;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return this.ticksUntilEnable <= 0;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.lastScreen);
    }

    /** 点链接先弹一个「确定要打开浏览器吗」，避免误触直接跳出游戏。 */
    private void openUrl(String url) {
        if (StringUtils.isNotBlank(url) && this.minecraft != null) {
            this.minecraft.setScreen(new ConfirmLinkScreen(yes -> {
                if (yes) {
                    Util.getPlatform().openUri(url);
                }
                this.minecraft.setScreen(this);
            }, url, true));
        }
    }
}

