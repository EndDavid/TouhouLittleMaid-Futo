package com.github.tartaricacid.touhoulittlemaid.client.renderer.entity;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.GeoBlockRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.resource.BedrockEntityModels;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.VanillaConfig;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MagmaCubeRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.MagmaCube;

/**
 * 把原版岩浆怪渲染成「魔理沙油库里」。
 *
 * <p>与史莱姆那一半（{@link EntityYukkuriSlimeRender}）不同，这一版<b>直接用上游 1.20.1 的
 * 基岩模型与贴图</b>，而不是手写网格：</p>
 *
 * <table border="1">
 *     <caption>两版的差异</caption>
 *     <tr><th></th><th>1.18.2</th><th>1.20.1（本实现）</th></tr>
 *     <tr><td>贴图</td><td>64×64</td><td><b>128×128</b></td></tr>
 *     <tr><td>几何</td><td>手写 {@code LayerDefinition}，9 根骨骼</td><td>{@code marisa_yukkuri.json}，14 根骨骼</td></tr>
 *     <tr><td>UV</td><td>(0,18) / (46,14) / (0,49) …</td><td>(45,16) / (45,33) / (0,0) … <b>全换</b></td></tr>
 * </table>
 *
 * <p>也就是说这不是「换一张图」而是「换整套外观」，手写网格等于把 JSON 手工重排一遍，
 * 抄错一个 uv 就是贴图错位且极难发现。所以这里走 {@link BedrockEntityModels} +
 * {@link GeoBlockRenderer}（内部就是 GeckoLib 的 {@code renderRecursively}），
 * 几何与贴图都保持上游原样。</p>
 *
 * <h2>为什么继承 {@link EntityRenderer} 而不是 {@code MobRenderer}</h2>
 * <p>{@code LivingEntityRenderer#render} 会给 {@code PoseStack} 套上
 * {@code scale(-1, -1, 1)} 和 {@code translate(0, -1.501, 0)}，那是给「y 向下」的原版
 * {@code EntityModel} 准备的；基岩几何是 <b>y 向上、原点在脚底</b>，直接画会上下颠倒。
 * 想用 {@code MobRenderer} 就得在 {@code renderToBuffer} 里做逆变换，而那个逆变换依赖
 * {@code scale()} 算出来的压缩比例（每个 tick 都不同），无法静态抵消。</p>
 *
 * <p>{@code EntityRenderer#render} 拿到的矩阵栈则<b>只有</b>「平移到实体脚下」，
 * 正是基岩几何要的坐标系 —— 女仆渲染器（{@code GeoReplacedEntityRenderer}）也是这么绕开
 * {@code LivingEntityRenderer} 的。阴影不受影响：它由 {@code EntityRenderDispatcher}
 * 按 {@code shadowRadius} / {@code shadowStrength} 画，与是否继承 {@code MobRenderer} 无关。</p>
 */
public class EntityMarisaYukkuriSlimeRender extends EntityRenderer<MagmaCube> {
    /** 上游 1.20.1 的贴图（128×128）。旧版 {@code textures/entity/marisa_yukkuri.png} 是 64×64，不能混用。 */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/bedrock/entity/marisa_yukkuri.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(TEXTURE);
    /** 对应 {@code assets/touhou_little_maid/models/bedrock/entity/marisa_yukkuri.json}。 */
    private static final String MODEL_NAME = "marisa_yukkuri";

    private final MagmaCubeRenderer vanillaRender;

    public EntityMarisaYukkuriSlimeRender(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.25F;
        this.vanillaRender = new MagmaCubeRenderer(context);
    }

    /** 岩浆怪自发光，原版 {@code MagmaCubeRenderer} 也是恒为满亮度（上游那半个渲染器同样返回 15）。 */
    @Override
    protected int getBlockLightLevel(MagmaCube cube, BlockPos pos) {
        return 15;
    }

    @Override
    public void render(MagmaCube cube, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        AnimatedGeoModel model = VanillaConfig.replaceSlimeModel() ? BedrockEntityModels.get(MODEL_NAME) : null;
        // 关掉开关、或模型文件坏掉时退回原版：宁可画成普通岩浆怪，也不要让它凭空消失
        if (model == null) {
            this.vanillaRender.render(cube, entityYaw, partialTicks, poseStack, buffer, packedLight);
            return;
        }
        if (cube.isInvisibleTo(Minecraft.getInstance().player)) {
            return;
        }

        // 阴影跟着体型走，否则大岩浆怪脚下只有一小块阴影（与上游一致）
        this.shadowRadius = 0.25F * (float) cube.getSize();

        poseStack.pushPose();
        // 与 LivingEntityRenderer#setupRotations 同一条式子：模型正面朝 -Z，实体朝 +Z
        poseStack.mulPose(Vector3f.YP.rotationDegrees(180.0F - entityYaw));
        this.applySquish(cube, poseStack, partialTicks);
        GeoBlockRenderer.render(model, TEXTURE, poseStack, buffer,
                packedLight, OverlayTexture.NO_OVERLAY, RENDER_TYPE);
        poseStack.popPose();
    }

    /**
     * 原版 {@code SlimeRenderer#scale} 逐字照抄（岩浆怪原版也用同一套）。
     *
     * <p>{@code squish} 是它弹跳时的挤压值，除以 {@code size * 0.5 + 1} 之后再做一次
     * 倒数变换，得到「横向压扁、纵向拉长」的形变。油库里沿用这条公式，所以手感和原版一致。</p>
     */
    private void applySquish(MagmaCube cube, PoseStack poseStack, float partialTicks) {
        poseStack.scale(0.999F, 0.999F, 0.999F);
        poseStack.translate(0.0F, 0.001F, 0.0F);
        float size = (float) cube.getSize();
        float squish = Mth.lerp(partialTicks, cube.oSquish, cube.squish) / (size * 0.5F + 1.0F);
        float inverse = 1.0F / (squish + 1.0F);
        poseStack.scale(inverse * size, 1.0F / inverse * size, inverse * size);
    }

    @Override
    public ResourceLocation getTextureLocation(MagmaCube cube) {
        return TEXTURE;
    }
}

