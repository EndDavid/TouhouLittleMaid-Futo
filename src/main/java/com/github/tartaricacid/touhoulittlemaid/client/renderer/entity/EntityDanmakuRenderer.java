package com.github.tartaricacid.touhoulittlemaid.client.renderer.entity;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.projectile.DanmakuColor;
import com.github.tartaricacid.touhoulittlemaid.entity.projectile.DanmakuType;
import com.github.tartaricacid.touhoulittlemaid.entity.projectile.EntityDanmaku;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * 弹幕渲染器，移植自上游 {@code client/renderer/entity/EntityDanmakuRenderer}。
 *
 * <p>做法是「一个始终面向摄像机（billboard）的四边形」、从
 * {@code textures/entity/danmaku.png} 里取对应的 32x32 色块：</p>
 * <pre>
 * 贴图尺寸 416 x 128  =  13 列 x 4 行，每块 32x32
 *   列 = DanmakuColor 的序号（RED..GRAY，共 13）
 *   行 = DanmakuType  的序号（PELLET / BALL / ORBS / BIG_BALL，共 4）
 * 起址 = (32 * color.ordinal(), 32 * type.ordinal())
 * </pre>
 *
 * <p>用 {@code itemEntityTranslucentCull}（带 alpha 混合 + 背面剔除），
 * 这样弹幕是半透明发光片而不是硬边方块。法线统一为 {@code (0,0,1)}，
 * 让它受光均匀 —— 弹幕不该出现明暗渐变。</p>
 */
public class EntityDanmakuRenderer extends EntityRenderer<EntityDanmaku> {
    private static final ResourceLocation DANMAKU_TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/entity/danmaku.png");
    private static final RenderType RENDER_TYPE = RenderType.itemEntityTranslucentCull(DANMAKU_TEXTURE);
    /**
     * 贴图列数（{@link DanmakuColor} 的数量）与行数（{@link DanmakuType} 的数量），
     * 用于把 32x32 块换算成 0..1 的 UV。
     */
    private static final int TEXTURE_WIDTH = 416;
    private static final int TEXTURE_HEIGHT = 128;
    private static final int CELL = 32;

    public EntityDanmakuRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, Matrix3f normal,
                              double x, double y, double u, double v, int packedLight) {
        buffer.vertex(pose, (float) x, (float) y, 0.0F)
                .color(255, 255, 255, 255)
                .uv((float) u, (float) v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight)
                .normal(normal, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    @Override
    public void render(EntityDanmaku entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        DanmakuColor color = entity.getColor();
        DanmakuType type = entity.getDanmakuType();

        double[] startU = {CELL * color.ordinal()};
        double[] startV = {CELL * type.ordinal()};

        poseStack.pushPose();
        poseStack.translate(0, 0.1, 0);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Vector3f.YP.rotationDegrees(180.0F));

        VertexConsumer buffer = bufferSource.getBuffer(RENDER_TYPE);
        PoseStack.Pose last = poseStack.last();
        Matrix4f pose = last.pose();
        Matrix3f normal = last.normal();

        double s = type.getSize();
        double u0 = startU[0] / TEXTURE_WIDTH;
        double u1 = (startU[0] + CELL) / TEXTURE_WIDTH;
        double v0 = startV[0] / TEXTURE_HEIGHT;
        double v1 = (startV[0] + CELL) / TEXTURE_HEIGHT;

        vertex(buffer, pose, normal, -s, s, u0, v0, packedLight);
        vertex(buffer, pose, normal, -s, -s, u0, v1, packedLight);
        vertex(buffer, pose, normal, s, -s, u1, v1, packedLight);
        vertex(buffer, pose, normal, s, s, u1, v0, packedLight);

        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    @NotNull
    public ResourceLocation getTextureLocation(EntityDanmaku entity) {
        return DANMAKU_TEXTURE;
    }
}

