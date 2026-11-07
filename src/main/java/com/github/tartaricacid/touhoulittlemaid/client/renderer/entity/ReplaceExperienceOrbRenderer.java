package com.github.tartaricacid.touhoulittlemaid.client.renderer.entity;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.VanillaConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ExperienceOrbRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ExperienceOrb;

/**
 * 经验球替换渲染，移植自上游 {@code ReplaceExperienceOrbRenderer}。
 *
 * <p>TLM 把原版经验球的绿色小球换成了东方风格的点数贴片，图标按经验的多少取
 * {@code 0..10} 共 11 档（与原版 {@code ExperienceOrb#getIcon} 的分档完全一致），
 * 贴图是 64x64 的 4 列 3 行图集。</p>
 *
 * <p>注册方式：客户端入口里<b>覆盖</b>原版 {@code EntityType.EXPERIENCE_ORB} 的渲染器。
 * Fabric 的 {@code EntityRendererRegistry} 就是往 {@code EntityRenderers} 的 Map 里 put，
 * 后注册的会覆盖原版那一项，因此这样写合法。</p>
 *
 * <p>{@link VanillaConfig#REPLACE_XP_TEXTURE} 关闭时退回原版渲染器 —— 上游也是这样，
 * 保留了一个随时可关的开关。</p>
 */
public class ReplaceExperienceOrbRenderer extends EntityRenderer<ExperienceOrb> {
    private static final ResourceLocation POINT_ITEM_TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/entity/point_item.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(POINT_ITEM_TEXTURE);

    private final ExperienceOrbRenderer vanillaRender;

    public ReplaceExperienceOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.15F;
        this.shadowStrength = 0.75F;
        this.vanillaRender = new ExperienceOrbRenderer(context);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
                               float x, float y, int red, int green, int blue,
                               float texU, float texV, int packedLight) {
        consumer.vertex(pose, x, y, 0.0F)
                .color(red, green, blue, 128)
                .uv(texU, texV)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight)
                .normal(normal, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    /**
     * 点数贴片比原版经验球暗，这里补 7 级亮度让它在暗处也能看清（与上游一致）。
     */
    @Override
    protected int getBlockLightLevel(ExperienceOrb orb, BlockPos pos) {
        return Mth.clamp(super.getBlockLightLevel(orb, pos) + 7, 0, 15);
    }

    @Override
    public void render(ExperienceOrb orb, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        if (VanillaConfig.replaceXpTexture()) {
            this.renderPointItem(orb, poseStack, buffer, packedLight);
        } else {
            this.vanillaRender.render(orb, entityYaw, partialTicks, poseStack, buffer, packedLight);
        }
    }

    private void renderPointItem(ExperienceOrb orb, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        int icon = orb.getIcon();
        float texU1 = (float) (icon % 4 * 16) / 64.0F;
        float texU2 = (float) (icon % 4 * 16 + 16) / 64.0F;
        float texV2 = (float) (icon / 4 * 16) / 64.0F;
        float texV1 = (float) (icon / 4 * 16 + 16) / 64.0F;
        poseStack.translate(0.0F, 0.1F, 0.0F);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Vector3f.YP.rotationDegrees(180.0F));
        poseStack.scale(0.3F, 0.3F, 0.3F);

        VertexConsumer consumer = buffer.getBuffer(RENDER_TYPE);
        PoseStack.Pose last = poseStack.last();
        Matrix4f pose = last.pose();
        Matrix3f normal = last.normal();
        vertex(consumer, pose, normal, -0.5F, -0.25F, 255, 255, 255, texU1, texV1, packedLight);
        vertex(consumer, pose, normal, 0.5F, -0.25F, 255, 255, 255, texU2, texV1, packedLight);
        vertex(consumer, pose, normal, 0.5F, 0.75F, 255, 255, 255, texU2, texV2, packedLight);
        vertex(consumer, pose, normal, -0.5F, 0.75F, 255, 255, 255, texU1, texV2, packedLight);
        poseStack.popPose();
        // 让原版的阴影（shadowRadius / shadowStrength）继续生效
        super.render(orb, 0.0F, 0.0F, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ExperienceOrb orb) {
        return POINT_ITEM_TEXTURE;
    }
}

