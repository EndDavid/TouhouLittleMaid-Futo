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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * 头顶气泡渲染器（最小版）移植自上游 ChatBubbleRenderer。
 *
 * <h2>本版只画文本</h2>
 * <p>上游还支持图标（模型包里的颜文字贴图，靠 SizeTexture 读帧数做动画）。
 * 本移植版没有那套贴图缓存，因此图标内容直接跳过。
 * 布局与上游一致：最多三条，一条居中、两条左右分开、三条时第三条移到上方。</p>
 *
 * <h2>背景贴图</h2>
 * <p>默认背景 textures/chat_bubble/bg/default1.png，按上游同一套 UV 切分：
 * 横向 3 段（左 / 中 / 右），纵向 2 段（主体 / 指向下方的小箭头）。
 * 中间段按文字宽度重复，长句会自动变宽。</p>
 *
 * <h2>为什么用 RenderType.text 而不是自建类型</h2>
 * <p>上游自建了一种「关混合、不模糊」的渲染类型，但 RenderStateShard 与 RenderType
 * 的内部类构造器在 vanilla 里是 protected，上游靠 Forge 的 AT 打开了它们。
 * 本移植版没有 AT（见 build.gradle 末尾），因此改用原版公开的 RenderType.text：
 * POSITION_COLOR_TEX + 半透明混合 + LEQUAL 深度测试。贴图本身带 alpha，视觉上一致。</p>
 */
public final class ChatBubbleRenderer {
    public static final ResourceLocation DEFAULT_BG =
            new ResourceLocation("touhou_little_maid", "textures/chat_bubble/bg/default1.png");

    private static final List<Pair<Long, ChatText>> TMP_CHAT_BUBBLES = Lists.newArrayList();

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
        int renderYOffset = maid.isSleeping() ? 48 : 0;
        int size = TMP_CHAT_BUBBLES.size();
        if (size == 1) {
            drawOne(TMP_CHAT_BUBBLES.get(0).getRight(), 0, -32 + renderYOffset,
                    poseStack, buffer, packedLight, maid);
        } else if (size == 2) {
            drawOne(TMP_CHAT_BUBBLES.get(0).getRight(), -55, -32 + renderYOffset,
                    poseStack, buffer, packedLight, maid);
            drawOne(TMP_CHAT_BUBBLES.get(1).getRight(), 55, -32 + renderYOffset,
                    poseStack, buffer, packedLight, maid);
        } else if (size >= 3) {
            drawOne(TMP_CHAT_BUBBLES.get(0).getRight(), -55, -32 + renderYOffset,
                    poseStack, buffer, packedLight, maid);
            drawOne(TMP_CHAT_BUBBLES.get(1).getRight(), 55, -32 + renderYOffset,
                    poseStack, buffer, packedLight, maid);
            drawOne(TMP_CHAT_BUBBLES.get(2).getRight(), 0, -62 + renderYOffset,
                    poseStack, buffer, packedLight, maid);
        }
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
     * 画一条：背景 + 文本。startX 是相对女仆中线的水平偏移。
     */
    private static void drawOne(ChatText chatText, int startX, int startY,
                                PoseStack poseStack, MultiBufferSource buffer, int packedLight, EntityMaid maid) {
        if (!chatText.isText()) {
            // 图标需要贴图缓存 SizeTexture，本轮未移植
            return;
        }
        Component parseText = ParseI18n.parse(chatText.getText());
        int width = Minecraft.getInstance().font.width(parseText);
        // 至少 1 个中间块：0 个时左右两块会拼成没有身体的细缝
        int count = Math.max(1, width / 20);
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
        drawBg(poseStack, vertexBuilder, startX - 10, startY + 10, 0.19F, 1, 1);

        Minecraft.getInstance().font.drawInBatch(parseText,
                startX - width / 2.0F, startY + 6, 0xff000000,
                false, poseStack.last().pose(), buffer, false, 0, packedLight);

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
     * RenderType.text 的顶点格式是 POSITION_COLOR_TEX，所以必须给 color：
     * 顺序是 vertex -> color -> uv。少了 color 会在写顶点时抛异常。
     */
    private static void vertex(Matrix4f matrix4f, VertexConsumer builder, float x, float y, float z,
                               float u, float v) {
        builder.vertex(matrix4f, x, y, z).color(255, 255, 255, 255).uv(u, v).endVertex();
    }
}