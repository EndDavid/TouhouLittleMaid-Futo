package com.github.tartaricacid.touhoulittlemaid.client.renderer.entity;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * 什么都不画的实体渲染器。
 *
 * <p>给「没有外观、但必须是实体」的东西用 —— 目前只有座位 {@code EntitySit}：
 * 它是玩家 / 女仆坐下的锚点，本身不该被看见。</p>
 *
 * <p><b>为什么不能干脆不注册：</b>1.18.2 的 {@code EntityRenderDispatcher#getRenderer}
 * 对未注册类型返回 {@code null}，而渲染循环里并没有处处判空 —— 一旦某个实体类型没有
 * 渲染器，客户端在渲染那一帧就可能直接崩，而不是「看不见」。本移植版在 P 点实体上
 * 已经踩过同一个坑（见 {@code InitEntities.POWER_POINT} 的注释），这里直接按最保险的
 * 方式处理：注册一个空实现。</p>
 */
public class EmptyEntityRenderer extends EntityRenderer<Entity> {
    private static final ResourceLocation EMPTY =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/entity/empty.png");

    public EmptyEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        // 不投影、不收阴影：座位是个抽象锚点，在地上留一团影子会很怪
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(Entity entity) {
        return EMPTY;
    }
}

