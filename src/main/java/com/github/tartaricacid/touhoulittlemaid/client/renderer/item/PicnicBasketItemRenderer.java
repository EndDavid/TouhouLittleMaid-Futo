package com.github.tartaricacid.touhoulittlemaid.client.renderer.item;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.model.PicnicBasketIconModel;
import com.github.tartaricacid.touhoulittlemaid.client.model.PicnicBasketModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 野餐篮的物品图标，移植自上游 {@code client/renderer/tileentity/PicnicBasketRender}。
 *
 * <p>两种形态：</p>
 * <ul>
 *     <li><b>图形界面 / 物品栏</b>（{@code TransformType.GUI}）画一张扁平贴图
 *         （{@code textures/item/picnic_basket.png}，见 {@link PicnicBasketIconModel}）；</li>
 *     <li><b>手持 / 掉落 / 展示框</b>画真正的 3D 模型（{@code textures/entity/picnic_basket.png}）。</li>
 * </ul>
 * <p>上游用 Forge 的 {@code IItemRenderProperties#getItemStackRenderer} 返回一个
 * {@code BlockEntityWithoutLevelRenderer}；Fabric 的等价物是
 * {@link BuiltinItemRendererRegistry}（坐垫与手办柜用的是同一套）。</p>
 *
 * <h2>为什么用 {@code mode != GUI} 而不是枚举白名单</h2>
 * <p>{@code ItemTransforms.TransformType} 里 GUI 之外还有 {@code FIXED} / {@code GROUND} /
 * {@code HEAD} / {@code NONE} / 三种人称，它们都该画 3D 模型 —— 而且将来原版若再加类型，
 * 「默认画 3D」比「默认画 GUI 图标」更不容易出错。</p>
 *
 * <h2>与上游的一处差异</h2>
 * <p>图层是<b>第一次渲染时</b>才烘焙的。物品渲染器在 {@code BuiltinItemRendererRegistry}
 * 里只是个单例，构造时机早于 {@code Minecraft#getEntityModels()} 就绪，
 * 在那里 bakeLayer 会拿到一张空模型表。</p>
 */
public final class PicnicBasketItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/entity/picnic_basket.png");
    private static final ResourceLocation GUI_TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/item/picnic_basket.png");

    private PicnicBasketModel model;
    private PicnicBasketIconModel icon;

    private void initModels() {
        if (this.model != null) {
            return;
        }
        this.model = new PicnicBasketModel(
                Minecraft.getInstance().getEntityModels().bakeLayer(PicnicBasketModel.LAYER));
        this.icon = new PicnicBasketIconModel(
                Minecraft.getInstance().getEntityModels().bakeLayer(PicnicBasketIconModel.LAYER));
    }

    @Override
    public void render(ItemStack stack, ItemTransforms.TransformType mode, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // 物品图标渲染抛异常会直接崩在 ItemRenderer 里，没有上层会接住 ——
        // 画不出来最多是「物品栏里少个模型」，所以这里兜一层。
        try {
            this.initModels();
            if (mode == ItemTransforms.TransformType.GUI) {
                this.renderIcon(poseStack, bufferSource, packedLight, packedOverlay);
            } else {
                this.renderModel(poseStack, bufferSource, packedLight, packedOverlay);
            }
        } catch (Throwable e) {
            TouhouLittleMaid.LOGGER.error(
                    "[Futo] Picnic basket item renderer failed; the basket will render as a flat item", e);
        }
    }

    private void renderIcon(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5, 1.5, 0.5);
        poseStack.mulPose(Vector3f.ZN.rotationDegrees(180));
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(GUI_TEXTURE));
        this.icon.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }

    private void renderModel(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5, 1.5, 0.5);
        poseStack.mulPose(Vector3f.ZN.rotationDegrees(180));
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.model.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }
}

