package com.github.tartaricacid.touhoulittlemaid.client.renderer.ai;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.ChatText;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.MaidChatBubbles;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ParseI18n;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.apache.commons.lang3.tuple.Pair;
import java.util.List;
/**
 * 头顶气泡渲染器，按 <b>TLMO Fabric 1.20.1</b> 的样式重写。
 *
 * <h2>为什么要重写而不是继续沿用 1.18.2 的那套</h2>
 * 1.18.2 上游的气泡是「3×2 张 20×20 的小方格拼起来的」（{@code default1.png}），
 * 3. 文字不换行、4. 不做垂直居中，长句子会拉成一条横贯屏幕的白条。
 * 1.20.1 换成了<b>九宫格 + 自动换行</b>：
 * <ul>
 *     <li>贴图 {@code textures/entity/chat_bubble/type1.png}（256×256，实际内容在左上角
 *         48×24 的圆角矩形里，8 px 边距），用九宫格拉伸成任意大小；</li>
 *     <li>文字按 {@code font.split} 折行：居中气泡 200 px、左右两列 120 px；</li>
 *     <li>气泡整体尺寸 = 文字包围盒 + 四周 5 px 内边距。</li>
 * </ul>
 *
 * <h2>布局（与 1.20.1 {@code ChatBubbleRenderer} 逐项对齐）</h2>
 * <ul>
 *     <li>气泡按「右、左、右、左……」交替排布，偶数下标在右列、奇数下标在左列；</li>
 *     <li>每列向下堆叠，间距 16 px；</li>
 *     <li>总数为奇数时，最后一条居中，放在「左右两列里较长的那一列」的底部；</li>
 *     <li>左列气泡的尾巴从右侧伸出（贴图 u=32 那个箭头），右列从左侧伸出（u=0），
 *         居中的用中间那个（u=16）。</li>
 * </ul>
 *
 * <h2>坐姿 / 睡姿</h2>
 * 1.20.1 在女仆坐下时把气泡基线下移 0.25 格 —— 碰撞箱不变，但模型是蹲下去的，
 * 不下移就会顶在头发上。睡姿 1.18.2 上游是整体下移 48 px，这里换算成 0.9 格，
 * 让气泡底边落在与改造前相同的位置。
 *
 * <h2>顶点格式：这里踩过坑，别改回去</h2>
 * <p>上游自建了一种「关混合」的渲染类型，需要 Forge 的 AT 打开
 * {@code RenderStateShard} / {@code RenderType.create}。本移植版没有 AT，改用原版公开的
 * {@code RenderType.text}。它的顶点格式是 {@code POSITION_COLOR_TEX_LIGHTMAP}，
 * 四个元素缺一不可：只写 {@code vertex → color → uv} 会在
 * {@code BufferBuilder#endVertex} 抛 {@code Not filled all elements of the vertex}，
 * 表现就是「AI 一回复、头顶要出气泡，客户端立刻崩」。所以 {@link #vertex} 末尾必须补
 * {@code uv2}，这里固定给 {@link LightTexture#FULL_BRIGHT}：气泡不该随天黑变暗。</p>
 */
