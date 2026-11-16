package com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position;
import com.github.tartaricacid.touhoulittlemaid.block.BlockWChess;
import com.github.tartaricacid.touhoulittlemaid.client.resource.BedrockBlockModels;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityWChess;
import com.github.tartaricacid.touhoulittlemaid.util.WChessUtil;
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
 * 国际象棋棋盘的渲染器，对应上游 1.20.1 的 {@code TileEntityWChessRenderer}。
 *
 * <p>棋盘是 {@code wchess.json} 的一块 3×3×2px 板子，棋子是
 * {@code wchess_pieces.json} 里 13 个骨骼（12 个棋子 + {@code SELECT} 高亮环）。
 * 渲染方式与中国象棋那块（{@link TileEntityCChessRenderer}）完全一致，
 * 三条经验直接照搬：</p>
 * <ol>
 *     <li><b>不要 ZN(180)</b>，基岩几何本来就是 Y 向上；姿态统一走
 *         {@link BoardRenderUtil#applyFacing}。</li>
 *     <li><b>摆放靠量，不靠抄常数。</b>棋子的高度、底面、中心全部用
 *         {@link GeoBlockRenderer#measureBone} 走一遍真实渲染路径量出来 ——
 *         上游那些 {@code 0.875 + 0.5} / {@code 1.625} 是给 Y 向下空间的，
 *         抄过来必然错（中国象棋当初就错了 1.372 格）。</li>
 *     <li><b>棋子的本地坐标要在点击坐标系上取负。</b>{@link BoardRenderUtil#applyFacing}
 *         对四個朝向恒等于「比点击坐标系多转 180°」，详见该方法注释。</li>
 * </ol>
 *
 * <h2>格线在哪</h2>
 * <p>点击数学（{@code WChessUtil#getClickPosition}）是 {@code floor((x + 1) / 0.25)}：
 * 8 个格子铺满 {@code -1..1}，第 k 格中心在 {@code -0.875 + 0.25k}。
 * 贴图上那 8×8 的格子区也正好落在同一范围（见 {@code port/board_grid_probe.py}
 * 的解码结果：棋盘 up 面 {@code u 48..96} 对应世界 {@code -1.5..1.5}，
 * 而格子区从 {@code u 56} 开始，换算成世界坐标就是 {@code -1.0}）。
 * 所以这里不需要任何手调偏移：棋子中心 = 格子中心。</p>
 */
public class TileEntityWChessRenderer implements BlockEntityRenderer<TileEntityWChess> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/bedrock/block/wchess.png");
    private static final ResourceLocation PIECES_TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/bedrock/block/wchess_pieces.png");
    private static final int TIPS_RENDER_DISTANCE = 16;
    private static final int PIECE_RENDER_DISTANCE = 24;
    private static final String SELECTED_BONE = "SELECT";

    /**
     * 相邻两条格线的间距（格）。与 {@code WChessUtil#getClickPosition} 里的 0.25 同一个数。
     */
    private static final float CELL = 0.25F;

    /**
     * 第 0 格（引擎的 {@code FILE_LEFT} / {@code RANK_TOP}）中心在点击坐标系里的位置。
     * 点击数学是 {@code (x + 1) / 0.25}，所以第 0 格从 -1 开始、中心在 -0.875。
     */
    private static final float LATTICE_ORIGIN = -0.875F;

    /**
     * 棋子整体缩放。上游两个版本的 {@code WChessPiecesModel#renderToBuffer} 都写了
     * {@code scale(0.9)}：棋子裸宽 0.2375 格，放进 0.25 格会几乎顶满，
     * 缩到 0.9 才有参照图里那种「棋子之间留一道缝」的样子。
     */
    private static final float PIECE_SCALE = 0.9F;

    /**
     * 引擎棋子编码 → 骨骼名。白方在 8~13、黑方在 16~21
     * （{@code Position.SIDE_TAG(sd) = 8 + (sd << 3)}，类型 {@code 0..5}）。
     */
    private static final String[] PIECE_BONES = new String[23];

    static {
        PIECE_BONES[8] = "KING_W";
        PIECE_BONES[9] = "QUEEN_W";
        PIECE_BONES[10] = "ROOK_W";
        PIECE_BONES[11] = "BISHOP_W";
        PIECE_BONES[12] = "KNIGHT_W";
        PIECE_BONES[13] = "PAWN_W";
        PIECE_BONES[16] = "KING_B";
        PIECE_BONES[17] = "QUEEN_B";
        PIECE_BONES[18] = "ROOK_B";
        PIECE_BONES[19] = "BISHOP_B";
        PIECE_BONES[20] = "KNIGHT_B";
        PIECE_BONES[21] = "PAWN_B";
    }

    /**
     * 只打印一次实测数值（诊断用，不影响行为）。
     */
    private static boolean logged = false;

    private final Font font;
    private final BlockEntityRenderDispatcher dispatcher;
    private final RenderType boardRenderType;
    private final RenderType piecesRenderType;

    public TileEntityWChessRenderer(BlockEntityRendererProvider.Context context) {
        this.dispatcher = context.getBlockEntityRenderDispatcher();
        this.font = context.getFont();
        this.boardRenderType = RenderType.entityCutoutNoCull(TEXTURE);
        this.piecesRenderType = RenderType.entityCutoutNoCull(PIECES_TEXTURE);
    }

    @Override
    public void render(TileEntityWChess chess, float partialTick, PoseStack poseStack, MultiBufferSource bufferIn,
                       int combinedLightIn, int combinedOverlayIn) {
        Direction facing = chess.getBlockState().getValue(BlockWChess.FACING);
        this.renderChessboard(poseStack, bufferIn, combinedLightIn, combinedOverlayIn, facing);
        this.renderPiece(chess, poseStack, bufferIn, combinedLightIn, combinedOverlayIn, facing);
        this.renderTipsText(chess, poseStack, bufferIn, combinedLightIn);
    }

    private void renderChessboard(PoseStack poseStack, MultiBufferSource bufferIn,
                                  int combinedLightIn, int combinedOverlayIn, Direction facing) {
        poseStack.pushPose();
        // wchess.json 的板子正好占 y 0..0.125，原点就在方块底面，不需要任何竖直偏移
        poseStack.translate(0.5, 0.0, 0.5);
        BoardRenderUtil.applyFacing(poseStack, facing);
        GeoBlockRenderer.render(BedrockBlockModels.get("wchess"), TEXTURE, poseStack, bufferIn,
                combinedLightIn, combinedOverlayIn, boardRenderType);
        poseStack.popPose();
    }

    private void renderPiece(TileEntityWChess chess, PoseStack poseStack, MultiBufferSource bufferIn,
                             int combinedLightIn, int combinedOverlayIn, Direction facing) {
        if (!inRenderDistance(chess, PIECE_RENDER_DISTANCE)) {
            return;
        }
        AnimatedGeoModel pieces = BedrockBlockModels.get("wchess_pieces");
        if (pieces == null) {
            return;
        }
        // 量一个棋子：底面在哪、中心在不在原点、多高。不能读 JSON 里的 origin，
        // 因为 inflate 会把区间整体收缩（见 GeoCube 的注释）。
        float[] pb = GeoBlockRenderer.measureBone(pieces, "KING_W");
        if (pb == null) {
            return;
        }
        float pieceCenterX = (pb[0] + pb[3]) * 0.5F;
        float pieceCenterZ = (pb[2] + pb[5]) * 0.5F;
        // 不做翻转，所以棋子底面就是 minY；缩放在原点处进行，底面仍是 minY * scale
        float pieceLift = -pb[1] * PIECE_SCALE;
        // 棋盘表面 = wchess.json 的 maxY（0.125）
        float surface = boardTopY();
        if (!logged) {
            logged = true;
            float[] bb = BedrockBlockModels.bounds("wchess");
            TouhouLittleMaid.LOGGER.info(
                    "[Futo] wchess layout: board bbox={} surface={} | piece bbox={} center=({}, {}) lift={}",
                    bb == null ? "null" : java.util.Arrays.toString(bb), surface,
                    java.util.Arrays.toString(pb), pieceCenterX, pieceCenterZ, pieceLift);
        }

        int selectX = Position.FILE_X(chess.getSelectChessPoint());
        int selectY = Position.RANK_Y(chess.getSelectChessPoint());
        byte[] data = chess.getChessData().squares;

        poseStack.pushPose();
        poseStack.translate(0.5, surface, 0.5);
        BoardRenderUtil.applyFacing(poseStack, facing);
        for (int y = Position.RANK_TOP; y <= Position.RANK_BOTTOM; y++) {
            for (int x = Position.FILE_LEFT; x <= Position.FILE_RIGHT; x++) {
                int index = Position.COORD_XY(x, y);
                if (index < 0 || index >= data.length) {
                    continue;
                }
                byte piecesIndex = data[index];
                boolean hasPiece = WChessUtil.isWhite(piecesIndex) || WChessUtil.isBlack(piecesIndex);
                if (hasPiece && piecesIndex >= 0 && piecesIndex < PIECE_BONES.length) {
                    float localX = LATTICE_ORIGIN + (x - Position.FILE_LEFT) * CELL;
                    float localZ = LATTICE_ORIGIN + (y - Position.RANK_TOP) * CELL;
                    // 姿态比点击坐标系多转半圈，所以格点坐标取负（见 BoardRenderUtil#applyFacing）。
                    // 中心偏移也跟着取负，否则不对称的模型会被推开两倍偏移量。
                    float renderX = pieceCenterX - localX;
                    float renderZ = pieceCenterZ - localZ;
                    String bone = PIECE_BONES[piecesIndex];
                    if (bone != null) {
                        renderOnePiece(pieces, bone, poseStack, bufferIn, combinedLightIn, combinedOverlayIn,
                                renderX, pieceLift, renderZ);
                    }
                    if (selectX == x && selectY == y) {
                        renderOnePiece(pieces, SELECTED_BONE, poseStack, bufferIn, combinedLightIn, combinedOverlayIn,
                                renderX, pieceLift, renderZ);
                    }
                }
            }
        }
        poseStack.popPose();
    }

    private void renderOnePiece(AnimatedGeoModel pieces, String bone, PoseStack poseStack, MultiBufferSource bufferIn,
                                int combinedLightIn, int combinedOverlayIn, float x, float y, float z) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.scale(PIECE_SCALE, PIECE_SCALE, PIECE_SCALE);
        GeoBlockRenderer.renderBone(pieces, bone, PIECES_TEXTURE, poseStack, bufferIn,
                combinedLightIn, combinedOverlayIn, piecesRenderType);
        poseStack.popPose();
    }

    private void renderTipsText(TileEntityWChess chess, PoseStack poseStack, MultiBufferSource bufferIn,
                                int combinedLightIn) {
        boolean showTips = chess.isCheckmate() || chess.isRepeat() || chess.isMoveNumberLimit();
        if (!showTips || !inRenderDistance(chess, TIPS_RENDER_DISTANCE)) {
            return;
        }
        MutableComponent loseTips = null;
        MutableComponent resetTips = new TranslatableComponent("message.touhou_little_maid.wchess.reset")
                .withStyle(ChatFormatting.UNDERLINE).withStyle(ChatFormatting.AQUA);
        if (chess.isCheckmate()) {
            if (!chess.isPlayerTurn()) {
                loseTips = new TranslatableComponent("message.touhou_little_maid.gomoku.win")
                        .withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.DARK_PURPLE);
            } else {
                loseTips = new TranslatableComponent("message.touhou_little_maid.gomoku.lose")
                        .withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.DARK_PURPLE);
            }
        } else if (chess.isMoveNumberLimit()) {
            loseTips = new TranslatableComponent("message.touhou_little_maid.wchess.move_limit")
                    .withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.DARK_PURPLE);
        } else if (chess.isRepeat()) {
            loseTips = new TranslatableComponent("message.touhou_little_maid.cchess.repeat")
                    .withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.DARK_PURPLE);
        }
        if (loseTips == null) {
            return;
        }
        Camera camera = this.dispatcher.camera;
        BoardRenderUtil.drawTips(this.font, camera, poseStack, bufferIn, combinedLightIn,
                loseTips, resetTips, BoardRenderUtil.roundIcon(chess.getChessCounter()));
    }

    /**
     * 棋盘表面离方块底面的高度（棋子要贴在这个高度上）。
     */
    private static float boardTopY() {
        float[] b = BedrockBlockModels.bounds("wchess");
        return b == null ? 0.0F : b[4];
    }

    private boolean inRenderDistance(TileEntityWChess chess, int distance) {
        BlockPos pos = chess.getBlockPos();
        return this.dispatcher.camera.getPosition().distanceToSqr(pos.getX(), pos.getY(), pos.getZ())
                < (double) distance * distance;
    }

    @Override
    public boolean shouldRenderOffScreen(TileEntityWChess te) {
        return true;
    }
}

