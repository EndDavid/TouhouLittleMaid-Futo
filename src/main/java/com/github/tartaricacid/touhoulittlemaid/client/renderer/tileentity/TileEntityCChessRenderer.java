package com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.game.xqwlight.Position;
import com.github.tartaricacid.touhoulittlemaid.block.BlockCChess;
import com.github.tartaricacid.touhoulittlemaid.client.resource.BedrockBlockModels;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityCChess;
import com.github.tartaricacid.touhoulittlemaid.util.CChessUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
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
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * 象棋棋盘的渲染器，移植自上游 1.20.1 的 {@code TileEntityCChessRenderer}。
 *
 * <p>1.18.2 的上游用原版 {@code EntityModel} 画棋盘、用 {@code simplebedrockmodel} 画棋子；
 * 1.20.1 把两者都换成了基岩模型。本移植版跟随 1.20.1 —— 因为本移植的
 * {@link BedrockBlockModels} 已经把祭坛 / 神社那条管线跑通了，
 * 而 1.18.2 的 {@code CChessModel} 还需要再烘焙一层 {@code ModelLayer}，两条路只能选一条。</p>
 *
 * <h2>棋子怎么画</h2>
 * <p>{@code cchess_pieces.json} 是一个模型里塞了 15 个互不相干的骨骼
 * （{@link #PIECE_BONES} 的下标就是引擎的棋子编码）。每个交叉点按棋盘上的棋子编码
 * 取一个骨骼名，用 {@link GeoBlockRenderer#renderBone} 单独画那一支。
 * {@code Selected} 是那块高亮底环，叠在选中棋子上。</p>
 *
 * <h2>3×3 的坐标是怎么来的</h2>
 * <p>棋盘占 3×3 格，方块实体只在中心格上，因此渲染器要把自己摆到整张桌子的中心
 * （{@code translate(0.5, 1.5, 0.5)} 里的 1.5 是模型自身的高度），随后每个交叉点间隔
 * 0.304 格 —— 与 {@code CChessUtil#getClickPosition} 里那两个魔数是同一组数字，
 * 改一个就必须改另一个，否则「看得见的棋子」和「点得到的格子」会错位。</p>
 *
 * <p><b>已知限制：</b>1.18.2 的方块实体没有 {@code getRenderBoundingBox}，
 * 所以中心格离开视锥时整张桌子会消失（与本移植版雕像渲染盒的问题同源）。</p>
 */
public class TileEntityCChessRenderer implements BlockEntityRenderer<TileEntityCChess> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/bedrock/block/cchess.png");
    private static final ResourceLocation PIECES_TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/bedrock/block/cchess_pieces.png");
    private static final int TIPS_RENDER_DISTANCE = 16;
    private static final int PIECE_RENDER_DISTANCE = 24;
    private static final String SELECTED_BONE = "Selected";

    /**
     * 引擎棋子编码 → 骨骼名。红方在 8~14、黑方在 16~22（见 {@code Position} 的棋子常量）。
     */
    private static final String[] PIECE_BONES = new String[23];

    static {
        PIECE_BONES[8] = "ShuaiRed";
        PIECE_BONES[9] = "ShiRed";
        PIECE_BONES[10] = "XiangRed";
        PIECE_BONES[11] = "MaRed";
        PIECE_BONES[12] = "JuRed";
        PIECE_BONES[13] = "PaoRed";
        PIECE_BONES[14] = "BingRed";
        PIECE_BONES[16] = "JiangBlack";
        PIECE_BONES[17] = "ShiBlack";
        PIECE_BONES[18] = "XiangBlack";
        PIECE_BONES[19] = "MaBlack";
        PIECE_BONES[20] = "JuBlack";
        PIECE_BONES[21] = "PaoBlack";
        PIECE_BONES[22] = "ZuBlack";
    }

    private final Font font;
    private final BlockEntityRenderDispatcher dispatcher;
    private final RenderType boardRenderType;
    private final RenderType piecesRenderType;

    public TileEntityCChessRenderer(BlockEntityRendererProvider.Context context) {
        this.dispatcher = context.getBlockEntityRenderDispatcher();
        this.font = context.getFont();
        this.boardRenderType = RenderType.entityCutoutNoCull(TEXTURE);
        this.piecesRenderType = RenderType.entityCutoutNoCull(PIECES_TEXTURE);
    }

    @Override
    public void render(TileEntityCChess cchess, float partialTick, PoseStack poseStack, MultiBufferSource bufferIn,
                       int combinedLightIn, int combinedOverlayIn) {
        Direction facing = cchess.getBlockState().getValue(BlockCChess.FACING);
        this.renderChessboard(poseStack, bufferIn, combinedLightIn, combinedOverlayIn, facing);
        this.renderPiece(cchess, poseStack, bufferIn, combinedLightIn, combinedOverlayIn, facing);
        this.renderTipsText(cchess, poseStack, bufferIn, combinedLightIn);
    }

    private void renderChessboard(PoseStack poseStack, MultiBufferSource bufferIn,
                                  int combinedLightIn, int combinedOverlayIn, Direction facing) {
        poseStack.pushPose();
        poseStack.translate(0.5, 1.5, 0.5);
        // 基岩模型的 Y 轴朝上、原版实体模型的 Y 轴朝下，所以这里要比原版多翻一次
        poseStack.mulPose(Vector3f.ZN.rotationDegrees(180));
        poseStack.mulPose(Vector3f.YN.rotationDegrees(facing.get2DDataValue() * 90));
        if (facing == Direction.SOUTH || facing == Direction.NORTH) {
            poseStack.mulPose(Vector3f.YN.rotationDegrees(180));
        }
        GeoBlockRenderer.render(BedrockBlockModels.get("cchess"), TEXTURE, poseStack, bufferIn,
                combinedLightIn, combinedOverlayIn, boardRenderType);
        poseStack.popPose();
    }

    private void renderPiece(TileEntityCChess cchess, PoseStack poseStack, MultiBufferSource bufferIn,
                             int combinedLightIn, int combinedOverlayIn, Direction facing) {
        if (!inRenderDistance(cchess, PIECE_RENDER_DISTANCE)) {
            return;
        }
        AnimatedGeoModel pieces = BedrockBlockModels.get("cchess_pieces");
        if (pieces == null) {
            return;
        }
        int selectX = Position.FILE_X(cchess.getSelectChessPoint());
        int selectY = Position.RANK_Y(cchess.getSelectChessPoint());
        byte[] data = cchess.getChessData().squares;

        poseStack.pushPose();
        switch (facing) {
            case NORTH -> poseStack.translate(1.365 + 0.5, 1.625, 1.370 + 0.5);
            case EAST -> poseStack.translate(-1.365 + 0.5, 1.625, 1.370 + 0.5);
            case WEST -> poseStack.translate(1.365 + 0.5, 1.625, -1.370 + 0.5);
            default -> poseStack.translate(-1.365 + 0.5, 1.625, -1.370 + 0.5);
        }
        poseStack.mulPose(Vector3f.ZN.rotationDegrees(180));
        poseStack.mulPose(Vector3f.YN.rotationDegrees(facing.get2DDataValue() * 90));
        if (facing == Direction.SOUTH || facing == Direction.NORTH) {
            poseStack.mulPose(Vector3f.YN.rotationDegrees(180));
        }
        for (int y = Position.RANK_TOP; y <= Position.RANK_BOTTOM; y++) {
            for (int x = Position.FILE_LEFT; x <= Position.FILE_RIGHT; x++) {
                int index = Position.COORD_XY(x, y);
                byte piecesIndex = data[index];
                boolean hasPiece = index < data.length && (CChessUtil.isRed(piecesIndex) || CChessUtil.isBlack(piecesIndex));
                if (hasPiece && piecesIndex >= 0 && piecesIndex < PIECE_BONES.length) {
                    String bone = PIECE_BONES[piecesIndex];
                    if (bone != null) {
                        GeoBlockRenderer.renderBone(pieces, bone, PIECES_TEXTURE, poseStack, bufferIn,
                                combinedLightIn, combinedOverlayIn, piecesRenderType);
                    }
                    if (selectX == x && selectY == y) {
                        GeoBlockRenderer.renderBone(pieces, SELECTED_BONE, PIECES_TEXTURE, poseStack, bufferIn,
                                combinedLightIn, combinedOverlayIn, piecesRenderType);
                    }
                }
                poseStack.translate(0.304, 0, 0);
            }
            poseStack.translate(-0.304 * 9, 0, -0.304);
        }
        poseStack.popPose();
    }

    /**
     * 将死 / 长打 / 自然限着时浮在棋盘上方的提示文字。
     */
    private void renderTipsText(TileEntityCChess chess, PoseStack poseStack, MultiBufferSource bufferIn,
                                int combinedLightIn) {
        boolean showTips = chess.isCheckmate() || chess.isRepeat() || chess.isMoveNumberLimit();
        if (!showTips || !inRenderDistance(chess, TIPS_RENDER_DISTANCE)) {
            return;
        }

        Camera camera = this.dispatcher.camera;
        MutableComponent loseTips = null;
        MutableComponent resetTips = new TranslatableComponent("message.touhou_little_maid.cchess.reset")
                .withStyle(ChatFormatting.UNDERLINE).withStyle(ChatFormatting.AQUA);
        MutableComponent roundText = new TranslatableComponent("message.touhou_little_maid.gomoku.round", chess.getChessCounter())
                .withStyle(ChatFormatting.WHITE);
        MutableComponent preRoundIcon = new TextComponent("⏹ ").withStyle(ChatFormatting.GREEN);
        MutableComponent postRoundIcon = new TextComponent(" ⏹").withStyle(ChatFormatting.GREEN);
        MutableComponent roundTips = preRoundIcon.append(roundText).append(postRoundIcon);

        if (chess.isCheckmate()) {
            if (!chess.isPlayerTurn()) {
                loseTips = new TranslatableComponent("message.touhou_little_maid.gomoku.win")
                        .withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.DARK_PURPLE);
            } else {
                loseTips = new TranslatableComponent("message.touhou_little_maid.gomoku.lose")
                        .withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.DARK_PURPLE);
            }
        } else if (chess.isMoveNumberLimit()) {
            loseTips = new TranslatableComponent("message.touhou_little_maid.cchess.move_limit")
                    .withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.DARK_PURPLE);
        } else if (chess.isRepeat()) {
            loseTips = new TranslatableComponent("message.touhou_little_maid.cchess.repeat")
                    .withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.DARK_PURPLE);
        }
        if (loseTips == null) {
            return;
        }

        float loseTipsWidth = (float) (-this.font.width(loseTips) / 2);
        float resetTipsWidth = (float) (-this.font.width(resetTips) / 2);
        float roundTipsWidth = (float) (-this.font.width(roundTips) / 2);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.75, 0.5);
        // 文字永远正对镜头：先转回玩家视角的偏航，再补上俯仰
        poseStack.mulPose(Vector3f.YN.rotationDegrees(180 + camera.getYRot()));
        poseStack.mulPose(Vector3f.XN.rotationDegrees(camera.getXRot()));
        poseStack.scale(0.03F, -0.03F, 0.03F);
        this.font.drawInBatch(loseTips, loseTipsWidth, -10, 0xFFFFFF, true,
                poseStack.last().pose(), bufferIn, false, 0, combinedLightIn);
        poseStack.scale(0.5F, 0.5F, 0.5F);
        this.font.drawInBatch(roundTips, roundTipsWidth, -30, 0xFFFFFF, true,
                poseStack.last().pose(), bufferIn, false, 0, combinedLightIn);
        this.font.drawInBatch(resetTips, resetTipsWidth, 0, 0xFFFFFF, true,
                poseStack.last().pose(), bufferIn, false, 0, combinedLightIn);
        poseStack.popPose();
    }

    private boolean inRenderDistance(TileEntityCChess chess, int distance) {
        BlockPos pos = chess.getBlockPos();
        return this.dispatcher.camera.getPosition().distanceToSqr(pos.getX(), pos.getY(), pos.getZ())
                < (double) distance * distance;
    }

    @Override
    public boolean shouldRenderOffScreen(TileEntityCChess te) {
        return true;
    }
}

