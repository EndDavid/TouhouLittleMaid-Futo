package com.github.tartaricacid.touhoulittlemaid.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 世界空间绘制工具。
 *
 * <h2>这一版为什么能对上 —— 直接照 vanilla {@code WorldRenderer#renderChunkDebugInfo} 的写法</h2>
 *
 * <p>反编译 yarn 1.18.2 的 {@code WorldRenderer.render(...)} 后确认了三件事：</p>
 *
 * <ol>
 *     <li>相机旋转在别处就已进了 modelview，{@code render(...)} 里一次旋转调用都没有；</li>
 *     <li>画世界几何（方块描边、区块调试框）时顶点是 {@code buffer.vertex(x, y, z)}
 *         —— <b>不带任何矩阵</b>，纯 modelview 空间，坐标是「世界坐标 − 相机位置」；</li>
 *     <li>最后用 {@code Tessellator.draw()} <b>当场刷出</b>，不留给后续批次。</li>
 * </ol>
 *
 * <p>所以本类不再自己往 PoseStack 里塞相机变换，也不把顶点交给延迟刷出的
 * {@code RenderBuffers}。用 {@link #beginCameraSpace()} 建立一个
 * 「<b>单位阵 → 相机旋转 → 平移(-相机位置)</b>」的 modelview，之后所有顶点一律用世界
 * 绝对坐标、不乘矩阵，画完立刻 flush。这样与外部残留状态完全无关。</p>
 */
@Environment(EnvType.CLIENT)
public final class RenderHelper {
    private RenderHelper() {
    }

    /**
     * 把 modelview 重置成「相机变换」，即 {@code R_x(pitch) · R_y(yaw+180) · T(-cameraPos)}。
     *
     * <p>{@code setIdentity()} 是必须的：实测 {@code WorldRenderEvents.LAST} 触发时
     * modelview 的状态并不可靠（这正是前几版圈随视角转、文字糊脸的原因）。
     * 这里直接把它抹平，只保留我们自己算的相机变换。</p>
     *
     * @return 调用方必须把它原样传给 {@link #endCameraSpace(PoseStack)}
     */
    public static PoseStack beginCameraSpace() {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 position = camera.getPosition();
        PoseStack stack = RenderSystem.getModelViewStack();
        stack.pushPose();
        stack.setIdentity();
        // 与 vanilla 世界渲染同序：先俯仰，后偏航
        stack.mulPose(com.mojang.math.Vector3f.XP.rotationDegrees(camera.getXRot()));
        stack.mulPose(com.mojang.math.Vector3f.YP.rotationDegrees(camera.getYRot() + 180.0F));
        stack.translate(-position.x, -position.y, -position.z);
        RenderSystem.applyModelViewMatrix();
        return stack;
    }

    /**
     * 恢复 {@link #beginCameraSpace()} 之前的 modelview。
     */
    public static void endCameraSpace(PoseStack stack) {
        stack.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    // ------------------------------------------------------------------ 线段

    /**
     * 开始一批线段。着色器 / 线宽与 vanilla 区块调试框一致。
     */
    public static BufferBuilder beginLines(float width) {
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.lineWidth(width);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        return builder;
    }

    /**
     * 当场刷出线段批次。
     */
    public static void endLines() {
        Tesselator.getInstance().end();
        RenderSystem.lineWidth(1.0F);
    }

    /**
     * 写一条线。法线分别取 (1,0,0)/(0,1,0)/(0,0,1) 各写一遍 ——
     * {@code LINES} 着色器会按法线做方向性裁剪，只写一个法线时某些角度整条线会消失。
     */
    public static void renderLine(BufferBuilder builder, Vec3 start, Vec3 end,
                                  float red, float green, float blue) {
        line(builder, start, end, red, green, blue, 1.0F, 0.0F, 0.0F);
        line(builder, start, end, red, green, blue, 0.0F, 1.0F, 0.0F);
        line(builder, start, end, red, green, blue, 0.0F, 0.0F, 1.0F);
    }

    private static void line(BufferBuilder builder, Vec3 start, Vec3 end,
                             float red, float green, float blue, float nx, float ny, float nz) {
        builder.vertex(start.x, start.y, start.z).color(red, green, blue, 1.0F)
                .normal(nx, ny, nz).endVertex();
        builder.vertex(end.x, end.y, end.z).color(red, green, blue, 1.0F)
                .normal(nx, ny, nz).endVertex();
    }

    /**
     * 画一个水平圆环（多边形近似）。所有顶点 y 相同，所以必定躺在水平面上。
     */
    public static void renderCylinder(BufferBuilder builder, Vec3 center, double radius, int precision,
                                      float red, float green, float blue) {
        double step = 2 * Math.PI / precision;
        for (int i = precision; i >= 0; i--) {
            Vec3 p1 = new Vec3(center.x + radius * Math.cos(i * step), center.y,
                    center.z + radius * Math.sin(i * step));
            Vec3 p2 = new Vec3(center.x + radius * Math.cos((i - 1) * step), center.y,
                    center.z + radius * Math.sin((i - 1) * step));
            renderLine(builder, p1, p2, red, green, blue);
        }
    }

    // ------------------------------------------------------------------ 实心框

    /**
     * 半透明实心立方体（女仆脚下的高亮）。
     *
     * <p>顶点序列逐行照抄上游 {@code util/RenderHelper#addChainedFilledBoxVertices}
     * （那串重复顶点是 {@code TRIANGLE_STRIP} 连面所需）。坐标用世界绝对坐标。</p>
     */
    public static void addChainedFilledBoxVertices(AABB box,
                                                   float red, float green, float blue, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableTexture();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        float x0 = (float) box.minX;
        float y0 = (float) box.minY;
        float z0 = (float) box.minZ;
        float x1 = (float) box.maxX;
        float y1 = (float) box.maxY;
        float z1 = (float) box.maxZ;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        v(builder, x0, y0, z0, red, green, blue, alpha);
        v(builder, x0, y0, z0, red, green, blue, alpha);
        v(builder, x0, y0, z0, red, green, blue, alpha);
        v(builder, x0, y0, z1, red, green, blue, alpha);
        v(builder, x0, y1, z0, red, green, blue, alpha);
        v(builder, x0, y1, z1, red, green, blue, alpha);
        v(builder, x0, y1, z1, red, green, blue, alpha);
        v(builder, x0, y0, z1, red, green, blue, alpha);
        v(builder, x1, y1, z1, red, green, blue, alpha);
        v(builder, x1, y0, z1, red, green, blue, alpha);
        v(builder, x1, y0, z1, red, green, blue, alpha);
        v(builder, x1, y0, z0, red, green, blue, alpha);
        v(builder, x1, y1, z1, red, green, blue, alpha);
        v(builder, x1, y1, z0, red, green, blue, alpha);
        v(builder, x1, y1, z0, red, green, blue, alpha);
        v(builder, x1, y0, z0, red, green, blue, alpha);
        v(builder, x0, y1, z0, red, green, blue, alpha);
        v(builder, x0, y0, z0, red, green, blue, alpha);
        v(builder, x0, y0, z0, red, green, blue, alpha);
        v(builder, x1, y0, z0, red, green, blue, alpha);
        v(builder, x0, y0, z1, red, green, blue, alpha);
        v(builder, x1, y0, z1, red, green, blue, alpha);
        v(builder, x1, y0, z1, red, green, blue, alpha);
        v(builder, x0, y1, z0, red, green, blue, alpha);
        v(builder, x0, y1, z0, red, green, blue, alpha);
        v(builder, x0, y1, z1, red, green, blue, alpha);
        v(builder, x1, y1, z0, red, green, blue, alpha);
        v(builder, x1, y1, z1, red, green, blue, alpha);
        v(builder, x1, y1, z1, red, green, blue, alpha);
        v(builder, x1, y1, z1, red, green, blue, alpha);

        tesselator.end();
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
    }

    private static void v(BufferBuilder builder, float x, float y, float z,
                          float red, float green, float blue, float alpha) {
        builder.vertex(x, y, z).color(red, green, blue, alpha).endVertex();
    }

    // ------------------------------------------------------------------ 文字

    /**
     * 在<b>世界坐标</b>处画一段永远面向相机的文字。
     *
     * <p><b>调用前必须处在 {@link #beginCameraSpace()} 的窗口内</b>
     * （此时 modelview 已含 {@code T(-相机位置)}）。因此局部 pose 只需要：</p>
     *
     * <pre>
     * pose = T(世界坐标) · camera.rotation() · S(-scale, -scale, scale)
     * </pre>
     *
     * <p>推导：最终 = {@code R · T(-cam) · T(w) · R_cam · S} =
     * {@code T(R·(w-cam)) · R · R_cam · S}，而 {@code R_cam = R⁻¹}，
     * 于是旋转项相互抵消，只剩「平移到视空间 + 缩放」——
     * 这正是字永远正对屏幕、且不镜像的条件。</p>
     *
     * <p>之前几版要么把 {@code T(-cam)} 也塞进局部 pose（相机变换做两次），
     * 要么用延迟刷出的 {@code RenderBuffers}（刷出时 modelview 已不是我们设的那个），
     * 所以文字糊在脸上 / 镜像。这里用 {@code MultiBufferSource.immediate} +
     * {@code endBatch()} 当场画完。</p>
     */
    public static void renderFloatingText(String text, double x, double y, double z,
                                          int color, float scale, boolean center, float yOffset,
                                          boolean seeThrough) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return;
        }
        Font font = minecraft.font;

        PoseStack poseStack = new PoseStack();
        poseStack.translate(x, y, z);
        poseStack.mulPoseMatrix(new Matrix4f(camera.rotation()));
        poseStack.scale(-scale, -scale, scale);

        MultiBufferSource.BufferSource immediate =
                MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        float fontX = center ? (float) (-font.width(text)) / 2.0F : 0.0F;
        font.drawInBatch(text, fontX, yOffset, color, false, poseStack.last().pose(),
                immediate, seeThrough, 0, 0xF000F0);
        immediate.endBatch();
    }
}

