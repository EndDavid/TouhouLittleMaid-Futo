package com.github.tartaricacid.touhoulittlemaid.client.renderer.ai;
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
import org.apache.commons.lang3.tuple.Pair;
import java.util.List;
/**
 * 头顶气泡渲染器，移植自上游 {@code client/renderer/entity/ChatBubbleRenderer}。
 *
 * <h2>本版只画文本</h2>
 * <p>上游还支持图标（模型包里的颜文字贴图，靠 SizeTexture 读帧数做动画）。
 * 本移植版没有那套贴图缓存，因此 {@link ChatText#isIcon()} 的内容直接跳过。</p>
 *
 * <h2>布局与上游逐项对齐</h2>
 * <ul>
 *     <li>一条：居中，{@code y = -32}，箭头贴图取中列；</li>
 *     <li>两条：第一条在左（{@code x = -fullWidth/2 - 5}，箭头取右列），
 *         第二条在右（{@code x = fullWidth/2 + 5}，箭头取左列）；</li>
 *     <li>三条：第三位移到 {@code y = -62} 居中；</li>
 *     <li>睡着时整体下移 48（{@code y} 加 48）；</li>
 *     <li>背景横向切 3 段、纵向 2 段（主体 / 指向下方的小箭头），
 *         中间段按 {@code 文字宽度 / 20} 重复，<b>不做 {@code max(1, …)}</b> ——
 *         上游就是这么算的，短句只有左右两块。</li>
 * </ul>
 *
 * <h2>顶点格式：这里踩过坑，别改回去</h2>
 * <p>上游自建了一种 {@code DefaultVertexFormat.POSITION_TEX} + 关混合的渲染类型，
 * 需要 Forge 的 AT 打开 {@code RenderStateShard} / {@code RenderType.create}。
 * 本移植版没有 AT，改用原版公开的 {@code RenderType.text}。</p>
 * <p><b>注意 {@code RenderType.text} 的顶点格式是
 * {@code POSITION_COLOR_TEX_LIGHTMAP}，四个元素缺一不可：</b>
 * 只写 {@code vertex → color → uv} 会在 {@code BufferBuilder#endVertex} 抛
 * {@code IllegalStateException: Not filled all elements of the vertex}，
 * 表现就是「AI 一回复、头顶要出气泡，客户端立刻崩」。
 * 所以 {@link #vertex} 末尾必须补 {@code uv2}，这里固定给
 * {@link LightTexture#FULL_BRIGHT}：上游那个渲染类型没有光照图状态，
 * 气泡永远是全亮的，不该随天黑变暗。</p>
 */
