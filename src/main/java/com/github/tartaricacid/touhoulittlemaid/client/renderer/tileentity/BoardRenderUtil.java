package com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

/**
 * 三张棋盘（中国象棋 / 五子棋 / 国际象棋）共用的姿态与提示文字。
 *
 * <p>为什么必须共用：{@link #applyFacing} 里的那两行是<b>棋子与格线对不齐</b>这类
 * 问题的唯一根源，三块棋盘各写一遍就一定会有一块写错。上一轮中国象棋的
 * 「右侧棋子不在格线上 / 字是倒的」就是这里少了一句半圈。细节见该方法的注释。</p>
 */
public final class BoardRenderUtil {
    /**
     * 提示文字离地面的高度、字号缩放，取自上游三个渲染器里逐字相同的数值。
     */
    private static final float TIPS_HEIGHT = 0.75F;
    private static final float TIPS_SCALE = 0.03F;

    private BoardRenderUtil() {
    }

    /**
     * 棋盘与棋子共用的朝向姿态。
     *
     * <p>上游（1.18.2 与 1.20.1 两份渲染器在这一段上逐字相同）写的是：</p>
     * <pre>
     * mulPose(ZN(180));
     * mulPose(YN(facing.toYRot()));
     * if (facing == NORTH || facing == SOUTH) mulPose(YN(180));
     * </pre>
     *
     * <h2>为什么这里写的是 {@code YP}</h2>
     * <p>那句 {@code ZN(180)} 是为了把原版 {@code ModelPart} 的 <b>Y 向下</b> 翻成 Y 向上，
     * 而它顺带着把 x 也取了负。本移植版画的基岩几何（{@code GeoCube}）本来就是 Y 向上，
     * 而且 {@code GeoCube#createFromPojoCube} 里<b>已经把 x 取过一次负</b>。
     * 把 {@code M = Z·R(-θ)} 用 {@code Z} 共轭到基岩空间就是 {@code Z·R(-θ)·Z = R(θ)}，
     * 所以这里写 {@code YP(θ)}。</p>
     *
     * <p>写成 {@code YN(θ)} 在 NORTH / SOUTH 下看不出差别（±180° 互为逆），
     * 但在 EAST / WEST 会差整整半圈：点击命中的格子和看到的棋子就对不上。</p>
     *
     * <h2>为什么 NORTH / SOUTH 还要再 180°</h2>
     * <p>上游就有这一句。它和上一条合起来等于「相对点击坐标系多转半圈」——
     * 这就是三张棋盘的渲染器里棋子的本地坐标都要写 {@code -clickX} 的原因
     * （见各自渲染器里那行 {@code renderX = ... - localX}）。</p>
     */
    public static void applyFacing(PoseStack poseStack, Direction facing) {
        poseStack.mulPose(Vector3f.YP.rotationDegrees(facing.toYRot()));
        if (facing.getAxis() == Direction.Axis.Z) {
            poseStack.mulPose(Vector3f.YP.rotationDegrees(180.0F));
        }
    }

    /**
     * 终局 / 和局时浮在棋盘上方、永远正对镜头的一行提示。
     *
     * @param loseTips  胜负文字（调用方已经决定好内容与颜色）
     * @param resetTips 重开提示
     * @param roundIcon 回合数（{@code ⏹ N ⏹}）
     */
    public static void drawTips(Font font, Camera camera, PoseStack poseStack, MultiBufferSource bufferIn,
                                int combinedLightIn, Component loseTips, Component resetTips, Component roundIcon) {
        float loseTipsWidth = (float) (-font.width(loseTips) / 2);
        float resetTipsWidth = (float) (-font.width(resetTips) / 2);
        float roundTipsWidth = (float) (-font.width(roundIcon) / 2);
        poseStack.pushPose();
        poseStack.translate(0.5, TIPS_HEIGHT, 0.5);
        // 文字永远正对镜头：先转回玩家视角的偏航，再补上俯仰
        poseStack.mulPose(Vector3f.YN.rotationDegrees(180 + camera.getYRot()));
        poseStack.mulPose(Vector3f.XN.rotationDegrees(camera.getXRot()));
        poseStack.scale(TIPS_SCALE, -TIPS_SCALE, TIPS_SCALE);
        font.drawInBatch(loseTips, loseTipsWidth, -10, 0xFFFFFF, true,
                poseStack.last().pose(), bufferIn, false, 0, combinedLightIn);
        poseStack.scale(0.5F, 0.5F, 0.5F);
        font.drawInBatch(roundIcon, roundTipsWidth, -30, 0xFFFFFF, true,
                poseStack.last().pose(), bufferIn, false, 0, combinedLightIn);
        font.drawInBatch(resetTips, resetTipsWidth, 0, 0xFFFFFF, true,
                poseStack.last().pose(), bufferIn, false, 0, combinedLightIn);
        poseStack.popPose();
    }

    /**
     * {@code ⏹ N ⏹} 这一行回合数，三张棋盘格式相同。
     */
    public static MutableComponent roundIcon(int round) {
        MutableComponent roundText = new TranslatableComponent("message.touhou_little_maid.gomoku.round", round)
                .withStyle(ChatFormatting.WHITE);
        MutableComponent preIcon = new TextComponent("⏹ ").withStyle(ChatFormatting.GREEN);
        MutableComponent postIcon = new TextComponent(" ⏹").withStyle(ChatFormatting.GREEN);
        return preIcon.append(roundText).append(postIcon);
    }
}

