package com.github.tartaricacid.touhoulittlemaid.mixin.client;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.network.ServerPresence;
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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把「Touhou Little Maid: Main」创造模式页签的图标换成布都的 pack 立绘。
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

    /**
     * 当前选中的页签下标。原版字段是 {@code private static}，这里只用来在
     * 「选中的正是被藏起来的页签」时退回到「建筑方块」，免得高亮框停在空白位置。
     */
    @Shadow
    private static int selectedTab;

    /**
     * 这个页签是不是本模组的三个之一。
     */
    @Unique
    private static boolean futo$isOurs(CreativeModeTab tab) {
        return tab == InitItems.MAIN_TAB || tab == InitItems.CHAIR_TAB || tab == InitItems.GARAGE_KIT_TAB;
    }

    /**
     * 本模组的页签在<i>当前这个服务端</i>上是否该消失。
     */
    @Unique
    private static boolean futo$hiddenHere(CreativeModeTab tab) {
        return ServerPresence.isAbsent() && futo$isOurs(tab);
    }

    /**
     * 选中的页签被藏起来时退回「建筑方块」。
     *
     * <p>只在极端情况下用得上：玩家在连接后的头 3 秒（判定还没完成）就打开创造界面、
     * 恰好选中本模组的页签，之后判定落地。不做这一步的话内容虽为空，但高亮框会停在
     * 一个没有按钮的位置。</p>
     */
    @Inject(method = "init", at = @At("HEAD"))
    private void futo$dropSelectionOnHiddenTab(CallbackInfo ci) {
        if (!ServerPresence.isAbsent()) {
            return;
        }
        CreativeModeTab[] tabs = CreativeModeTab.TABS;
        if (selectedTab < 0 || selectedTab >= tabs.length) {
            return;
        }
        if (futo$isOurs(tabs[selectedTab])) {
            selectedTab = CreativeModeTab.TAB_BUILDING_BLOCKS.getId();
        }
    }

    @Inject(
            method = "renderTabButton(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/CreativeModeTab;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void futo$captureTabRenderContext(PoseStack poseStack, CreativeModeTab tab, CallbackInfo ci) {
        if (futo$hiddenHere(tab)) {
            ci.cancel();
            return;
        }
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

    /**
     * 按钮不画了，点击判定也要一起失效 —— 否则玩家能在空白处点开一个看不见的页签。
     */
    @Inject(
            method = "checkTabClicked(Lnet/minecraft/world/item/CreativeModeTab;DD)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void futo$noClickOnHiddenTab(CreativeModeTab tab, double mouseX, double mouseY,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (futo$hiddenHere(tab)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 悬停同理：不画按钮，也不能在空白处弹出页签名提示。
     */
    @Inject(
            method = "checkTabHovering(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/CreativeModeTab;II)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void futo$noHoverOnHiddenTab(PoseStack poseStack, CreativeModeTab tab, int mouseX, int mouseY,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (futo$hiddenHere(tab)) {
            cir.setReturnValue(false);
        }
    }
}
