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
 * （{@code translate(0.5, boardLift(), 0.5)}，见 {@link #boardLift()} 为什么是 0），
 * 每个交叉点间隔 0.304 格 —— 与 {@code CChessUtil#getClickPosition} 里那两个魔数是同一组数字，
 * 改一个就必须改另一个，否则「看得见的棋子」和「点得到的格子」会错位。</p>
 *
 * <h2>棋子上的字</h2>
 * <p>字不是贴图贴在柱身上，而是每个棋子骨骼里额外带一个 {@code size [64, 0, 64]} 的
 * <b>平面</b> cube，box-UV 的 up 面正好框住贴图里的一整字。这个 cube 的
 * {@code inflate} 是负的，套通用公式会把区间算反、变成一根三格多的柱子 —— 
 * 处理方式见 {@code GeoCube#createFromPojoCube} 的注释。</p>
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
     * 棋盘本地网格的间距（格）。与 {@code CChessUtil#getClickPosition} 里的 0.304 是同一个数。
     */
    private static final float GRID_STEP = 0.304F;

    /**
     * 第一个交叉点（{@code FILE_LEFT} / {@code RANK_TOP}）在方块中心的本地偏移。
     *
     * <p>同样取自点击数学：它把点击点 {@code x} 映射成 {@code (x + 1.365) / 0.304}，
     * 所以 {@code x = -1.365} 正好是第一条线。两者必须同源，否则棋子会整体偏移。</p>
     */
    private static final float GRID_ORIGIN_X = -1.365F;
    private static final float GRID_ORIGIN_Z = -1.370F;

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

    /**
     * 只打印一次实测数值，方便核对摆放（诊断用，不影响行为）。
     */
    private static boolean logged = false;

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
        // 竖直位置：模型自己的原点就在方块底面上（见 {@link #boardLift()} 为何是 0）。
        poseStack.translate(0.5, boardLift(), 0.5);
        // 朝向：必须与上游逐字一致，见 {@link #applyFacing}。
        applyFacing(poseStack, facing);
        GeoBlockRenderer.render(BedrockBlockModels.get("cchess"), TEXTURE, poseStack, bufferIn,
                combinedLightIn, combinedOverlayIn, boardRenderType);
        poseStack.popPose();
    }

    /**
     * 棋盘与棋子共用的姿态。上游
     * 1.18.2 与 1.20.1 两份渲染器在这一段上
     * 是逐字相同的：
     *
     * <pre>
     * mulPose(ZN(180));
     * mulPose(YN(facing.toYRot()));
     * if (facing == NORTH || facing == SOUTH) mulPose(YN(180));
     * </pre>
     *
     * <h2>为什么这里写的是 {@code YP}</h2>
     * <p>那句 {@code ZN(180)} 是为了把原版
     * {@code ModelPart} 的 <b>Y 向下</b> 翻成 Y 向上
     * ，而它顺带着把 x 也取了负。
     * 本移植版画的基岩几何（{@code GeoCube}）
     * 本来就是 Y 向上，而且
     * {@code GeoCube#createFromPojoCube}里<b>已经把 x 取过
     * 一次负</b>。把 {@code M = Z·R(-θ)} 用
     * {@code Z} 共轭到基岩空间就是
     * {@code Z·R(-θ)·Z = R(θ)}，所以这里写
     * {@code YP(θ)}。</p>
     *
     * <p>写成 {@code YN(θ)} 在 NORTH / SOUTH 下看不出
     * 差别（±180° 互为逆），但在
     * EAST / WEST 会差整整半圈：点击命中的
     * 格子和看到的棋子就对不上。</p>
     *
     * <h2>为什么 NORTH / SOUTH 还要再 180°</h2>
     * <p>上游就有这一句。贴图里格线在
     * u 上的单边留白是 5 / 19 像素（不对称
     * ），少了这半圈整块棋盘就会偏出
     * <b>整整一格</b>，重开区也会跑到
     * 另一侧——「右边一列棋子不在
     * 格线上」与「字也是倒的」能同时
     * 出现，就是这个原因。</p>
     */
    private static void applyFacing(PoseStack poseStack, Direction facing) {
        // 实现在 BoardRenderUtil —— 三块棋盘共用一份，避免某一处悄悄改错。
        // 那两行各自为什么这么写（为什么是 YP 而不是 YN、为什么正统朝要补半圈），
        // 全部记在 BoardRenderUtil#applyFacing 的注释里。
        BoardRenderUtil.applyFacing(poseStack, facing);
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
        // 棋子的摆法必须量出来，不能读 JSON 里的 origin：
        // 一个棋子是 4 圈各 8 个带角度的小方块绕出来的「柱身」，加上顶部一张平贴的字，
        // 每个 cube 的 origin 都在很歪的位置（还带旋转），
        // 只有走一遍真实渲染路径才知道它到底多高、底面在哪。见 GeoBlockRenderer#measureBone。
        float[] pb = GeoBlockRenderer.measureBone(pieces, "ShuaiRed");
        if (pb == null) {
            return;
        }
        // 棋子中心（用于把棋子摆到交叉点上）
        float pieceCenterX = (pb[0] + pb[3]) * 0.5F;
        float pieceCenterZ = (pb[2] + pb[5]) * 0.5F;
        // 不做翻转，所以棋子底面就在 minY，抬 -minY 让底面落在原点平面上
        float pieceLift = -pb[1];
        // 棋盘表面离方块底面的高度
        float surface = boardTopY();
        if (!logged) {
            logged = true;
            float[] bb = BedrockBlockModels.bounds("cchess");
            AnimatedGeoModel board = BedrockBlockModels.get("cchess");
            TouhouLittleMaid.LOGGER.info(
                    "[Futo] chess layout: board bbox={} lift={} surface={} | piece bbox={} center=({}, {}) lift={}",
                    bb == null ? "null" : java.util.Arrays.toString(bb), boardLift(), surface,
                    java.util.Arrays.toString(pb), pieceCenterX, pieceCenterZ, pieceLift);
            // 两块「重开」竖板的位置：isClickResetArea 认为它们在 x 1.199..1.431、
            // z 0.464..1.061 与 -1.061..-0.464（关于 z=0 对称）。这里打出来对照。
            for (String boneName : new String[]{"main", "bone", "bone2"}) {
                float[] mb = GeoBlockRenderer.measureBone(board, boneName);
                TouhouLittleMaid.LOGGER.info("[Futo] chess bone {} -> {}", boneName,
                        mb == null ? "null" : java.util.Arrays.toString(mb));
            }
        }
        int selectX = Position.FILE_X(cchess.getSelectChessPoint());
        int selectY = Position.RANK_Y(cchess.getSelectChessPoint());
        byte[] data = cchess.getChessData().squares;

        // 不再从某个角开始逐个步进：那样需要 1.365 / 1.370 / 0.304 三个魔数
        // 同时对上棋盘朝向，任何一个偏了整盘棋子都会整体歪掉。
        // 改成「每个交叉点直接算出它在方块内的本地坐标」，坐标公式与
        // {@code CChessUtil#getClickPosition} 完全互为逆运算：
        //     clickPos.x -> raw = (x + 1.365) / 0.304 -> file = raw + FILE_LEFT
        // 反过来就是下面的 localX / localZ。这样棋子永远落在「点得到」的位置上。
        poseStack.pushPose();
        poseStack.translate(0.5, surface, 0.5);
        // 和棋盘用同一个姿态，否则字会和格线相差 180°。
        applyFacing(poseStack, facing);
        for (int y = Position.RANK_TOP; y <= Position.RANK_BOTTOM; y++) {
            for (int x = Position.FILE_LEFT; x <= Position.FILE_RIGHT; x++) {
                int index = Position.COORD_XY(x, y);
                byte piecesIndex = data[index];
                boolean hasPiece = index < data.length && (CChessUtil.isRed(piecesIndex) || CChessUtil.isBlack(piecesIndex));
                if (hasPiece && piecesIndex >= 0 && piecesIndex < PIECE_BONES.length) {
                    float localX = GRID_ORIGIN_X + (x - Position.FILE_LEFT) * GRID_STEP;
                    float localZ = GRID_ORIGIN_Z + (y - Position.RANK_TOP) * GRID_STEP;
                    // 格点坐标是「点击坐标系」里的数值，而 {@link #applyFacing} 对四个朝向都等于 {@code R(180)·R(-θ)}，
                    // 比点击坐标系多转了半圈 —— 所以格点坐标取负才是这个姿态下的本地坐标。
                    // 中心偏移也跟着取负，否则不对称的模型会被推开两倍偏移量。
                    float renderX = pieceCenterX - localX;
                    float renderZ = pieceCenterZ - localZ;
                    String bone = PIECE_BONES[piecesIndex];
                    if (bone != null) {
                        poseStack.pushPose();
                        poseStack.translate(renderX, pieceLift, renderZ);
                        GeoBlockRenderer.renderBone(pieces, bone, PIECES_TEXTURE, poseStack, bufferIn,
                                combinedLightIn, combinedOverlayIn, piecesRenderType);
                        poseStack.popPose();
                    }
                    if (selectX == x && selectY == y) {
                        poseStack.pushPose();
                        poseStack.translate(renderX, pieceLift, renderZ);
                        GeoBlockRenderer.renderBone(pieces, SELECTED_BONE, PIECES_TEXTURE, poseStack, bufferIn,
                                combinedLightIn, combinedOverlayIn, piecesRenderType);
                        poseStack.popPose();
                    }
                }
            }
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

    /**
     * 棋盘「底面贴地」所需的竖直位移 —— 这里是 0，模型自己就摆在方块底面。
     *
     * <p>不要照抄祭坛 / 神社那种 {@code -minY} 的写法。{@code cchess.json} 的最低点是
     * {@code -0.121875}，但那是两侧带字的小挡板（bone / bone2）故意垂到地下的部分 —— 
     * 上游 1.18.2 的手写模型 {@code CChessModel} 展开后，这两块挡板的世界坐标同样是
     * {@code -0.128..0.122}，而主棋盘正好是 {@code 0..0.125}。</p>
     *
     * <p>也就是说模型的原点已经对齐方块底面，抬 {@code -minY} 会让整张桌子高
     * {@code 0.121875} 格 ≈ 2 像素 —— 这正是之前看到的「整体高两像素」。</p>
     */
    private static float boardLift() {
        return 0.0F;
    }

    /**
     * 棋盘表面离方块底面的高度（棋子要贴在这个高度上）。
     *
     * <p>取模型自身的 {@code maxY}（= 0.128125）而不是主棋盘盒的 0.125：多出的
     * 0.003 正是两侧挡板的高出量。让棋子比板面高那么一丝，既看不出（0.05 像素），
     * 又顺带避开了与板面完全共面的 z-fighting。</p>
     */
    private static float boardTopY() {
        float[] b = BedrockBlockModels.bounds("cchess");
        return b == null ? 0.0F : b[4];
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
