package com.github.tartaricacid.touhoulittlemaid.client.renderer.entity;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.model.EntityFairyModel;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MiscConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.monster.EntityFairy;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 女仆妖精渲染器，移植自上游（1.18.2）{@code client/renderer/entity/EntityFairyRenderer}。
 *
 * <p>它本身只负责<b>旧模型</b>（{@link EntityFairyModel}），并按
 * {@code MiscConfig#USE_NEW_MAID_FAIRY_MODEL} 决定要不要把整帧交给
 * {@link NewEntityFairyRenderer}（新模型）。这与上游一致：
 * 上游在这个类里持有 {@code newEntityFairyRenderer} 并在 {@code render} 开头分派，
 * 而不是在注册处二选一 —— 那样配置改了还得重启。</p>
 *
 * <p><b>悬空时的 8° 前倾：</b>上游用 {@code Vector3f.XN.rotation} 实现。1.18.2 的
 * {@code com.mojang.math.Vector3f} 是 Mojang 自己的类，{@code XN} 是「绕 X 轴负方向」的
 * 单位向量常量，{@code rotation(rad)} 返回对应的四元数。两个渲染器都要倾斜，
 * 所以两边各写一份（上游也是各写一份）。</p>
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

    /** 新模型那一路。构造时就会烘焙它自己的模型层，所以注册处必须先登记图层。 */
    private final NewEntityFairyRenderer newEntityFairyRenderer;

    public EntityFairyRenderer(EntityRendererProvider.Context context) {
        super(context, new EntityFairyModel(context.bakeLayer(EntityFairyModel.LAYER)), 0.5F);
        this.newEntityFairyRenderer = new NewEntityFairyRenderer(context);
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

    @Override
    public void render(EntityFairy fairy, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        if (MiscConfig.USE_NEW_MAID_FAIRY_MODEL.get()) {
            this.newEntityFairyRenderer.render(fairy, entityYaw, partialTicks, poseStack, buffer, packedLight);
        } else {
            super.render(fairy, entityYaw, partialTicks, poseStack, buffer, packedLight);
        }
    }
}
