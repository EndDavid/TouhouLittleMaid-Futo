package com.github.tartaricacid.touhoulittlemaid.mixin.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 抱着女仆时，不画玩家<b>手里那件物品</b>（第三人称视角）。
 *
 * <p>{@link ItemInHandRendererMixin} 管的是<b>第一人称</b>的手臂与手持物；
 * 第三人称的女仆是走玩家模型 + {@code ItemInHandLayer}，那条路不会被它拦到，
 * 于是玩家举着的东西（剑 / 镐 / 弓……）会从怀里的女仆身上穿出来。
 * 两手都用来环抱了，此时再挂一件物品既不合逻辑也难看，所以一并隐藏。</p>
 *
 * <p>判定同样用 {@link EntityMaid#isCarryingMaid}：上游只认
 * {@code getFirstPassenger()}，而乘客关系是两端各自复现的，客户端未必复现成功。</p>
 */
@Mixin(ItemInHandLayer.class)
public class ItemInHandLayerMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;"
                     + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
                     + "Lnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void futo$hideHeldItemWhileCarrying(PoseStack poseStack, MultiBufferSource bufferSource,
                                                int packedLight, LivingEntity entity,
                                                float limbSwing, float limbSwingAmount, float partialTicks,
                                                float ageInTicks, float netHeadYaw, float headPitch,
                                                CallbackInfo ci) {
        if (entity == Minecraft.getInstance().player && EntityMaid.isCarryingMaid(Minecraft.getInstance().player)) {
            ci.cancel();
        }
    }
}

