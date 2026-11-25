package com.github.tartaricacid.touhoulittlemaid.client.gui.mod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import org.apache.commons.lang3.StringUtils;

/**
 * 「没装 Cloth Config」时顶替配置界面的一页说明，移植自上游
 * {@code client/gui/mod/ClothConfigScreen}。
 *
 * <p>上游只在两种情况下会用到它：</p>
 * <ol>
 *     <li>玩家在模组列表里点了「配置」，而 Cloth 没装
 *         （{@code registerNoClothConfigPage()}）；</li>
 *     <li>单个女仆的 AI 设置界面要走 Cloth，而 Cloth 没装。</li>
 * </ol>
 *
 * <p>本移植版<b>不注册任何「配置按钮」</b>（那是 Forge 的 {@code ConfigGuiHandler}，
 * Fabric 上由 ModMenu 提供，见 {@code compat/modmenu/ModMenuApiImpl}），
 * 所以这一页只由 {@link #open()} 直接打开 —— 也就是玩家按了「打开配置」键、
 * 却没装 Cloth 的时候。</p>
 *
 * <p>按钮用 1.18.2 的构造函数写法（1.19.3 之后改成了 {@code Button.builder(...)}），
 * 这里与仓库其它界面保持一致。</p>
 */
public class ClothConfigScreen extends Screen {
    private static final String CLOTH_CONFIG_URL = "https://www.curseforge.com/minecraft/mc-mods/cloth-config";

    private final Screen lastScreen;
    private MultiLineLabel message = MultiLineLabel.EMPTY;

    protected ClothConfigScreen(Screen lastScreen) {
        super(new TextComponent("Cloth Config API"));
        this.lastScreen = lastScreen;
    }

    /** 打开一页「请安装 Cloth Config」，返回时回到玩家原来所在的界面。 */
    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ClothConfigScreen(minecraft.screen));
    }

    @Override
    protected void init() {
        int posX = (this.width - 200) / 2;
        int posY = this.height / 2;
        this.message = MultiLineLabel.create(this.font,
                new TranslatableComponent("gui.touhou_little_maid.cloth_config_warning.tips"), 300);

        this.addRenderableWidget(new Button(posX, posY - 15, 200, 20,
                new TranslatableComponent("gui.touhou_little_maid.cloth_config_warning.download"),
                b -> openUrl(CLOTH_CONFIG_URL)));
        this.addRenderableWidget(new Button(posX, posY + 50, 200, 20,
                CommonComponents.GUI_BACK,
                pressed -> Minecraft.getInstance().setScreen(this.lastScreen)));
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        this.message.renderCentered(poseStack, this.width / 2, 80);
        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    /** 点「下载」先弹一个「确定要打开浏览器吗」，避免误触直接跳出游戏。 */
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

