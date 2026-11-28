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
