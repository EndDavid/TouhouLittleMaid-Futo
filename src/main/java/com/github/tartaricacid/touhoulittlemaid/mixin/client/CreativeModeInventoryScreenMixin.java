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
 * 这里以它为基准保持中心不变、边长取 {@link #futo$tabIconSize}（默认 16，即原版尺寸），
 * 所以左上角要往回挪 {@code (size - 16) / 2}；默认值下偏移为 0，位置与原版完全一致。</p>
 *
 * <p>贴图保持 32×32 不缩放，靠 {@code blit} 把整张 UV 压进 16×16 的目标矩形，
 * 由 GPU 点采样一次完成。这样才能「32×32 的材质按 16×16 显示」——
 * 注意不能用 9 参的 {@code blit}：那个重载的 UV 跨度就是目标宽高，写 16 会变成裁掉原图四分之三。</p>
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
     * 图标画多大（GUI 像素）。默认 16 —— 与原版物品图标槽位同尺寸，也就是「正常大小」。
     *
     * <p>贴图是 32×32，画到 16×16 就是 2:1 点采样（每个输出像素取原图 2×2 块里的一枚），
     * 颜色全部来自原图、边缘保持硬边，不会糊。页签底图有 28×32 大，理论上画到 24×24
     * 也放得下（横向还剩 2 像素），嫌小可以直接改这个常量，贴图不用动。</p>
     */
    @Unique
    private static final int futo$tabIconSize = 16;

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
        // 原版给的 x, y 是那个 16×16 物品图标槽位的左上角。保持中心不动、边长取
        // futo$tabIconSize，左上角就要往回挪 (size - 16) / 2；默认 16 时偏移为 0。
        int inset = (futo$tabIconSize - 16) / 2;

        // 【必须用这个 11 参重载】它把「目标尺寸」与「UV 范围」分成两组参数：
        //     blit(pose, x, y, width, height, u, v, uWidth, vHeight, texWidth, texHeight)
        // 而常用的 9 参重载 blit(pose, x, y, blitOffset, u, v, w, h, texW, texH) 里
        // UV 范围是与目标尺寸共用 w/h 的（它内部传的就是 (u, v, w, h) 当 uv 跨度），
        // 写 (16, 16, 32, 32) 会变成「取原图左上角 16×16 那四分之一」—— 那是裁切，不是缩放。
        // 这里目标 16×16、UV 取满 0..32，才是真正的 2:1 点采样。
        this.futo$tabPoseStack.pushPose();
        // 原版画物品图标前把 itemRenderer.blitOffset 设成 100 让它压在页签底图上；
        // 这个重载的 z 固定为 0，所以用 pose 的 z 平移达到同样的层级。
        this.futo$tabPoseStack.translate(0.0, 0.0, 100.0);
        GuiComponent.blit(this.futo$tabPoseStack, x - inset, y - inset,
                futo$tabIconSize, futo$tabIconSize, 0.0F, 0.0F, 32, 32, 32, 32);
        this.futo$tabPoseStack.popPose();
    }
}
