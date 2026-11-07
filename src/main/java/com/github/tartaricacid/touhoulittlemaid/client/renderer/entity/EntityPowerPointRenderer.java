package com.github.tartaricacid.touhoulittlemaid.client.renderer.entity;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityPowerPoint;
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

/**
 * P 点渲染器，移植自上游 {@code EntityPowerPointRenderer}。
 *
 * <p>贴图是 64x64 的 4 列 3 行图集，按 {@code EntityPowerPoint#getIcon()} 取格子，
 * 相机朝向的 billboard 四边形，缩放 0.3。</p>
 */
public class EntityPowerPointRenderer extends EntityRenderer<EntityPowerPoint> {
    private static final ResourceLocation POWER_POINT_TEXTURES =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/entity/power_point.png");
    private static final RenderType RENDER_TYPE = RenderType.itemEntityTranslucentCull(POWER_POINT_TEXTURES);

    public EntityPowerPointRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
                               double x, double y, double texU, double texV, int packedLight) {
        consumer.vertex(pose, (float) x, (float) y, 0.0F)
                .color(255, 255, 255, 255)
                .uv((float) texU, (float) texV)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight)
                .normal(normal, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    @Override
    public void render(EntityPowerPoint point, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        int icon = point.getIcon();
        int remainder = icon % 4;
        int quotient = icon / 4;
        double texU1 = remainder * 16 / 64.0;
        double texU2 = (remainder * 16 + 16) / 64.0;
        double texV2 = quotient * 16 / 64.0;
        double texV1 = (quotient * 16 + 16) / 64.0;

        poseStack.pushPose();
        poseStack.translate(0, 0.1, 0);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Vector3f.YP.rotationDegrees(180.0F));
        poseStack.scale(0.3F, 0.3F, 0.3F);

        VertexConsumer consumer = buffer.getBuffer(RENDER_TYPE);
        PoseStack.Pose last = poseStack.last();
        Matrix4f pose = last.pose();
        Matrix3f normal = last.normal();
        vertex(consumer, pose, normal, -1, -0.25, texU1, texV1, packedLight);
        vertex(consumer, pose, normal, 1, -0.25, texU2, texV1, packedLight);
        vertex(consumer, pose, normal, 1, 1.75, texU2, texV2, packedLight);
        vertex(consumer, pose, normal, -1, 1.75, texU1, texV2, packedLight);
        poseStack.popPose();
        super.render(point, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(EntityPowerPoint point) {
        return POWER_POINT_TEXTURES;
    }
}

