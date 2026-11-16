package com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Point;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Statue;
import com.github.tartaricacid.touhoulittlemaid.block.BlockGomoku;
import com.github.tartaricacid.touhoulittlemaid.client.resource.BedrockBlockModels;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGomoku;
import com.github.tartaricacid.touhoulittlemaid.util.GomokuUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * 五子棋棋盘的渲染器，对应上游 1.20.1 的 {@code TileEntityGomokuRenderer}。
 *
 * <p>棋盘是 {@code gomoku.json}（一张 2×2 格的木桌 + 一层格线），棋子是
 * {@code gomoku_piece.json}（一颗 2×1×2 的圆饼）。画法与另外两张棋盘一致，
 * 三条经验照搬：不要 {@code ZN(180)}、摆放靠 {@code measureBone} 量、
 * 棋子本地坐标在点击坐标系上取负。</p>
 *
 * <h2>格线是怎么定下来的</h2>
 * <p>这张桌子没有格线贴图可以直接数（格线是单独一层 cube，贴图里是一张
 * 151×151 的透明底图）。用 {@code port/board_grid_probe.py} 解码后发现：
 * 竖线和横线都落在 rect 内第 5、15、25 … 145 个 texel 上 —— 正好 15 条、间距 10 texel、
 * 两端各留 5 texel、关于中心完全对称。151 texel 对应 2 格，于是</p>
 * <pre>
 *     间距   = 10 / 151 * 2 = 0.132450 格
 *     第 0 条 = -1 + 5.5 / 151 * 2 = -0.927152 格
 * </pre>
 * <p>这与 {@link GomokuUtil} 里的两个常数是同一组数字 —— 点击判定和渲染共用它们，
 * 所以「棋子画在哪里」和「点哪里能落子」在纸面上就不可能不一致。</p>
 */
public class TileEntityGomokuRenderer implements BlockEntityRenderer<TileEntityGomoku> {
    private static final ResourceLocation BOARD_TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/bedrock/block/gomoku.png");
    private static final ResourceLocation BLACK_PIECE_TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/bedrock/block/gomoku_black_piece.png");
    private static final ResourceLocation WHITE_PIECE_TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/bedrock/block/gomoku_white_piece.png");
    private static final int TIPS_RENDER_DISTANCE = 16;
    private static final int PIECE_RENDER_DISTANCE = 24;

    /**
     * 「最近一手」标记的贴图与字号，取自上游 {@code renderLatestChessTips}。
     */
    private static final String LATEST_MARK = "▼";
    private static final float LATEST_MARK_SCALE = 0.015625F;

    /**
     * 只打印一次实测数值（诊断用，不影响行为）。
     */
    private static boolean logged = false;

    private final Font font;
    private final BlockEntityRenderDispatcher dispatcher;
    private final RenderType boardRenderType;
    private final RenderType blackRenderType;
    private final RenderType whiteRenderType;

    public TileEntityGomokuRenderer(BlockEntityRendererProvider.Context context) {
        this.dispatcher = context.getBlockEntityRenderDispatcher();
        this.font = context.getFont();
        this.boardRenderType = RenderType.entityCutoutNoCull(BOARD_TEXTURE);
        this.blackRenderType = RenderType.entityCutoutNoCull(BLACK_PIECE_TEXTURE);
        this.whiteRenderType = RenderType.entityCutoutNoCull(WHITE_PIECE_TEXTURE);
    }

    @Override
    public void render(TileEntityGomoku gomoku, float partialTick, PoseStack poseStack, MultiBufferSource bufferIn,
                       int combinedLightIn, int combinedOverlayIn) {
        Direction facing = gomoku.getBlockState().getValue(BlockGomoku.FACING);
        this.renderChessboard(poseStack, bufferIn, combinedLightIn, combinedOverlayIn, facing);
        this.renderPiece(gomoku, poseStack, bufferIn, combinedLightIn, combinedOverlayIn, facing);
        this.renderLatestChessTips(gomoku, poseStack, bufferIn, combinedLightIn, facing);
        this.renderTipsText(gomoku, poseStack, bufferIn, combinedLightIn);
    }

