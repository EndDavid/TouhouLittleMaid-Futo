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

    /**
     * 气泡各层的局部 Z，<b>必须互不相同</b>。
     *
     * <h2>为什么不能都用同一个值（踩过的坑）</h2>
     * <p>底图是九宫格拼出来的若干个四边形，图标是另一个四边形。两者若在同一 z 上，
     * 就是「共面」的几何：数学上的深度相同，但光栅化时两个大小不同的四边形在
     * 逐像素插值出的深度值会有末位差异，配上 {@code RenderType} 的
     * {@code LEQUAL_DEPTH_TEST}（且写深度），结果就是<b>大约一半像素通过、一半不通过</b>
     * —— 表现是一条条横向条纹，玩家转动视角时条纹还会跟着移动闪烁。</p>
     *
     * <p>文字之所以从来没出过这个现象：{@code Font#drawInBatch} 的顶点 z 固定是 {@code 0}，
     * 恰好和底图的 {@code 0.2} 拉开了距离。上游 1.18.2 也是这么分的
     * （{@code drawBg} 传 0.2f、{@code drawIcon} 传 0）—— 这里只是把这条隐式约定写明。</p>
     *
     * <p><b>方向</b>：这个位姿下（{@code scale(…, +0.025F)} 且已经过
     * {@code cameraOrientation()}）<b>z 越小越靠近镜头</b>，
     * 证据就是 z=0 的文字能盖住 z=0.2 的底图。</p>
     */
    private static final float Z_BACKGROUND = 0.2F;
    /**
     * 尾巴压在底图之上（与 1.20.1 里那两句 {@code translate(0, 0, -0.01)} 同理）。
     *
     * <p>与底图之间留 {@code 0.1} 个单位（= 0.0025 格 ≈ 2.5 mm，肉眼看不出错位），
     * 换成深度值是几百个单位，远大于光栅化的浮点误差 —— 不要改成 {@code 0.199} 这种
     * 「只差一点」的值，那样又会回到共面附近。</p>
     */
    private static final float Z_ARROW = 0.1F;
    /** 内容层（图标）。文字走 {@code Font} 自带的 0，正好是同一层。 */
    private static final float Z_CONTENT = 0.0F;

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
                arrowU, ARROW_V, ARROW_SIZE, ARROW_SIZE, backgroundSize, Z_ARROW);
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
     * <p><b>z 必须用 {@link #Z_CONTENT}，不能和底图同为 0.2</b>：共面会 z-fighting，
     * 表现就是玩家转视角时图标上出现横向条纹闪烁（实测踩过）。</p>
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
        vertex(matrix, iconBuilder, iconX, iconY + height, Z_CONTENT, 0.0F, v1);
        vertex(matrix, iconBuilder, iconX + width, iconY + height, Z_CONTENT, 1.0F, v1);
        vertex(matrix, iconBuilder, iconX + width, iconY, Z_CONTENT, 1.0F, v0);
        vertex(matrix, iconBuilder, iconX, iconY, Z_CONTENT, 0.0F, v0);
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
        drawSprite(poseStack, builder, x, y, width, height, u, v, sourceWidth, sourceHeight,
                textureSize, Z_BACKGROUND);
    }

    /**
     * 同上，但显式指定 z（尾巴用 {@link #Z_ARROW}，压底图一点点）。
     */
    private static void drawSprite(PoseStack poseStack, VertexConsumer builder,
                                   int x, int y, int width, int height,
                                   int u, int v, int sourceWidth, int sourceHeight,
                                   int[] textureSize, float z) {
        float textureWidth = textureSize[0];
        float textureHeight = textureSize[1];
        float u0 = u / textureWidth;
        float u1 = (u + sourceWidth) / textureWidth;
        float v0 = v / textureHeight;
        float v1 = (v + sourceHeight) / textureHeight;
        Matrix4f matrix = poseStack.last().pose();
        vertex(matrix, builder, x, y + height, z, u0, v1);
        vertex(matrix, builder, x + width, y + height, z, u1, v1);
        vertex(matrix, builder, x + width, y, z, u1, v0);
        vertex(matrix, builder, x, y, z, u0, v0);
    }

    /**
     * {@code RenderType.text} 的顶点格式是 {@code POSITION_COLOR_TEX_LIGHTMAP}，
     * 顺序 position → color → uv0 → uv2，四个都要写，少一个就抛
     * {@code Not filled all elements of the vertex}。
     *
     * <p>{@code z} 必须按层区分（见 {@link #Z_BACKGROUND} 的注释），
     * 不要图省事统一传一个值 —— 共面会换来满屏横条纹。</p>
     */
    private static void vertex(Matrix4f matrix4f, VertexConsumer builder, float x, float y,
                               float z, float u, float v) {
        builder.vertex(matrix4f, x, y, z)
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