public final class ChatBubbleRenderer {
    public static final ResourceLocation DEFAULT_BG =
            new ResourceLocation("touhou_little_maid", "textures/chat_bubble/bg/default1.png");
    private static final List<Pair<Long, ChatText>> TMP_CHAT_BUBBLES = Lists.newArrayList();
    private static final String LEFT_ARROW = "left_arrow";
    private static final String MIDDLE_ARROW = "middle_arrow";
    private static final String RIGHT_ARROW = "right_arrow";
    /**
     * 第几个槽位 → 摆在女仆哪一侧。顺序与上游一致：
     * 第一条在左、第二条在右、第三条居中偏上。
     */
    private static final int SIDE_LEFT = -1;
    private static final int SIDE_CENTER = 0;
    private static final int SIDE_RIGHT = 1;
    private ChatBubbleRenderer() {
    }
    /**
     * 在女仆头顶画所有未过期内容。distanceSqr 由调用方算好传进来。
     */
    public static void renderChatBubble(EntityMaid maid, PoseStack poseStack,
                                        MultiBufferSource buffer, int packedLight, double distanceSqr) {
        // 与原版名牌同一个阈值（64 格）
        if (distanceSqr > 64.0D * 64.0D) {
            return;
        }
        if (!collectChatBubbles(maid)) {
            return;
        }
        int yOffset = maid.isSleeping() ? 48 : 0;
        int size = TMP_CHAT_BUBBLES.size();
        if (size == 1) {
            drawTextBubble(TMP_CHAT_BUBBLES.get(0).getRight(), SIDE_CENTER, -32 + yOffset,
                    maid, poseStack, buffer, packedLight);
            return;
        }
        if (size == 2) {
            drawTextBubble(TMP_CHAT_BUBBLES.get(0).getRight(), SIDE_LEFT, -32 + yOffset,
                    maid, poseStack, buffer, packedLight);
            drawTextBubble(TMP_CHAT_BUBBLES.get(1).getRight(), SIDE_RIGHT, -32 + yOffset,
                    maid, poseStack, buffer, packedLight);
            return;
        }
        drawTextBubble(TMP_CHAT_BUBBLES.get(0).getRight(), SIDE_LEFT, -32 + yOffset,
                maid, poseStack, buffer, packedLight);
        drawTextBubble(TMP_CHAT_BUBBLES.get(1).getRight(), SIDE_RIGHT, -32 + yOffset,
                maid, poseStack, buffer, packedLight);
        drawTextBubble(TMP_CHAT_BUBBLES.get(2).getRight(), SIDE_CENTER, -62 + yOffset,
                maid, poseStack, buffer, packedLight);
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
        out.add(bubble);
    }
    /**
     * 画一条文本气泡：背景 + 文本。
     *
     * @param side {@link #SIDE_LEFT} / {@link #SIDE_CENTER} / {@link #SIDE_RIGHT}，
     *             决定水平偏移与箭头贴图取哪一列
     */
    private static void drawTextBubble(ChatText chatText, int side, int startY,
                                       EntityMaid maid, PoseStack poseStack, MultiBufferSource buffer,
                                       int packedLight) {
        if (!chatText.isText()) {
            // 图标气泡需要贴图缓存 SizeTexture，本轮未移植
            return;
        }
        Font font = Minecraft.getInstance().font;
        Component text = ParseI18n.parse(chatText.getText());
        int width = font.width(text);
        String arrow;
        int startX;
        if (side == SIDE_CENTER) {
            arrow = MIDDLE_ARROW;
            startX = 0;
        } else {
            // 左右分开时要额外让开 5，否则两个气泡会贴在一起
            int fullWidth = ((width / 20) + 2) * 20;
            if (side == SIDE_LEFT) {
                arrow = RIGHT_ARROW;
                startX = -fullWidth / 2 - 5;
            } else {
                arrow = LEFT_ARROW;
                startX = fullWidth / 2 + 5;
            }
        }
        int textX = startX;
        int y = startY;
        drawBubble(maid, poseStack, buffer, startX, startY, width, arrow,
                (ps, buf) -> font.drawInBatch(text, -width / 2.0F + textX, y + 6, 0xFF000000,
                        false, ps.last().pose(), buf, false, 0, packedLight));
    }
    /**
     * 画气泡背景，再调用 {@code content} 在同一个变换里画内容。
     */
    private static void drawBubble(EntityMaid maid, PoseStack poseStack, MultiBufferSource buffer,
                                   int startX, int startY, int stringWidth, String arrow,
                                   BubbleContent content) {
        int count = stringWidth / 20;
        int fullWidth = (count + 2) * 20;
        int leftStartX = -fullWidth / 2 + startX;
        int rightStartX = fullWidth / 2 - 20 + startX;
        int middleStartX = -fullWidth / 2 + 20 + startX;
        poseStack.pushPose();
        poseStack.translate(0, maid.getBbHeight() + 0.75F, 0);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);
        VertexConsumer vertexBuilder = buffer.getBuffer(RenderType.text(DEFAULT_BG));
        drawBg(poseStack, vertexBuilder, leftStartX, startY, 0.2F, 0, 0);
        for (int i = 0; i < count; i++) {
            drawBg(poseStack, vertexBuilder, middleStartX, startY, 0.2F, 1, 0);
            middleStartX = middleStartX + 20;
        }
        drawBg(poseStack, vertexBuilder, rightStartX, startY, 0.2F, 2, 0);
        if (LEFT_ARROW.equals(arrow)) {
            drawBg(poseStack, vertexBuilder, leftStartX - 10, startY + 10, 0.19F, 0, 1);
        } else if (RIGHT_ARROW.equals(arrow)) {
            drawBg(poseStack, vertexBuilder, rightStartX + 10, startY + 10, 0.19F, 2, 1);
        } else {
            drawBg(poseStack, vertexBuilder, startX - 10, startY + 10, 0.19F, 1, 1);
        }
        content.draw(poseStack, buffer);
        poseStack.popPose();
    }
    /**
     * 画背景的一格。UV 用上游那套 u = uIndex / 3、v = vIndex / 2。
     */
    public static void drawBg(PoseStack poseStack, VertexConsumer builder, int x, int y, float z,
                              int uIndex, int vIndex) {
        float height = 20;
        float width = 20;
        Matrix4f matrix4f = poseStack.last().pose();
        float u0 = uIndex / 3.0F;
        float u1 = (uIndex + 1) / 3.0F;
        float v0 = vIndex / 2.0F;
        float v1 = (vIndex + 1) / 2.0F;
        vertex(matrix4f, builder, x, y + height, z, u0, v1);
        vertex(matrix4f, builder, x + width, y + height, z, u1, v1);
        vertex(matrix4f, builder, x + width, y, z, u1, v0);
        vertex(matrix4f, builder, x, y, z, u0, v0);
    }
    /**
     * {@code RenderType.text} 的顶点格式是 {@code POSITION_COLOR_TEX_LIGHTMAP}，
     * 顺序 position → color → uv0 → uv2，四个都要写，少一个就抛
     * {@code Not filled all elements of the vertex}。
     */
    private static void vertex(Matrix4f matrix4f, VertexConsumer builder, float x, float y, float z,
                               float u, float v) {
        builder.vertex(matrix4f, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .uv2(LightTexture.FULL_BRIGHT)
                .endVertex();
    }
    private interface BubbleContent {
        void draw(PoseStack poseStack, MultiBufferSource buffer);
    }
}
