package com.github.tartaricacid.touhoulittlemaid.client.renderer.entity;

import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoChairEntity;
import com.github.tartaricacid.touhoulittlemaid.client.resource.ChairModelRegistry;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.ChairModelInfo;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoReplacedEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * 坐垫渲染器，合并了上游的 {@code EntityChairRenderer} 与 {@code GeckoEntityChairRenderer}。
 *
 * <p><b>为什么合并。</b>上游分两层是因为它要同时支持「旧版基岩模型 + JS 动画」和
 * 「Gecko 模型」两条完全不同的渲染路径。本移植版没有 JS 动画引擎，旧版坐垫模型也统一
 * 转成了 {@link com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoModel}，
 * 两条路径在 {@link GeoReplacedEntityRenderer} 里已经合一，再拆一层只会多一个空壳。</p>
 *
 * <p>「蹲下 + 手持坐垫碰撞箱显示器」时只画红色线框，这条行为与上游一致，
 * 分流位置也一致（在 {@code render} 最前面）。</p>
 */
public class EntityChairRenderer extends GeoReplacedEntityRenderer<EntityChair, GeckoChairEntity> {
    /**
     * 物品栏 / 手持图标渲染期间临时关掉碰撞箱显示，否则图标里会多出一个红框。
     */
    public static boolean renderHitBox = true;

    public EntityChairRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public GeckoChairEntity getAnimatableEntity(EntityChair entity) {
        return entity.getAnimatableEntity();
    }

    @Override
    public void render(EntityChair chair, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        if (renderHitBox && canShowHitBox(Minecraft.getInstance().player)) {
            renderHitBox(chair, poseStack, bufferSource);
            return;
        }
        // 逐帧更新这条坐垫用的是哪个模型 —— model_id 是同步数据，随时可能被换肤包改掉
        ChairModelInfo info = ChairModelRegistry.getInfo(chair.getModelId()).orElse(null);
        GeckoChairEntity animatable = getAnimatableEntity(chair);
        if (animatable != null) {
            animatable.setChair(info);
        }
        super.render(chair, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private boolean canShowHitBox(@Nullable LocalPlayer player) {
        if (player == null || !player.isShiftKeyDown()) {
            return false;
        }
        return player.getMainHandItem().getItem() == InitItems.CHAIR_SHOW;
    }

    private void renderHitBox(EntityChair chair, PoseStack poseStack, MultiBufferSource bufferSource) {
        AABB aabb = chair.getBoundingBox().move(-chair.getX(), -chair.getY(), -chair.getZ());
        LevelRenderer.renderLineBox(poseStack, bufferSource.getBuffer(RenderType.lines()), aabb, 1.0F, 0.0F, 0.0F, 1.0F);
    }

    @Override
    public float getHeightScale(EntityChair entity) {
        return ChairModelRegistry.getInfo(entity.getModelId())
                .map(ChairModelInfo::getRenderEntityScale).orElse(1.0F);
    }

    @Override
    public float getWidthScale(EntityChair entity) {
        return ChairModelRegistry.getInfo(entity.getModelId())
                .map(ChairModelInfo::getRenderEntityScale).orElse(1.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(EntityChair entity) {
        return ChairModelRegistry.getInfo(entity.getModelId())
                .map(ChairModelInfo::getTexture)
                .orElseGet(() -> super.getTextureLocation(entity));
    }
}

