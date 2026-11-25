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
 * <p>重定向拿到的是原版算好的 {@code x, y}，也就是那个 16×16 物品图标的位置，直接照搬即可。
 * 深度用 100：原版在画图标之前把 {@code itemRenderer.blitOffset} 设成 100 让物品盖在页签底图上，
 * 我们顶替的正是那一层，所以取同一个值，既盖住底图也不会突出到别的东西前面。</p>
 */
@Mixin(CreativeModeInventoryScreen.class)
public class CreativeModeInventoryScreenMixin {
    /**
     * 主页签图标。由 {@code port/make-icons.py} 从 {@code pack.png}（32×32）按多数色
     * 降采样到 16×16 —— 页签里的位置就是 16×16，尺寸对上了就不用运行期缩放。
     */
    @Unique
    private static final ResourceLocation futo$tabIcon =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/tab_icon.png");

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
        // blit(pose, x, y, blitOffset, u, v, w, h, texWidth, texHeight)：贴图就是 16×16，
        // 所以 uv 直接 0..16 整张贴满，不会出现「只采到一角」。
        GuiComponent.blit(this.futo$tabPoseStack, x, y, 100, 0.0F, 0.0F, 16, 16, 16, 16);
    }
}

