package com.github.tartaricacid.touhoulittlemaid.client.renderer.entity;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.model.EntityYukkuriModel;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.VanillaConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Slime;

/**
 * 把原版史莱姆渲染成「灵梦油库里」，移植自上游 1.18.2 的
 * {@code client/renderer/entity/EntityYukkuriSlimeRender}。
 *
 * <p><b>贴图用的是上游 1.20.1 的</b>{@code textures/bedrock/entity/reimu_yukkuri.png}：
 * 上游 1.20.1 把这张图重画了（64×64 里 577 个像素与旧 {@code textures/entity/yukkuri.png} 不同），
 * 并把文件名从 {@code yukkuri} 改成 {@code reimu_yukkuri}（因为岩浆怪那套叫 marisa）。
 * 新旧两版的 UV 布局与尺寸都没变，所以重画的图可以直接贴在这套网格上。</p>
 *
 * <p><b>缩放公式</b>照抄原版 {@code SlimeRenderer#scale}：史莱姆的「挤动」按
 * {@code squish} 与体型的比例压缩，油库里沿用同一套，所以大小与弹跳手感跟原版一致。</p>
 *
 * <p>{@link VanillaConfig#replaceSlimeModel()} 关闭时退回原版 {@link SlimeRenderer} ——
 * 与上游一致，随时可关。</p>
 */
public class EntityYukkuriSlimeRender extends MobRenderer<Slime, EntityYukkuriModel> {
    /** 上游 1.20.1 重绘的那张（旧版叫 {@code textures/entity/yukkuri.png}）。 */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/bedrock/entity/reimu_yukkuri.png");

    private final SlimeRenderer vanillaRender;

    public EntityYukkuriSlimeRender(EntityRendererProvider.Context context) {
        super(context, new EntityYukkuriModel(context.bakeLayer(EntityYukkuriModel.LAYER)), 0.25F);
        this.vanillaRender = new SlimeRenderer(context);
    }

    @Override
    public void render(Slime slime, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        if (VanillaConfig.replaceSlimeModel()) {
            this.renderYukkuri(slime, entityYaw, partialTicks, poseStack, buffer, packedLight);
        } else {
            this.vanillaRender.render(slime, entityYaw, partialTicks, poseStack, buffer, packedLight);
        }
    }

    private void renderYukkuri(Slime slime, float entityYaw, float partialTicks, PoseStack poseStack,
                               MultiBufferSource buffer, int packedLight) {
        // 阴影跟着体型走，否则大史莱姆脚下会是一小块阴影
        this.shadowRadius = 0.25F * (float) slime.getSize();
        super.render(slime, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    protected void scale(Slime slime, PoseStack poseStack, float partialTicks) {
        poseStack.scale(0.999F, 0.999F, 0.999F);
        poseStack.translate(0.0F, 0.001F, 0.0F);
        float size = (float) slime.getSize();
        float squish = Mth.lerp(partialTicks, slime.oSquish, slime.squish) / (size * 0.5F + 1.0F);
        float inverse = 1.0F / (squish + 1.0F);
        poseStack.scale(inverse * size, 1.0F / inverse * size, inverse * size);
    }

    @Override
    public ResourceLocation getTextureLocation(Slime slime) {
        return TEXTURE;
    }
}