public final class ChatBubbleRenderer {
    /** 正文气泡背景（1.20.1 的 {@code IChatBubbleData#TYPE_1}）。 */
    public static final ResourceLocation TYPE_1 =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/entity/chat_bubble/type1.png");
    /** 内部提示气泡背景（1.20.1 的 {@code IChatBubbleData#TYPE_2}），保留给后续区分用。 */
    public static final ResourceLocation TYPE_2 =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/entity/chat_bubble/type2.png");
    private static final List<Pair<Long, ChatText>> TMP_CHAT_BUBBLES = Lists.newArrayList();
    /** 贴图实际尺寸（{@code blit} 的默认坐标系就是 256×256）。 */
    private static final int TEXTURE_SIZE = 256;
    /** 气泡主体在贴图里的位置与尺寸：左上角 48×24。 */
    private static final int BODY_U = 0;
    private static final int BODY_V = 0;
    private static final int BODY_WIDTH = 48;
    private static final int BODY_HEIGHT = 24;
    /** 九宫格边距。 */
    private static final int SLICE = 8;
    /** 三个尾巴在贴图里的位置：v 固定 24，u 分别是 0 / 16 / 32，每格 16×16。 */
    private static final int ARROW_V = 24;
    private static final int ARROW_SIZE = 16;
    private static final int ARROW_U_LEFT = 0;
    private static final int ARROW_U_MIDDLE = 16;
    private static final int ARROW_U_RIGHT = 32;
    /** 气泡内边距（1.20.1 的 5 px）。 */
    private static final int PADDING = 5;
    /** 一行文字的高度。 */
    private static final int LINE_HEIGHT = 9;
    /** 同一列里两个气泡之间的间距（1.20.1 的 16 px）。 */
    private static final int BUBBLE_GAP = 16;
    /** 换行宽度：居中 200 px，左右两列 120 px（1.20.1 的常量）。 */
    private static final int MAX_WIDTH_CENTER = 200;
    private static final int MAX_WIDTH_SIDE = 120;
    /** 与名牌相同的可见距离（64 格）。 */
    private static final double MAX_RENDER_DISTANCE_SQR = 64.0D * 64.0D;
    /** 坐下时气泡基线下移的格数（1.20.1 的 0.25）。 */
    private static final float SITTING_Y_OFFSET = 0.25F;
    /** 睡着时气泡基线下移的格数：48 px x 0.025 = 1.2 格，与改造前的行为完全一致。 */
    private static final float SLEEPING_Y_OFFSET = 1.2F;
    /** 姿态缩放：1 像素 = 0.025 格。 */
    private static final float PIXEL_SCALE = 0.025F;
    private ChatBubbleRenderer() {
    }
    /**
     * 在女仆头顶画所有未过期内容。
     *
     * @param distanceSqr 相机到实体的距离平方，由调用方算好传进来
     */
    public static void renderChatBubble(EntityMaid maid, PoseStack poseStack,
                                        MultiBufferSource buffer, int packedLight, double distanceSqr) {
        if (distanceSqr > MAX_RENDER_DISTANCE_SQR) {
            return;
        }
        if (!collectChatBubbles(maid)) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        float baseHeight = getBaseHeight(maid);
        int size = TMP_CHAT_BUBBLES.size();
        // 奇数个时，最后一条居中；其余按「右、左」交替两列堆叠
        int columnCount = isOdd(size) ? size - 1 : size;
        int rightY = 0;
        int leftY = 0;
        for (int i = 0; i < columnCount; i++) {
            Side side = isOdd(i) ? Side.LEFT : Side.RIGHT;
            Bubble bubble = Bubble.create(TMP_CHAT_BUBBLES.get(i).getRight(), side, font);
            if (side == Side.LEFT) {
                drawBubble(poseStack, buffer, packedLight, bubble, side, baseHeight, leftY);
                leftY += bubble.height() + BUBBLE_GAP;
            } else {
                drawBubble(poseStack, buffer, packedLight, bubble, side, baseHeight, rightY);
                rightY += bubble.height() + BUBBLE_GAP;
            }
        }
        if (isOdd(size)) {
            Bubble bubble = Bubble.create(TMP_CHAT_BUBBLES.get(size - 1).getRight(), Side.CENTER, font);
            drawBubble(poseStack, buffer, packedLight, bubble, Side.CENTER, baseHeight,
                    Math.max(leftY, rightY));
        }
    }
    /**
     * 气泡基线的世界高度：碰撞箱顶部再抬 0.75 格，按姿势微调。
     */
    private static float getBaseHeight(EntityMaid maid) {
        float height = maid.getBbHeight() + 0.75F;
        if (maid.isMaidInSittingPose()) {
            height -= SITTING_Y_OFFSET;
        }
        if (maid.isSleeping()) {
            height -= SLEEPING_Y_OFFSET;
        }
        return height;
    }
    /**
     * 收集未过期内容。过期判定必须在客户端也做一次：
     * 服务端每 tick 会清，但同步包有延迟，到期那一刻客户端手里还是旧值。
     */
    private static boolean collectChatBubbles(EntityMaid maid) {
        MaidChatBubbles chatBubble = maid.getChatBubble();
        TMP_CHAT_BUBBLES.clear();
        addIfAlive(TMP_CHAT_BUBBLES, chatBubble.getBubble1());
        addIfAlive(TMP_CHAT_BUBBLES, chatBubble.getBubble2());
        addIfAlive(TMP_CHAT_BUBBLES, chatBubble.getBubble3());
        return !TMP_CHAT_BUBBLES.isEmpty();
    }
    private static void addIfAlive(List<Pair<Long, ChatText>> out, Pair<Long, ChatText> bubble) {
        if (bubble == null || bubble == MaidChatBubbles.EMPTY) {
            return;
        }
        if (System.currentTimeMillis() > bubble.getLeft()) {
            return;
        }
        ChatText text = bubble.getRight();
        if (text == null || text == ChatText.EMPTY_CHAT_TEXT) {
            return;
        }
        if (!text.isText()) {
            // 图标气泡需要贴图缓存 SizeTexture，本轮未移植
            return;
        }
        out.add(bubble);
    }
    /**
     * 画一条气泡：九宫格背景 + 尾巴 + 多行文字。
     *
     * @param yOffset 同一列里已经堆了几个气泡，本气泡要往下让开多少像素
     */
    private static void drawBubble(PoseStack poseStack, MultiBufferSource buffer,
                                   int packedLight, Bubble bubble, Side side,
                                   float baseHeight, int yOffset) {
        int boxWidth = bubble.width() + PADDING * 2;
        int boxHeight = bubble.height() + PADDING * 2;
        int startX = switch (side) {
            case LEFT -> -1 - boxWidth;
            case RIGHT -> 1;
            case CENTER -> -boxWidth / 2;
        };
        int startY = -boxHeight;
        poseStack.pushPose();
        // 每 1 像素 = 0.025 格；yOffset 是向下的像素数，所以是减
        poseStack.translate(0, baseHeight - yOffset * PIXEL_SCALE, 0);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-PIXEL_SCALE, -PIXEL_SCALE, PIXEL_SCALE);
        VertexConsumer builder = buffer.getBuffer(RenderType.text(TYPE_1));
        drawNineSliced(poseStack, builder, startX, startY, boxWidth, boxHeight);
        int arrowU = switch (side) {
            case LEFT -> ARROW_U_RIGHT;
            case RIGHT -> ARROW_U_LEFT;
            case CENTER -> ARROW_U_MIDDLE;
        };
        int arrowX = switch (side) {
            case LEFT -> startX + boxWidth - ARROW_SIZE / 2;
            case RIGHT -> startX - ARROW_SIZE / 2;
            case CENTER -> -ARROW_SIZE / 2;
        };
        drawSprite(poseStack, builder, arrowX, -ARROW_SIZE / 2, ARROW_SIZE, ARROW_SIZE,
                arrowU, ARROW_V, ARROW_SIZE, ARROW_SIZE);
        Font font = Minecraft.getInstance().font;
        Matrix4f matrix = poseStack.last().pose();
        for (int i = 0; i < bubble.lines().size(); i++) {
            font.drawInBatch(bubble.lines().get(i), startX + PADDING, startY + PADDING + i * LINE_HEIGHT,
                    0xFF000000, false, matrix, buffer, false, 0, packedLight);
        }
        poseStack.popPose();
    }
    /**
     * 九宫格拉伸：四角原样、四条边单向拉伸、中间双向拉伸。
     *
     * <p>与 1.20.1 的 {@code GuiGraphics#blitNineSliced} 一致，边距会先被
     * {@code min(slice, size / 2)} 夹一次 —— 气泡很小时（一个字符）不会画出负宽度。</p>
     */
    private static void drawNineSliced(PoseStack poseStack, VertexConsumer builder,
                                       int x, int y, int width, int height) {
        int left = Math.min(SLICE, width / 2);
        int right = Math.min(SLICE, width / 2);
        int top = Math.min(SLICE, height / 2);
        int bottom = Math.min(SLICE, height / 2);
        int middleSourceWidth = BODY_WIDTH - left - right;
        int middleSourceHeight = BODY_HEIGHT - top - bottom;
        int middleWidth = width - left - right;
        int middleHeight = height - top - bottom;
        int u0 = BODY_U;
        int v0 = BODY_V;
        int u1 = BODY_U + BODY_WIDTH - right;
        int v1 = BODY_V + BODY_HEIGHT - bottom;
        // 四角：源与目标同尺寸
        drawSprite(poseStack, builder, x, y, left, top, u0, v0, left, top);
        drawSprite(poseStack, builder, x + width - right, y, right, top, u1, v0, right, top);
        drawSprite(poseStack, builder, x, y + height - bottom, left, bottom, u0, v1, left, bottom);
        drawSprite(poseStack, builder, x + width - right, y + height - bottom, right, bottom,
                u1, v1, right, bottom);
        // 上下两条边：只横向拉伸
        drawSprite(poseStack, builder, x + left, y, middleWidth, top,
                u0 + left, v0, middleSourceWidth, top);
        drawSprite(poseStack, builder, x + left, y + height - bottom, middleWidth, bottom,
                u0 + left, v1, middleSourceWidth, bottom);
        // 左右两条边：只纵向拉伸
        drawSprite(poseStack, builder, x, y + top, left, middleHeight,
                u0, v0 + top, left, middleSourceHeight);
        drawSprite(poseStack, builder, x + width - right, y + top, right, middleHeight,
                u1, v0 + top, right, middleSourceHeight);
        // 中央：双向拉伸
        drawSprite(poseStack, builder, x + left, y + top, middleWidth, middleHeight,
                u0 + left, v0 + top, middleSourceWidth, middleSourceHeight);
    }
    /**
     * 从贴图上取一块 {@code sourceWidth × sourceHeight} 的矩形，拉伸绘制成
     * {@code width × height}。
     *
     * <p>UV 用贴图实际尺寸归一化（1.20.1 的 {@code blit} 默认按 256×256 处理，
     * 这里显式写出来，免得以后换贴图尺寸时算错）。</p>
     */
    private static void drawSprite(PoseStack poseStack, VertexConsumer builder,
                                   int x, int y, int width, int height,
                                   int u, int v, int sourceWidth, int sourceHeight) {
        float u0 = u / (float) TEXTURE_SIZE;
        float u1 = (u + sourceWidth) / (float) TEXTURE_SIZE;
        float v0 = v / (float) TEXTURE_SIZE;
        float v1 = (v + sourceHeight) / (float) TEXTURE_SIZE;
        Matrix4f matrix = poseStack.last().pose();
        vertex(matrix, builder, x, y + height, u0, v1);
        vertex(matrix, builder, x + width, y + height, u1, v1);
        vertex(matrix, builder, x + width, y, u1, v0);
        vertex(matrix, builder, x, y, u0, v0);
    }
    /**
     * {@code RenderType.text} 的顶点格式是 {@code POSITION_COLOR_TEX_LIGHTMAP}，
     * 顺序 position → color → uv0 → uv2，四个都要写，少一个就抛
     * {@code Not filled all elements of the vertex}。
     */
    private static void vertex(Matrix4f matrix4f, VertexConsumer builder, float x, float y,
                               float u, float v) {
        builder.vertex(matrix4f, x, y, 0.2F)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .uv2(LightTexture.FULL_BRIGHT)
                .endVertex();
    }
    private static boolean isOdd(int value) {
        return value % 2 != 0;
    }
    /** 气泡摆在女仆哪一侧。 */
    private enum Side {
        LEFT, RIGHT, CENTER
    }
    /**
     * 一条折好行的气泡：行列表 + 文字包围盒。
     */
    private record Bubble(List<FormattedCharSequence> lines, int width, int height) {
        private static Bubble create(ChatText chatText, Side side, Font font) {
            Component text = ParseI18n.parse(chatText.getText());
            int maxWidth = side == Side.CENTER ? MAX_WIDTH_CENTER : MAX_WIDTH_SIDE;
            List<FormattedCharSequence> lines = font.split(text, maxWidth);
            if (lines.isEmpty()) {
                lines = Lists.newArrayList(FormattedCharSequence.EMPTY);
            }
            int width = 0;
            for (FormattedCharSequence line : lines) {
                width = Math.max(width, font.width(line));
            }
            return new Bubble(lines, width, lines.size() * LINE_HEIGHT);
        }
    }
}
