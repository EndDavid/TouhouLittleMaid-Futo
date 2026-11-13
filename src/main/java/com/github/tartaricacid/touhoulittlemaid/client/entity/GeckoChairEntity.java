package com.github.tartaricacid.touhoulittlemaid.client.entity;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.resource.ChairModelRegistry;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.ChairModelInfo;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.AnimatableEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.PlayState;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.AnimationBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.ILoopType;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.controller.AnimationController;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.event.predicate.AnimationEvent;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.resource.GeckoLibCache;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * 坐垫的 Gecko 动画宿主，移植自上游 {@code client/entity/GeckoChairEntity}。
 *
 * <p>坐垫动画用 {@code parallelN} 这种「八条并行轨道」的约定：{@code n = 0..7} 各一条
 * 控制器，全部同时播放。这样模型作者可以把「轮子转」「猫尾摆」等互不相干的循环动画
 * 拆到不同轨道里，不需要在同一个时间轴上对拍。</p>
 *
 * <p><b>与上游的一处修正：</b>上游的兜底模型 id 写的是 {@code touhou_little_maid:fox_miko}，
 * 那是<b>女仆</b>模型而不是坐垫模型，一旦真的走到兜底分支就会把一只女仆画成坐垫。
 * 这里改成默认坐垫 {@code touhou_little_maid:cushion}。</p>
 */
public class GeckoChairEntity extends AnimatableEntity<EntityChair> {
    private static final ResourceLocation FALLBACK_MODEL =
            new ResourceLocation(ChairModelRegistry.DEFAULT_CHAIR_ID);
    private static final ResourceLocation FALLBACK_TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/entity/empty.png");
    private static final int FPS = 30;
    private static final int PARALLEL_CONTROLLERS = 8;

    private ChairModelInfo chairInfo;

    public GeckoChairEntity(EntityChair entity) {
        super(entity, FPS);
        registerControllers();
    }

    private void registerControllers() {
        for (int i = 0; i < PARALLEL_CONTROLLERS; i++) {
            String controllerName = String.format("parallel_%d_controller", i);
            String animationName = String.format("parallel%d", i);
            addAnimationController(new AnimationController<>(this, controllerName, 0,
                    e -> predicateParallel(e, animationName)));
        }
    }

    @Override
    public ResourceLocation getModelLocation() {
        if (this.chairInfo != null) {
            ResourceLocation id = this.chairInfo.getModelId();
            if (id != null && GeckoLibCache.getInstance().getGeoModels().containsKey(id)) {
                return id;
            }
        }
        return FALLBACK_MODEL;
    }

    @Override
    public ResourceLocation getTextureLocation() {
        return this.chairInfo != null ? this.chairInfo.getTexture() : FALLBACK_TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationFileLocation() {
        if (this.chairInfo != null) {
            ResourceLocation id = this.chairInfo.getModelId();
            if (id != null && GeckoLibCache.getInstance().getAnimations().containsKey(id)) {
                return id;
            }
        }
        return FALLBACK_MODEL;
    }

    public ChairModelInfo getChairInfo() {
        return this.chairInfo;
    }

    public void setChair(ChairModelInfo chairInfo) {
        this.chairInfo = chairInfo;
    }

    private PlayState predicateParallel(AnimationEvent<GeckoChairEntity> event, String animationName) {
        if (Minecraft.getInstance().isPaused()) {
            return PlayState.STOP;
        }
        event.getController().setAnimation(new AnimationBuilder()
                .addAnimation(animationName, ILoopType.EDefaultLoopTypes.LOOP));
        return PlayState.CONTINUE;
    }
}
