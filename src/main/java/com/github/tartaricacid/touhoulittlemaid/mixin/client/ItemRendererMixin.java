package com.github.tartaricacid.touhoulittlemaid.mixin.client;

import com.github.tartaricacid.touhoulittlemaid.client.renderer.item.PerspectiveBakedModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让 {@link PerspectiveBakedModel} 真正按视角切换到 3D 手持模型。
 *
 * <h2>为什么必须在这里做</h2>
 *
 * <p>原版 {@code ItemRenderer#render} 的流程是：</p>
 *
 * <pre>
 * ItemTransforms transforms = model.getTransforms();
 * transforms.getTransform(type).apply(leftHand, poseStack);   // 视角变换
 * ...
 * this.renderModelLists(model, ...);                          // 画 quads
 * </pre>
 *
 * <p>「变换」和「几何」都取自同一个 {@code model}，而 {@code getQuads} 拿不到
 * {@code type}。所以只在 {@code BakedModel} 里做包装是无法二选一的
 * （上游能这么做，是因为它覆写的是 Forge 给 {@code BakedModel} 加的
 * {@code handlePerspective(TransformType, PoseStack)} 扩展方法，原版没有）。</p>
 *
 * <p>这个注入点是唯一同时持有 {@code ItemStack}（判断是不是御币）与
 * {@code TransformType}（判断是不是 GUI）的地方。做法是在 HEAD 里拿选好的模型
 * <b>重新调用一次</b> {@code render} 然后取消原调用；第二次调用时
 * {@code model} 已经不是 {@link PerspectiveBakedModel}，注入不再介入，递归只有一层。</p>
 */
@Mixin(ItemRenderer.class)
public class ItemRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemTransforms$TransformType;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void futo$renderPerspectiveModel(ItemStack stack, ItemTransforms.TransformType type, boolean leftHand,
                                             PoseStack poseStack, MultiBufferSource bufferSource,
                                             int light, int packedOverlay, BakedModel model, CallbackInfo ci) {
        if (model instanceof PerspectiveBakedModel perspective) {
            BakedModel picked = perspective.pickFor(type);
            // 用选中的模型再走一遍原方法；picked 不是包装器，不会再次进入这个分支
            ((ItemRenderer) (Object) this).render(stack, type, leftHand, poseStack, bufferSource,
                    light, packedOverlay, picked);
            ci.cancel();
        }
    }
}

