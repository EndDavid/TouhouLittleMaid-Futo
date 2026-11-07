package com.github.tartaricacid.touhoulittlemaid.client.renderer.entity;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityExtinguishingAgent;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 灭火剂实体的渲染器，移植自上游 {@code client/renderer/entity/EntityExtinguishingAgentRenderer}。
 *
 * <p><b>这个类必须存在。</b>1.18.2 的 {@code EntityRenderDispatcher#getRenderer} 对未注册
 * 渲染器的类型<b>直接返回 null</b>（没有 fallback），而 {@code LevelRenderer} 每帧都会调
 * {@code shouldRender(entity, ...)} 并立即解引用该返回值 —— 于是实体一旦进入视野就
 * {@code NullPointerException} 崩客户端。</p>
 *
 * <p>灭火剂本身没有模型：它的视觉效果全部由 {@code baseTick} 里生成的
 * {@code ParticleTypes.CLOUD} 粒子承担，因此这里只提供一个什么都不画的空渲染器
 * （与上游一致，贴图返回 {@code null}）。</p>
 */
public class EntityExtinguishingAgentRenderer extends EntityRenderer<EntityExtinguishingAgent> {
    public EntityExtinguishingAgentRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Nullable
    @Override
    public ResourceLocation getTextureLocation(EntityExtinguishingAgent entity) {
        return null;
    }
}

