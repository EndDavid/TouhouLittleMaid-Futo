package com.github.tartaricacid.touhoulittlemaid.client.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.LivingEntity;

/**
 * 在 GUI 里画一个会自转的实体（上游 {@code util/RenderHelper#renderEntityInInventory} 的移植）。
 *
 * <h2>为什么不能直接用 {@code InventoryScreen#renderEntityInInventory}</h2>
 *
 * <p>那个方法也能在 GUI 里画实体，但它的朝向被写死成从鼠标坐标反推：</p>
 *
 * <pre>
 * float angleX = (float) Math.atan(mouseX / 40.0F);
 * entity.setYRot(180.0F + angleX * 40.0F);
 * </pre>
 *
 * <p>{@code atan} 的值域是 {@code (-π/2, π/2)}，乘 40 之后女仆只能在
 * {@code 180° ± 62.8°} 这个约 125° 的扇区里摆动 —— <b>无法整圈自转</b>。
 * 物品提示框里的预览需要匀速转一圈（否则看不清背面和侧面），所以这里照抄上游的写法：
 * 自己往 modelview 里塞变换，再直接调 {@link EntityRenderDispatcher#render}，
 * 实体朝向固定 {@code 180°}，绕 Y 轴的旋转由外部传入的 {@link Quaternion} 决定。</p>
 *
 * <h2>modelview 的两次「分层」为什么必要</h2>
 *
 * <p>这段是从原版 {@code renderEntityInInventoryRaw} 学来的结构，两步都在
 * {@code RenderSystem.getModelViewStack()} 上做：</p>
 *
 * <ol>
 *     <li>先把 GUI 坐标系搬到 {@code (x, y, blitOffset + 1050)}、并把 Z 轴翻过来
 *         （{@code scale(1, 1, -1)}）—— 这是把「GUI 的 XY 平面」变成「世界的 XY 平面 + 朝向相机」；</li>
 *     <li>再 {@code applyModelViewMatrix()} 落地，之后<b>新建一个干净的 PoseStack</b>
 *         用来画实体本身（{@code translate(0, 0, 1000)} 拉开深度、{@code scale} 缩放实体）。</li>
 * </ol>
 *
 * <p>分两步的好处是实体自己的变换不会污染 GUI 后续的绘制 ——
 * 方法返回前会把 modelview 栈 pop 回去，并 {@code applyModelViewMatrix()} 刷新一次。</p>
 */
@Environment(EnvType.CLIENT)
public final class InventoryEntityRenderer {
    private InventoryEntityRenderer() {
    }

    /**
     * 在 GUI 的指定像素位置画一个实体。
     *
     * @param poseStack  GUI 当前的 PoseStack（只用来 push/pop，不直接承载变换）
     * @param x          GUI 像素 X（对应 {@code poseStack} 的左上角原点）
     * @param y          GUI 像素 Y
     * @param z          深度（bleed offset）
     * @param scale      缩放（上游提示框里是「模型缩放 × 25」）
     * @param rotation   实体本身的朝向（提示框里传绕 Y 轴的自转角）
     * @param entity     要画的实体，必须已设置好模型 / 名字
     */
    public static void render(PoseStack poseStack, int x, int y, int z, int scale,
                              Quaternion rotation, LivingEntity entity) {
        poseStack.pushPose();
        poseStack.translate(x, y, z + 50);
        // Z 轴取负：GUI 的 +Z 指向屏幕外，世界的 +Z 指向相机方向，必须翻过来
        poseStack.mulPoseMatrix(Matrix4f.createScaleMatrix(scale, scale, -scale));
        poseStack.mulPose(rotation);

        // 换到「实体在 GUI 里的标准光照」，否则女仆会按世界光照亮度渲染（地下就是全黑）
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        // 面向相机：GUI 里没有相机实体，必须显式给一个朝向，否则实体会按默认朝向渲染
        Quaternion cameraOrientation = Vector3f.ZP.rotationDegrees(180.0F);
        cameraOrientation.conj();
        dispatcher.overrideCameraOrientation(cameraOrientation);
        // GUI 里投影阴影只会糊在提示框背景上
        dispatcher.setRenderShadow(false);

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        // runAsFancy 是必需的：女仆用 GeckoLib 渲染，需要 shader / 光照这类「非快速」路径
        RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F,
                poseStack, bufferSource, 15728880));
        // 必须立刻刷出：提示框的绘制顺序在 bufferSource 的常规冲刷之外
        RenderSystem.disableDepthTest();
        bufferSource.endBatch();
        RenderSystem.enableDepthTest();

        dispatcher.setRenderShadow(true);
        poseStack.popPose();
        // 还原成「画 3D 物品」的光照，避免影响同批次里的其它 GUI 元素
        Lighting.setupFor3DItems();
    }
}