    private void renderChessboard(PoseStack poseStack, MultiBufferSource bufferIn,
                                  int combinedLightIn, int combinedOverlayIn, Direction facing) {
        poseStack.pushPose();
        // 桌子腿故意埋进地下（模型 minY 是 -1.89），所以竖直偏移必须是 0
        poseStack.translate(0.5, 0.0, 0.5);
        BoardRenderUtil.applyFacing(poseStack, facing);
        GeoBlockRenderer.render(BedrockBlockModels.get("gomoku"), BOARD_TEXTURE, poseStack, bufferIn,
                combinedLightIn, combinedOverlayIn, boardRenderType);
        poseStack.popPose();
    }

    private void renderPiece(TileEntityGomoku gomoku, PoseStack poseStack, MultiBufferSource bufferIn,
                             int combinedLightIn, int combinedOverlayIn, Direction facing) {
        if (!inRenderDistance(gomoku, PIECE_RENDER_DISTANCE)) {
            return;
        }
        AnimatedGeoModel pieceModel = BedrockBlockModels.get("gomoku_piece");
        if (pieceModel == null) {
            return;
        }
        // 棋子只有一颗，量一次就够：中心在原点、底面 0.015625、顶面 0.046875
        float[] pb = GeoBlockRenderer.measureBone(pieceModel, "main");
        if (pb == null) {
            return;
        }
        float pieceCenterX = (pb[0] + pb[3]) * 0.5F;
        float pieceCenterZ = (pb[2] + pb[5]) * 0.5F;
        // 棋盘表面：不能取整张桌子的包围盒（棋盒会把它抬到 0.29），
        // 只量 main 这一支 —— 它才是桌面 + 格线层。
        float surface = boardSurface();
        float pieceLift = surface - pb[1];
        if (!logged) {
            logged = true;
            float[] bb = BedrockBlockModels.bounds("gomoku");
            TouhouLittleMaid.LOGGER.info(
                    "[Futo] gomoku layout: board bbox={} surface={} | piece bbox={} center=({}, {}) lift={} "
                            + "| lattice origin={} step={}",
                    bb == null ? "null" : java.util.Arrays.toString(bb), surface,
                    java.util.Arrays.toString(pb), pieceCenterX, pieceCenterZ, pieceLift,
                    GomokuUtil.GRID_ORIGIN, GomokuUtil.GRID_STEP);
        }

        int[][] chessData = gomoku.getChessData();
        poseStack.pushPose();
        poseStack.translate(0.5, surface, 0.5);
        BoardRenderUtil.applyFacing(poseStack, facing);
        for (int x = 0; x < GomokuUtil.GRID_SIZE; x++) {
            for (int y = 0; y < GomokuUtil.GRID_SIZE; y++) {
                int type = chessData[x][y];
                if (type != Point.BLACK && type != Point.WHITE) {
                    continue;
                }
                float localX = (float) GomokuUtil.lineAt(x);
                float localZ = (float) GomokuUtil.lineAt(y);
                // 姿态比点击坐标系多转半圈，所以格线坐标取负（见 BoardRenderUtil#applyFacing）
                float renderX = pieceCenterX - localX;
                float renderZ = pieceCenterZ - localZ;
                poseStack.pushPose();
                poseStack.translate(renderX, pieceLift, renderZ);
                GeoBlockRenderer.render(pieceModel,
                        type == Point.BLACK ? BLACK_PIECE_TEXTURE : WHITE_PIECE_TEXTURE,
                        poseStack, bufferIn, combinedLightIn, combinedOverlayIn,
                        type == Point.BLACK ? blackRenderType : whiteRenderType);
                poseStack.popPose();
            }
        }
        poseStack.popPose();
    }

