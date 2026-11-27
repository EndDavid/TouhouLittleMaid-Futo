package com.github.tartaricacid.touhoulittlemaid.compat.trinkets.client;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
/**
 * 「饰品页 / 主界面」互切的按钮，对应上游 {@code compat/curios/client/CuriosButton}。
 *
 * <p>贴图 {@code textures/gui/bauble_button.png}（256x256，按钮本体 54x31，
 * 上半是「未打开」、下半是「已打开」），与上游同一张。</p>
 *
 * <p><b>与上游的差别：</b>上游这个按钮只出现在「背包界面」里，用来切到 Curios 子页；
 * 本移植版没有那套背包界面，所以它同时充当<b>入口</b>：
 * 主界面上一个（点了去饰品页）、饰品页上一个（点了回主界面）。
 * 两个方向都走「客户端请服务端换菜单」那条通道 —— 与顶部三个页签完全一样。</p>
 */
public class TrinketBackButton extends Button {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/bauble_button.png");
    /**
     * {@code true} 表示「当前在饰品页，按钮画成已打开的样子」。
     */
    private final boolean onTrinketPage;
    private final Component tooltip;
    public TrinketBackButton(int x, int y, boolean onTrinketPage, OnPress onPress) {
        super(x, y, 54, 31, new TranslatableComponent(""), onPress);
        this.onTrinketPage = onTrinketPage;
        this.tooltip = new TranslatableComponent(onTrinketPage
                ? "gui.touhou_little_maid.curios_button.close.desc"
                : "gui.touhou_little_maid.curios_button.open.desc");
    }
    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.enableDepthTest();
        // 11 参 blit：目标 54x31，UV 从 (0, v) 取 54x31，贴图 256x256。
        // 两张状态图在贴图里是上下排列的，所以打开态取 v = 31。
        int v = this.onTrinketPage ? 31 : 0;
        GuiComponent.blit(poseStack, this.x, this.y, this.width, this.height, 0, v, 54, 31, 256, 256);
    }
    /**
     * 供界面在 {@code super.render} 之后统一弹提示。（与其它按钮的处理方式一致）。
     */
    public List<Component> getTooltip() {
        return List.of(this.tooltip);
    }
    /**
     * 鼠标是否落在这个按钮上。写成 static 工具是为了不与
     * {@code AbstractMaidScreen#isMouseOver(AbstractWidget, int, int)} 撞名。
     */
    public boolean isHovered(int mouseX, int mouseY) {
        return this.visible
               && mouseX >= this.x && mouseX < this.x + this.width
               && mouseY >= this.y && mouseY < this.y + this.height;
    }
}
