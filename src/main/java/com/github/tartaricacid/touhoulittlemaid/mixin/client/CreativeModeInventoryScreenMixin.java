package com.github.tartaricacid.touhoulittlemaid.mixin.client;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把「Touhou Little Maid: Main」创造模式页签的图标换成布都的 pack 立绘。
 *
 * <h2>为什么只能走 mixin</h2>
 *
 * <p>1.18.2 里页签图标只有一个来源：{@code CreativeModeTab#getIconItem()} 返回的
 * {@code ItemStack}，再交给 {@code ItemRenderer#renderAndDecorateItem} 画成 16×16 的物品图标。
 * Fabric 的 {@code FabricItemGroupBuilder.icon(Supplier&lt;ItemStack&gt;)} 也只收物品栈，
 * 没有任何「直接给一张贴图」的口子。要既不改注册表（上游没有这种物品，
 * 凭空多注册一件会被 {@code /give}、REI 之类看到），又让它显示成画而不是物品，
 * 就只能在画的那一步把 {@code ItemStack} 换成贴图。</p>
 *
 * <h2>注入点</h2>
 *
 * <p>{@code CreativeModeInventoryScreen#renderTabButton(PoseStack, CreativeModeTab)} 里
 * 画完页签底图之后会调用一次
 * {@code itemRenderer.renderAndDecorateItem(tab.getIconItem(), x, y)}。这里把这一次调用
 * 重定向掉：是本模组的主页签就改画 {@code textures/gui/tab_icon.png}，否则原样转交给
 * 原方法（其它页签的行为一点不变）。</p>
 *
 * <p>{@code PoseStack} 与 {@code CreativeModeTab} 都是 {@code renderTabButton} 的参数，
 * 重定向处理器的入参里拿不到，所以在 HEAD 处先存进两个 {@code @Unique} 字段。
 * 单线程（渲染线程）逐帧调用，不存在并发问题。</p>
 *
 * <h2>坐标与深度</h2>
 *
 * <p>重定向拿到的是原版算好的 {@code x, y}，也就是那个 16×16 物品图标槽位的左上角。
 * 这里以它为基准保持中心不变、把边长放大到 {@link #futo$tabIconSize}，所以左上角要
 * 往回挪 {@code (size - 16) / 2}。放大是安全的：页签底图是 28×32，原版的图标位置在
 * 底图里留了 6（左右）/ 9（上）的边距，24×24 撑满后横向还剩 2 像素、纵向还剩 3 像素，
 * 不会盖到页签的圆角或边框上。</p>
 *
 * <p>深度用 100：原版在画图标之前把 {@code itemRenderer.blitOffset} 设成 100
 * 让物品盖在页签底图上，我们顶替的正是那一层，所以取同一个值，
 * 既盖住底图也不会突出到别的东西前面。</p>
 */
@Mixin(CreativeModeInventoryScreen.class)
public class CreativeModeInventoryScreenMixin {
    /**
     * 主页签图标，与 {@code pack.png} / {@code icon.png} 字节相同的 32×32 原图。
     *
     * <p><b>不做离线缩放</b>：离线先降到 16×16 等于在脚本里就丢掉一半信息 —— 面积平均会
     * 造出一百多种中间色（糊成一团），多数色虽然保住调色板但 2×2 合并后五官错位。
     * 保持 32×32 原图、交给 GPU 在画的时候点采样，颜色才全部来自原图。
     * 而且同一个文件可以随 {@link #futo$tabIconSize} 调大而自动变清楚。</p>
     */
    @Unique
    private static final ResourceLocation futo$tabIcon =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/tab_icon.png");

    /**
     * 图标画多大（GUI 像素）。原版那个物品图标槽位是 16×16，但页签底图本身是 28×32，
     * 图标位置在底图里留了 6 / 9 的边距 —— 24×24 居中放进 28×32 还剩 2~3 像素边距，
     * 是能塞下的最大值。16×16 只留得下 32×32 原图四分之一的像素，脸会糊成一团；
     * 24×24 能留下 56%。嫌太满就改成 20（边距 4）或 16（原版尺寸）。
     */
    @Unique
    private static final int futo$tabIconSize = 24;

    @Unique
    private PoseStack futo$tabPoseStack;

    @Unique
    private CreativeModeTab futo$tab;

    @Inject(
            method = "renderTabButton(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/CreativeModeTab;)V",
            at = @At("HEAD")
    )
    private void futo$captureTabRenderContext(PoseStack poseStack, CreativeModeTab tab, CallbackInfo ci) {
        this.futo$tabPoseStack = poseStack;
        this.futo$tab = tab;
    }

    @Redirect(
            method = "renderTabButton(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/CreativeModeTab;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderAndDecorateItem(Lnet/minecraft/world/item/ItemStack;II)V")
    )
    private void futo$drawMainTabIcon(ItemRenderer itemRenderer, ItemStack stack, int x, int y) {
        if (this.futo$tab != InitItems.MAIN_TAB || this.futo$tabPoseStack == null) {
            itemRenderer.renderAndDecorateItem(stack, x, y);
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, futo$tabIcon);
        // 原版给的 x, y 是那个 16×16 物品图标槽位的左上角。这里保持中心不动、把边长放大到
        // futo$tabIconSize，于是左上角要往回挪 (size - 16) / 2。
        // blit(pose, x, y, blitOffset, u, v, w, h, texWidth, texHeight)：源区域取满整张 32×32
        // 贴图（u/v = 0、w/h = 32），目标就是算出来的正方形 —— 32→size 由 GPU 点采样一次完成。
        int inset = (futo$tabIconSize - 16) / 2;
        GuiComponent.blit(this.futo$tabPoseStack, x - inset, y - inset, 100, 0.0F, 0.0F,
                futo$tabIconSize, futo$tabIconSize, 32, 32);
    }
}
