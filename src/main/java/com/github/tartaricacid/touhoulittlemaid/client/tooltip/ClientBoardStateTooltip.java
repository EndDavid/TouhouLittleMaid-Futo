package com.github.tartaricacid.touhoulittlemaid.client.tooltip;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.GomokuCodec;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Point;
import com.github.tartaricacid.touhoulittlemaid.inventory.tooltip.BoardStateTooltip;
import com.github.tartaricacid.touhoulittlemaid.util.CChessUtil;
import com.github.tartaricacid.touhoulittlemaid.util.WChessUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 残局道具的「棋盘缩略图」tooltip，移植自上游 1.20.1 的
 * {@code client/tooltip/ClientBoardStateTooltip}。
 *
 * <h2>1.18.2 的接口差别（三处）</h2>
 * <ol>
 *     <li>{@code renderImage} 的签名是
 *         {@code (Font, int, int, PoseStack, ItemRenderer, int blitOffset)}，
 *         没有 1.20 那个 {@code GuiGraphics}。</li>
 *     <li>所以贴图要靠 {@link RenderSystem#setShaderTexture} 绑定，
 *         再用 {@link GuiComponent#blit} 画 —— 1.18.2 的 {@code blit} 是
 *         {@code GuiComponent} 上的静态方法。</li>
 *     <li>注册也不一样：Forge 是 {@code RegisterClientTooltipComponentFactoriesEvent}，
 *         Fabric 是 {@code TooltipComponentCallback}（见 {@code InitClientTooltip}）。</li>
 * </ol>
 *
 * <h2>解码结果为什么要缓存</h2>
 * <p>tooltip 每帧都会重建一次：{@code getHeight} / {@code getWidth} / {@code renderImage}
 * 各自被调用，而五子棋那串数据要 Base64 解码 + Inflate + 建 15×15 数组。
 * 上游用 {@code Util.memoize} 缓存「最后一次的 (type, data)」，
 * 这里照抄 —— 同一个道具在屏幕上停留期间只会解码一次。</p>
 */
public class ClientBoardStateTooltip implements ClientTooltipComponent {
    private static final ResourceLocation GOMOKU_BG =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/gomoku.png");
    private static final ResourceLocation XIANGQI_BG =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/xiangqi.png");
    private static final ResourceLocation CHESS_BG =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/gui/chess.png");

    /**
     * 棋盘贴图尺寸：三张图都按「原尺寸」画，宽高各不相同。
     */
    private static final int GOMOKU_SIZE = 151;
    private static final int XIANGQI_WIDTH = 128;
    private static final int XIANGQI_HEIGHT = 126;
    private static final int CHESS_SIZE = 204;

    /**
     * 「最后一对 (type, data) -> 解码结果」的单槽缓存（与上游的 memoize 等价）。
     */
    private static String cachedType;
    private static String cachedData;
    private static Object cachedBoard;

    private final String type;
    private final Object boardGameData;

    public ClientBoardStateTooltip(BoardStateTooltip tooltip) {
        this.type = tooltip.type();
        this.boardGameData = decode(this.type, tooltip.stateData());
    }

    @Nullable
    private static synchronized Object decode(String type, String stateData) {
        if (stateData == null) {
            return null;
        }
        if (stateData.equals(cachedData) && type.equals(cachedType)) {
            return cachedBoard;
        }
        Object board = null;
        try {
            switch (type) {
                case BoardStateTooltip.GOMOKU ->
                        board = GomokuCodec.decode(stateData).board();
                case BoardStateTooltip.XIANGQI -> {
                    com.github.tartaricacid.touhoulittlemaid.api.game.xqwlight.Position position =
                            new com.github.tartaricacid.touhoulittlemaid.api.game.xqwlight.Position();
                    position.fromFen(stateData);
                    board = position.squares;
                }
                case BoardStateTooltip.CHESS -> {
                    com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position position =
                            new com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position();
                    position.fromFen(stateData);
                    board = position.squares;
                }
                default -> {
                }
            }
        } catch (Exception e) {
            // 数据包写坏 / 物品 NBT 被改过：当��「这张道具没有棋局」，只显示文字
            board = null;
        }
        cachedType = type;
        cachedData = stateData;
        cachedBoard = board;
        return board;
    }

    @Override
    public int getHeight() {
        if (this.boardGameData == null) {
            return 0;
        }
        return switch (this.type) {
            case BoardStateTooltip.GOMOKU -> 80;
            case BoardStateTooltip.XIANGQI -> 132;
            case BoardStateTooltip.CHESS -> 108;
            default -> 0;
        };
    }

    @Override
    public int getWidth(Font font) {
        if (this.boardGameData == null) {
            return 0;
        }
        return switch (this.type) {
            case BoardStateTooltip.GOMOKU -> 76;
            case BoardStateTooltip.XIANGQI -> 128;
            case BoardStateTooltip.CHESS -> 102;
            default -> 0;
        };
    }

    @Override
    public void renderImage(Font font, int pX, int pY, PoseStack poseStack,
                            ItemRenderer itemRenderer, int blitOffset) {
        if (this.boardGameData == null) {
            return;
        }
        // 照抄原版 {@code ClientBundleTooltip#blit} 的前两行：
        // tooltip 渲染阶段已经开了混合与正确的着色器，
        // 但上一个组件可能把 shader 颜色改成别的值，所以这里重置一次。
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (this.type.equals(BoardStateTooltip.GOMOKU) && this.boardGameData instanceof int[][] data) {
            this.renderGomoku(pX, pY, poseStack, blitOffset, data);
            return;
        }
        if (this.type.equals(BoardStateTooltip.XIANGQI) && this.boardGameData instanceof byte[] data) {
            this.renderXiangqi(pX, pY, poseStack, blitOffset, data);
            return;
        }
        if (this.type.equals(BoardStateTooltip.CHESS) && this.boardGameData instanceof byte[] data) {
            this.renderChess(pX, pY, poseStack, blitOffset, data);
        }
    }

    /**
     * 五子棋：整盘 0.5 倍缩放画一张 151×151 的底图，再逐子贴 9×9 的圆点。
     *
     * <p>底图的下半部分（v 151 起）就是黑白两色的棋子贴图，所以不需要第二张图。</p>
     */
    private void renderGomoku(int pX, int pY, PoseStack poseStack, int blitOffset, int[][] data) {
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 1);
        RenderSystem.setShaderTexture(0, GOMOKU_BG);
        GuiComponent.blit(poseStack, pX, pY, blitOffset, 0, 0, GOMOKU_SIZE, GOMOKU_SIZE, GOMOKU_SIZE, GOMOKU_SIZE);

        for (int y = 0; y <= 14; y++) {
            for (int x = 0; x <= 14; x++) {
                int piecesIndex = data[x][y];
                int v;
                if (piecesIndex == Point.BLACK) {
                    v = 151;
                } else if (piecesIndex == Point.WHITE) {
                    v = 160;
                } else {
                    continue;
                }
                int offsetX = pX + 1 + x * 10;
                int offsetY = pY + 1 + y * 10;
                GuiComponent.blit(poseStack, offsetX, offsetY, blitOffset, 0, v, 9, 9, GOMOKU_SIZE, GOMOKU_SIZE);
            }
        }
        poseStack.popPose();
    }

    /**
     * 中国象棋：底图右侧与下方就是棋子贴图条，按「红 8~14 / 黑 16~22」取 11×11 的小块。
     */
    private void renderXiangqi(int pX, int pY, PoseStack poseStack, int blitOffset, byte[] data) {
        RenderSystem.setShaderTexture(0, XIANGQI_BG);
        GuiComponent.blit(poseStack, pX, pY, blitOffset, 0, 0,
                XIANGQI_WIDTH, XIANGQI_HEIGHT, XIANGQI_WIDTH, XIANGQI_HEIGHT);

        for (int y = 3; y <= 12; y++) {
            for (int x = 3; x <= 11; x++) {
                byte piecesIndex = CChessUtil.piecesIndex(x, y, data);
                int u;
                int v;
                if (CChessUtil.isRed(piecesIndex)) {
                    u = (piecesIndex - 8) * 11;
                    v = 126;
                } else if (CChessUtil.isBlack(piecesIndex)) {
                    u = (piecesIndex - 16) * 11;
                    v = 137;
                } else {
                    continue;
                }
                int offsetX = pX - 1 + (x - 3) * 13;
                int offsetY = pY - 1 + (y - 3) * 13;
                GuiComponent.blit(poseStack, offsetX, offsetY, blitOffset, u, v, 11, 11,
                        XIANGQI_WIDTH, XIANGQI_HEIGHT);
            }
        }
    }

    /**
     * 国际象棋：底图下方两条 24×24 的棋子贴图，8×8 的格子从 (6, 6) 起。
     */
    private void renderChess(int pX, int pY, PoseStack poseStack, int blitOffset, byte[] data) {
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 1);
        RenderSystem.setShaderTexture(0, CHESS_BG);
        GuiComponent.blit(poseStack, pX, pY, blitOffset, 0, 0, CHESS_SIZE, CHESS_SIZE, CHESS_SIZE, CHESS_SIZE);

        for (int y = 0; y <= 7; y++) {
            for (int x = 4; x <= 11; x++) {
                byte piecesIndex = WChessUtil.piecesIndex(x, y, data);
                int u;
                int v;
                if (WChessUtil.isWhite(piecesIndex)) {
                    u = (piecesIndex - 8) * 24;
                    v = 204;
                } else if (WChessUtil.isBlack(piecesIndex)) {
                    u = (piecesIndex - 16) * 24;
                    v = 228;
                } else {
                    continue;
                }
                int offsetX = pX + 6 + (x - 4) * 24;
                int offsetY = pY + 6 + y * 24;
                GuiComponent.blit(poseStack, offsetX, offsetY, blitOffset, u, v, 24, 24, CHESS_SIZE, CHESS_SIZE);
            }
        }
        poseStack.popPose();
    }
}

