package com.github.tartaricacid.touhoulittlemaid.client.renderer.ai;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.ChatBubbleScripts;
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
 * 头顶气泡渲染器，按 <b>TLMO Fabric 1.20.1</b> 的九宫格样式绘制，支持
 * <b>文本</b>与<b>表情图标</b>两类内容。
 *
 * <h2>内容类型</h2>
 * <ul>
 *     <li>文本：{@code chat_text} / 台本里的普通句子 / {@code {翻译键}}；</li>
 *     <li>图标：台本里写成 {@code %命名空间:贴图路径%} 的条目。贴图是
 *         <b>60×20 一帧、竖直堆叠</b>的多帧动画（{@code idle1.png} = 8 帧），
 *         每 2 tick 换一帧，与上游 {@code (tickCount / 2) % count} 一致。</li>
 * </ul>
 *
 * <h2>贴图</h2>
 * <p>底图由 {@link ChatBubbleScripts#getBackground(String)} 按模型 id 决定：
 * 台本里写的若是 1.18.2 那三张 60×40 旧网格图，就换成 1.20.1 的
 * {@code type2.png}（白底 + 淡蓝边，九宫格）；其它自定义贴图原样使用。
 * 九宫格边距按 1.20.1 的 {@code blitNineSliced}：四角原样、四边单向、中间双向。</p>
 *
 * <h2>布局（与 1.20.1 {@code ChatBubbleRenderer} 逐项对齐）</h2>
 * <ul>
 *     <li>气泡按「右、左、右、左……」交替排布；</li>
 *     <li>每列向下堆叠，间距 16 px；</li>
 *     <li>总数为奇数时最后一条居中，放在「左右两列里较长的那一列」的底部；</li>
 *     <li>尾巴：左列从右侧伸出、右列从左侧伸出、居中用中间那个。</li>
 * </ul>
 *
 * <h2>坐姿 / 睡姿</h2>
 * <p>坐下时基线下移 0.25 格（1.20.1 {@code EntityMaidRenderer} 里那一段）；
 * 睡着时下移 1.2 格（48 px × 0.025，与 1.18.2 上游一致）。</p>
 *
 * <h2>顶点格式：这里踩过坑，别改回去</h2>
 * <p>本移植版没有 AT，无法自建渲染类型，改用原版公开的 {@code RenderType.text}。
 * 它的顶点格式是 {@code POSITION_COLOR_TEX_LIGHTMAP}，四个元素缺一不可：
 * 只写 {@code vertex → color → uv} 会在 {@code BufferBuilder#endVertex} 抛
 * {@code Not filled all elements of the vertex}，表现就是「一冒气泡客户端立刻崩」。
 * 所以 {@link #vertex} 末尾必须补 {@code uv2}，固定给
 * {@link LightTexture#FULL_BRIGHT}：气泡不该随天黑变暗。</p>
 */
public final class ChatBubbleRenderer {
    /** 现代九宫格底图（1.20.1 的 {@code IChatBubbleData#TYPE_2}）：白底 + 淡蓝边。 */
    public static final ResourceLocation TYPE_2 =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/entity/chat_bubble/type2.png");
    /** 1.18.2 的正文底图（白底 + 粗黑边），保留常量供台本判断，不再直接绘制。 */
    public static final ResourceLocation TYPE_1 =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/entity/chat_bubble/type1.png");
    private static final List<Pair<Long, ChatText>> TMP_CHAT_BUBBLES = Lists.newArrayList();
    /** 底图主体在贴图里的位置与尺寸：左上角 48×24（1.20.1 的 type2.png）。 */
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
    /** 睡着时气泡基线下移的格数：48 px × 0.025 = 1.2 格。 */
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
        // 底图与贴图尺寸按模型 id 取一次，所有气泡共用
        ResourceLocation background = ChatBubbleScripts.getBackground(maid.getModelId());
        int[] backgroundSize = IconTextureSizes.get(background);
        int size = TMP_CHAT_BUBBLES.size();
        int columnCount = isOdd(size) ? size - 1 : size;
        int rightY = 0;
        int leftY = 0;
        for (int i = 0; i < columnCount; i++) {
            Side side = isOdd(i) ? Side.LEFT : Side.RIGHT;
            Bubble bubble = Bubble.create(TMP_CHAT_BUBBLES.get(i).getRight(), side, font, maid.tickCount);
            if (side == Side.LEFT) {
                drawBubble(poseStack, buffer, packedLight, bubble, side, baseHeight, leftY,
                        background, backgroundSize);
                leftY += bubble.height() + BUBBLE_GAP;
            } else {
                drawBubble(poseStack, buffer, packedLight, bubble, side, baseHeight, rightY,
                        background, backgroundSize);
                rightY += bubble.height() + BUBBLE_GAP;
            }
        }
        if (isOdd(size)) {
            Bubble bubble = Bubble.create(TMP_CHAT_BUBBLES.get(size - 1).getRight(), Side.CENTER, font, maid.tickCount);
            drawBubble(poseStack, buffer, packedLight, bubble, Side.CENTER, baseHeight,
                    Math.max(leftY, rightY), background, backgroundSize);
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
        if (!text.isText() && !text.isIcon()) {
            return;
        }
        out.add(bubble);
    }
    /**
     * 画一条气泡：九宫格背景 + 尾巴 + 内容（多行文字或表情图标）。
     *
     * @param yOffset 同一列里已经堆了几个气泡，本气泡要往下让开多少像素
     */
    private static void drawBubble(PoseStack poseStack, MultiBufferSource buffer,
                                   int packedLight, Bubble bubble, Side side,
                                   float baseHeight, int yOffset,
                                   ResourceLocation background, int[] backgroundSize) {
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
        VertexConsumer builder = buffer.getBuffer(RenderType.text(background));
        drawNineSliced(poseStack, builder, startX, startY, boxWidth, boxHeight, backgroundSize);
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
                arrowU, ARROW_V, ARROW_SIZE, ARROW_SIZE, backgroundSize);
        if (bubble.icon() != null) {
            drawIcon(poseStack, buffer, bubble, startX + PADDING, startY + PADDING);
        } else {
            drawText(poseStack, buffer, packedLight, bubble, startX + PADDING, startY + PADDING);
        }
        poseStack.popPose();
    }
    private static void drawText(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                 Bubble bubble, int textX, int textY) {
        Font font = Minecraft.getInstance().font;
        Matrix4f matrix = poseStack.last().pose();
        for (int i = 0; i < bubble.lines().size(); i++) {
            font.drawInBatch(bubble.lines().get(i), textX, textY + i * LINE_HEIGHT,
                    0xFF000000, false, matrix, buffer, false, 0, packedLight);
        }
    }
    /**
     * 画表情图标。
     *
     * <p>贴图与气泡底图不是同一张，必须换一个 {@code RenderType.text(icon)} 分批 ——
     * 它的顶点格式与底图那条完全相同，所以顶点写法不变。</p>
     *
     * <p>帧选择：{@code (tickCount / 2) % frames}，与上游一致；单帧图直接整张画。</p>
     */
    private static void drawIcon(PoseStack poseStack, MultiBufferSource buffer,
                                 Bubble bubble, int iconX, int iconY) {
        ResourceLocation icon = bubble.icon();
        int frames = bubble.iconFrames();
        VertexConsumer iconBuilder = buffer.getBuffer(RenderType.text(icon));
        float v0 = 0.0F;
        float v1 = 1.0F;
        if (frames > 1) {
            v0 = 1.0F / frames * bubble.iconFrame();
            v1 = 1.0F / frames * (bubble.iconFrame() + 1);
        }
        int width = bubble.width();
        int height = bubble.height();
        Matrix4f matrix = poseStack.last().pose();
        vertex(matrix, iconBuilder, iconX, iconY + height, 0.0F, v1);
        vertex(matrix, iconBuilder, iconX + width, iconY + height, 1.0F, v1);
        vertex(matrix, iconBuilder, iconX + width, iconY, 1.0F, v0);
        vertex(matrix, iconBuilder, iconX, iconY, 0.0F, v0);
    }
    /**
     * 九宫格拉伸：四角原样、四条边单向拉伸、中间双向拉伸。
     *
     * <p>与 1.20.1 的 {@code GuiGraphics#blitNineSliced} 一致，边距会先被
     * {@code min(slice, size / 2)} 夹一次 —— 气泡很小时（一个字符）不会画出负宽度。</p>
     */
    private static void drawNineSliced(PoseStack poseStack, VertexConsumer builder,
                                       int x, int y, int width, int height, int[] textureSize) {
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
        drawSprite(poseStack, builder, x, y, left, top, u0, v0, left, top, textureSize);
        drawSprite(poseStack, builder, x + width - right, y, right, top, u1, v0, right, top, textureSize);
        drawSprite(poseStack, builder, x, y + height - bottom, left, bottom,
                u0, v1, left, bottom, textureSize);
        drawSprite(poseStack, builder, x + width - right, y + height - bottom, right, bottom,
                u1, v1, right, bottom, textureSize);
        // 上下两条边：只横向拉伸
        drawSprite(poseStack, builder, x + left, y, middleWidth, top,
                u0 + left, v0, middleSourceWidth, top, textureSize);
        drawSprite(poseStack, builder, x + left, y + height - bottom, middleWidth, bottom,
                u0 + left, v1, middleSourceWidth, bottom, textureSize);
        // 左右两条边：只纵向拉伸
        drawSprite(poseStack, builder, x, y + top, left, middleHeight,
                u0, v0 + top, left, middleSourceHeight, textureSize);
        drawSprite(poseStack, builder, x + width - right, y + top, right, middleHeight,
                u1, v0 + top, right, middleSourceHeight, textureSize);
        // 中央：双向拉伸
        drawSprite(poseStack, builder, x + left, y + top, middleWidth, middleHeight,
                u0 + left, v0 + top, middleSourceWidth, middleSourceHeight, textureSize);
    }
    /**
     * 从贴图上取一块 {@code sourceWidth × sourceHeight} 的矩形，拉伸绘制成
     * {@code width × height}。
     *
     * <p>UV 用<b>贴图实际尺寸</b>归一化 —— {@code type2.png} 是 256×256，
     * 但台本里可能换成别的尺寸，写死 256 会让自定义贴图错位。</p>
     */
    private static void drawSprite(PoseStack poseStack, VertexConsumer builder,
                                   int x, int y, int width, int height,
                                   int u, int v, int sourceWidth, int sourceHeight, int[] textureSize) {
        float textureWidth = textureSize[0];
        float textureHeight = textureSize[1];
        float u0 = u / textureWidth;
        float u1 = (u + sourceWidth) / textureWidth;
        float v0 = v / textureHeight;
        float v1 = (v + sourceHeight) / textureHeight;
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
     * 一条排好版的气泡：文本行 或 图标。
     *
     * <p>图标气泡的宽高固定为 60×20（上游 {@code drawIcon} 的尺寸），
     * 文本气泡的宽高由折行结果决定。</p>
     */
    private record Bubble(List<FormattedCharSequence> lines, int width, int height,
                          ResourceLocation icon, int iconFrames, int iconFrame) {
        private static Bubble create(ChatText chatText, Side side, Font font, int tickCount) {
            if (chatText.isIcon()) {
                ResourceLocation icon = chatText.getIconPath();
                int frames = IconTextureSizes.getFrameCount(icon);
                int frame = frames > 1 ? (tickCount / 2) % frames : 0;
                return new Bubble(Lists.newArrayList(), IconTextureSizes.FRAME_WIDTH,
                        IconTextureSizes.FRAME_HEIGHT, icon, frames, frame);
            }
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
            return new Bubble(lines, width, lines.size() * LINE_HEIGHT, null, 1, 0);
        }
    }
}
