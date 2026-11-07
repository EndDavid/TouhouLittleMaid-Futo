package com.github.tartaricacid.touhoulittlemaid.client.renderer.entity;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.model.EntityFairyModel;
import com.github.tartaricacid.touhoulittlemaid.entity.monster.EntityFairy;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 女仆妖精渲染器，移植自上游（1.18.2）{@code client/renderer/entity/EntityFairyRenderer}。
 *
 * <p><b>与上游的差异：</b>上游按配置 {@code UseNewMaidFairyModel} 在
 * 「旧模型 / 新模型 / 幼年模型」之间三选一分派渲染器。本移植版<b>只实现了旧模型</b> ——
 * 新模型是 {@code simplebedrockmodel} 的 JSON 模型，而工作区里只有它的贴图
 * （{@code textures/entity/new_maid_fairy/*.png}，128×128），没有对应的 JSON。
 * 硬接会直接抛「找不到模型」。因此这里的配置项被忽略，统一走旧模型；等 JSON 补齐后，
 * 再把 {@code NewEntityFairyRenderer} 接上即可，选择逻辑本身不需要改。</p>
 *
 * <p><b>悬空时的 8° 前倾：</b>上游用 {@code Vector3f.XN.rotation} 实现。1.18.2 的
 * {@code com.mojang.math.Vector3f} 是 Mojang 自己的类，{@code XN} 是「绕 X 轴负方向」的
 * 单位向量常量，{@code rotation(rad)} 返回对应的四元数。</p>
 */
public class EntityFairyRenderer extends MobRenderer<EntityFairy, EntityFairyModel> {
    /**
     * 18 种发色的贴图，索引即 {@code FairyType} 的序数。
     */
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[18];

    static {
        for (int i = 0; i < TEXTURES.length; i++) {
            TEXTURES[i] = new ResourceLocation(TouhouLittleMaid.MOD_ID,
                    "textures/entity/maid_fairy/maid_fairy_" + i + ".png");
        }
    }

    public EntityFairyRenderer(EntityRendererProvider.Context context) {
        super(context, new EntityFairyModel(context.bakeLayer(EntityFairyModel.LAYER)), 0.5F);
    }

    @Override
    protected void setupRotations(EntityFairy fairy, PoseStack poseStack, float ageInTicks,
                                  float rotationYaw, float partialTicks) {
        super.setupRotations(fairy, poseStack, ageInTicks, rotationYaw, partialTicks);
        if (!fairy.isOnGround()) {
            poseStack.mulPose(Vector3f.XN.rotation(8 * (float) Math.PI / 180.0F));
        }
    }

    @Override
    public ResourceLocation getTextureLocation(EntityFairy fairy) {
        int ordinal = fairy.getFairyTypeOrdinal();
        // 存档里的序数可能来自更早的版本/被 /data 改坏，越界时回退 0 而不是抛异常
        if (ordinal < 0 || ordinal >= TEXTURES.length) {
            return TEXTURES[0];
        }
        return TEXTURES[ordinal];
    }
}

