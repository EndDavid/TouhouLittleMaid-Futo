package com.github.tartaricacid.touhoulittlemaid.client.renderer.entity;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.model.NewEntityFairyModel;
import com.github.tartaricacid.touhoulittlemaid.entity.monster.EntityFairy;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * 「新女仆妖精」渲染器，移植自上游 1.18.2 {@code client/renderer/entity/NewEntityFairyRenderer}。
 *
 * <p>它只在 {@code MiscConfig#USE_NEW_MAID_FAIRY_MODEL} 为真时被
 * {@link EntityFairyRenderer#render} 委派渲染，负责把
 * {@link NewEntityFairyModel} 画出来 —— 也就是「新模型 / 旧模型」这组开关的另一半。</p>
 *
 * <p><b>与上游的一处防御性差异：</b>上游按 {@code getFairyTypeOrdinal()} 写了个
 * 18 分支的 {@code switch}，越界走 {@code default}。这里改成「贴图数组 + 越界回退 0」，
 * 与同目录的 {@link EntityFairyRenderer} 写法一致 —— 存档里的序数可能来自更早的版本，
 * 或被 {@code /data} 改坏，数组形式越界只会回退，不会因为某个 case 漏写而黑屏。</p>
 *
 * <p><b>为什么没有幼年形态：</b>1.18.2 上游只有这一套新模型；
 * {@code baby_maid_fairy.json} 是 1.20.1 才加的，与 1.18.2 的
 * {@code EntityFairy#isBaby()} 逻辑并不配套，本轮不引入。</p>
 */
public class NewEntityFairyRenderer extends MobRenderer<EntityFairy, NewEntityFairyModel> {
    /**
     * 18 种发色的新模型贴图，索引即 {@code FairyType} 的序数。
     *
     * <p>路径是 {@code textures/entity/new_maid_fairy/}，与上游 1.18.2 一致
     * （1.20.1 才挪到 {@code textures/bedrock/entity/} 下）。</p>
     */
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[18];
    /** Rick-rolling 彩蛋：名字叫 {@code rick} 的妖精换这张脸。 */
    private static final ResourceLocation TEXTURE_RICK =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/entity/new_maid_fairy/maid_fairy_rick.png");

    static {
        for (int i = 0; i < TEXTURES.length; i++) {
            TEXTURES[i] = new ResourceLocation(TouhouLittleMaid.MOD_ID,
                    "textures/entity/new_maid_fairy/maid_fairy_" + i + ".png");
        }
    }

    public NewEntityFairyRenderer(EntityRendererProvider.Context context) {
        super(context, new NewEntityFairyModel(context.bakeLayer(NewEntityFairyModel.LAYER)), 0.5F);
    }

    @Override
    protected void setupRotations(EntityFairy fairy, PoseStack poseStack, float ageInTicks,
                                  float rotationYaw, float partialTicks) {
        super.setupRotations(fairy, poseStack, ageInTicks, rotationYaw, partialTicks);
        // 悬空时整体前倾 8°，与旧模型那一路完全相同的手感
        if (!fairy.isOnGround()) {
            poseStack.mulPose(Vector3f.XN.rotation(8 * (float) Math.PI / 180.0F));
        }
    }

    @Override
    public ResourceLocation getTextureLocation(EntityFairy fairy) {
        String name = fairy.getName().getString().toLowerCase(Locale.ENGLISH);
        if (EntityFairy.RICK.equals(name)) {
            return TEXTURE_RICK;
        }
        int ordinal = fairy.getFairyTypeOrdinal();
        if (ordinal < 0 || ordinal >= TEXTURES.length) {
            return TEXTURES[0];
        }
        return TEXTURES[ordinal];
    }
}

