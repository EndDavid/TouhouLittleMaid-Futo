package com.github.tartaricacid.touhoulittlemaid.mixin.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 抱着女仆时隐藏第一人称的手 / 手持物 —— 对应上游 {@code CarryMaidHideArmEvent}。
 *
 * <p>上游原来的实现是监听 Forge 的 {@code RenderHandEvent} 并 {@code setCanceled(true)}：</p>
 *
 * <pre>
 * if (player.getFirstPassenger() instanceof EntityMaid) {
 *     event.setCanceled(true);
 * }
 * </pre>
 *
 * <p>Fabric 没有对应的「渲染手」事件，所以这里直接注入
 * {@code ItemInHandRenderer#renderHandsWithItems} 的 HEAD 并取消 ——
 * 那是第一人称手臂 + 手持物唯一的绘制入口，语义与上游逐字等价。</p>
 *
 * <p><b>为什么必须取消：</b>抱着女仆时，那只手（以及手里的东西）会被画在
 * 摄像机正前方、和女仆的身体交叉 —— 第一人称看起来就是「女仆糊在镜头上」。
 * 上游正是因为这一点才把整只手关掉的。</p>
 *
 * <p>判定用 {@link EntityMaid#isCarryingMaid}：上游只看 {@code getFirstPassenger()}，
 * 而乘客关系是两端各自复现的，客户端未必复现成功（见那里的说明）。</p>
 */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @Inject(method = "renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;"
                     + "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;"
                     + "Lnet/minecraft/client/player/LocalPlayer;I)V",
            at = @At("HEAD"), cancellable = true)
    private void futo$hideHandsWhileCarryingMaid(float partialTicks, PoseStack poseStack,
                                                 MultiBufferSource.BufferSource bufferSource,
                                                 LocalPlayer player, int combinedLight, CallbackInfo ci) {
        if (EntityMaid.isCarryingMaid(player)) {
            ci.cancel();
        }
    }
}

