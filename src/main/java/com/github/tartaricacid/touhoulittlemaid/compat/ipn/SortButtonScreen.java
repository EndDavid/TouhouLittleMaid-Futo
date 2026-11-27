package com.github.tartaricacid.touhoulittlemaid.compat.ipn;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * IPN 排序按钮的<b>底板</b>，移植自上游 {@code compat/ipn/SortButtonScreen}。
 *
 * <h2>它画的是什么</h2>
 * <p>IPN 会在注册过的容器界面上叠一排按钮（排序 / 按列排 / 按行排 / 编辑器 / 设置）。
 * 那些按钮是 IPN 自己画的，位置由界面类上的 {@code @IPNGuiHint} 决定；
 * 我们只负责在按钮后面垫一块 17×48 的底板，免得它们是「浮在草地上」的裸图标。</p>
 *
 * <h2>坐标</h2>
 * <p>上游在 {@code AbstractMaidContainerGui#renderBg} 里调用
 * {@code renderBackground(poseStack, leftPos + 249, topPos + 166)}，本移植版一模一样
 * （见 {@code AbstractMaidScreen#renderBg}）。249 已经超出 256 宽的界面本体，
 * 所以这块底板实际挂在界面右外侧 —— 那是 IPN 按钮的落点，不是画错了。</p>
 *
 * <h2>为什么不用问 IPN 的 API</h2>
 * <p>这里只是往 {@code maid_gui_side.png} 的 (0, 73) 取 17×48 贴上去，一个 IPN 的类型都不碰。
 * 因此缺 IPN 时最坏情况也就是「多画一块贴图」，绝不会抛 {@code NoClassDefFoundError}；
 * 但为了与上游行为一致（没装 IPN 就不该多任何东西），入口处仍然问一次
 * {@link IpnCompat#isLoaded()}。</p>
 */
public final class SortButtonScreen {
    /**
     * 底板所在的贴图。它就是界面上那堆侧边页签用的同一张，
     * 底板在 UV (0, 73)，尺寸 17×48。
     */
    private static final ResourceLocation SIDE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/maid_gui_side.png");

    private SortButtonScreen() {
    }

    /**
     * 在当前坐标系里画那块底板。调用方负责保证 {@code PoseStack} 没有被平移到界面原点。
     *
     * @param poseStack 当前矩阵
     * @param x         底板的绝对 X（左上角）
     * @param y         底板的绝对 Y（左上角）
     */
    public static void renderBackground(PoseStack poseStack, int x, int y) {
        if (!IpnCompat.isLoaded()) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, SIDE);
        // 上游同样只在装了 IPN 时才 enableDepthTest。它画在物品槽之前，
        // 打开深度测试才能让底板压在界面底图之上、又不盖住后画的物品图标。
        RenderSystem.enableDepthTest();
        GuiComponent.blit(poseStack, x, y, 0, 73, 17, 48, 256, 256);
    }
}