    /**
     * 「上一手在哪儿」的小三角，浮在那颗棋子上方、永远正对镜头。
     */
    private void renderLatestChessTips(TileEntityGomoku gomoku, PoseStack poseStack, MultiBufferSource bufferIn,
                                       int combinedLightIn, Direction facing) {
        Point point = gomoku.getLatestChessPoint();
        if (point == Point.NULL || !inRenderDistance(gomoku, PIECE_RENDER_DISTANCE)) {
            return;
        }
        if (point.x < 0 || point.x >= GomokuUtil.GRID_SIZE || point.y < 0 || point.y >= GomokuUtil.GRID_SIZE) {
            return;
        }
        Camera camera = this.dispatcher.camera;
        float localX = (float) GomokuUtil.lineAt(point.x);
        float localZ = (float) GomokuUtil.lineAt(point.y);
        // 标记必须跟着棋盘转动，否则棋盘朝东 / 朝西时它会跑到另一颗棋子上方。
        // 这里把「棋子用的那套变换」手算一遍（translate(0.5,·,0.5) + Ry(θ)），
        // 因为文字的 billboard 旋转必须发生在<b>世界坐标</b>里，不能被棋盘的旋转带着走。
        float renderX = -localX;
        float renderZ = -localZ;
        double yaw = Math.toRadians(facing.toYRot() + (facing.getAxis() == Direction.Axis.Z ? 180.0 : 0.0));
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        float worldX = (float) (0.5 + renderX * cos + renderZ * sin);
        float worldZ = (float) (0.5 - renderX * sin + renderZ * cos);
        poseStack.pushPose();
        poseStack.translate(worldX, boardSurface() + 0.25F, worldZ);
        poseStack.mulPose(com.mojang.math.Vector3f.YN.rotationDegrees(180 + camera.getYRot()));
        poseStack.scale(LATEST_MARK_SCALE, -LATEST_MARK_SCALE, LATEST_MARK_SCALE);
        float width = (float) (-this.font.width(LATEST_MARK) / 2) + 0.5F;
        this.font.drawInBatch(LATEST_MARK, width, -1.5F, 0xFF0000, false,
                poseStack.last().pose(), bufferIn, false, 0, combinedLightIn);
        poseStack.popPose();
    }

    private void renderTipsText(TileEntityGomoku gomoku, PoseStack poseStack, MultiBufferSource bufferIn,
                                int combinedLightIn) {
        Statue statue = gomoku.getStatue();
        if (statue == Statue.IN_PROGRESS || !inRenderDistance(gomoku, TIPS_RENDER_DISTANCE)) {
            return;
        }
        MutableComponent loseTips;
        MutableComponent resetTips = new TranslatableComponent("message.touhou_little_maid.gomoku.reset")
                .withStyle(ChatFormatting.UNDERLINE).withStyle(ChatFormatting.AQUA);
        if (statue == Statue.WIN) {
            // 玩家先手执黑，所以「轮到玩家」= 玩家赢
            if (gomoku.isPlayerTurn()) {
                loseTips = new TranslatableComponent("message.touhou_little_maid.gomoku.win")
                        .withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.DARK_PURPLE);
            } else {
                loseTips = new TranslatableComponent("message.touhou_little_maid.gomoku.lose")
                        .withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.DARK_PURPLE);
            }
        } else {
            loseTips = new TranslatableComponent("message.touhou_little_maid.gomoku.draw")
                    .withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.DARK_PURPLE);
        }
        Camera camera = this.dispatcher.camera;
        BoardRenderUtil.drawTips(this.font, camera, poseStack, bufferIn, combinedLightIn,
                loseTips, resetTips, BoardRenderUtil.roundIcon(gomoku.getChessCounter()));
    }

    /**
     * 棋盘表面离方块底面的高度。
     *
     * <p><b>必须只量 {@code main}。</b>整张桌子的包围盒会被两个棋盒抬到 0.29 附近，
     * 拿它当表面会让所有棋子浮在半空。{@code main} 是桌面 + 那层格线，
     * 顶面实测 0.125625。</p>
     */
    private static float boardSurface() {
        AnimatedGeoModel board = BedrockBlockModels.get("gomoku");
        if (board == null) {
            return 0.0F;
        }
        float[] main = GeoBlockRenderer.measureBone(board, "main");
        return main == null ? 0.0F : main[4];
    }

    private boolean inRenderDistance(TileEntityGomoku gomoku, int distance) {
        BlockPos pos = gomoku.getBlockPos();
        return this.dispatcher.camera.getPosition().distanceToSqr(pos.getX(), pos.getY(), pos.getZ())
                < (double) distance * distance;
    }

    @Override
    public boolean shouldRenderOffScreen(TileEntityGomoku te) {
        return true;
    }
}

