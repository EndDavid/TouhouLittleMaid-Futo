package com.github.tartaricacid.touhoulittlemaid.client.renderer.item;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.model.PicnicBasketIconModel;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.GeoBlockRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.resource.BedrockBlockModels;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
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
 * 野餐篮的物品图标，对应上游 1.20.1 的 {@code PicnicBasketRender}。
 *
 * <p>两种形态，与上游一致：</p>
 * <ul>
 *     <li><b>图形界面 / 物品栏</b>（{@code TransformType.GUI}）画一张扁平贴图
 *         {@code textures/item/picnic_basket.png}（{@link PicnicBasketIconModel}）；</li>
 *     <li><b>手持 / 掉落 / 展示框</b>画 1.20.1 的 3D 基岩模型
 *         {@code models/bedrock/block/picnic_basket.json}（贴图 {@code textures/bedrock/block/picnic_basket.png}，
 *         与本模组 jar 里那一份逐字节相同）。</li>
 * </ul>
 *
 * <p>上游用 Forge 的 {@code IItemRenderProperties#getItemStackRenderer} 返回
 * {@code BlockEntityWithoutLevelRenderer}；Fabric 的等价物是
 * {@link BuiltinItemRendererRegistry}（坐垫与手办柜用的是同一套）。</p>
 *
 * <h2>为什么不照抄上游那两句 {@code translate(0.5, 1.5, 0.5)} + {@code ZN(180)}</h2>
 * <p>那两句是给「原版 {@code ModelPart} 空间」的旧模型用的（Y 向下、y=24 是地面）。
 * 本移植版的基岩管线已经处理过方向 —— 野餐垫实测 bbox 是
 * {@code y[-0.013..0.625]}（Y 向上、底面在 0），照抄那两句会把模型倒扣再抬高。
 * 所以这里按 {@link BedrockBlockModels#bounds} 量出的底面自己摆，与野餐垫渲染器同一个理由。</p>
 *
 * <h2>为什么模型与图标都懒加载</h2>
 * <p>物品渲染器在注册时只是个单例，构造时机早于 {@code Minecraft#getEntityModels()} 与
 * 资源管理器就绪，那时 {@code bakeLayer} 会拿到空模型表、{@code BedrockBlockModels#get}
 * 也读不到文件。所以第一次渲染时才初始化。</p>
 */
public final class PicnicBasketItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/bedrock/block/picnic_basket.png");
    private static final ResourceLocation GUI_TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/item/picnic_basket.png");
    private static final RenderType MODEL_RENDER_TYPE = RenderType.entityCutoutNoCull(TEXTURE);
    private static final RenderType ICON_RENDER_TYPE = RenderType.entityCutoutNoCull(GUI_TEXTURE);
    private PicnicBasketIconModel icon;
    @Override
    public void render(ItemStack stack, ItemTransforms.TransformType mode, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // 物品图标渲染抛异常会直接崩在 ItemRenderer 里，没有上层会接住 ——
        // 画不出来最多是「物品栏里少个模型」，所以这里兜一层。
        try {
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
        if (this.icon == null) {
            this.icon = new PicnicBasketIconModel(
                    Minecraft.getInstance().getEntityModels().bakeLayer(PicnicBasketIconModel.LAYER));
        }
        poseStack.pushPose();
        poseStack.translate(0.5, 1.5, 0.5);
        poseStack.mulPose(Vector3f.ZN.rotationDegrees(180));
        VertexConsumer buffer = bufferSource.getBuffer(ICON_RENDER_TYPE);
        this.icon.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }
    private void renderModel(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        AnimatedGeoModel model = BedrockBlockModels.get("picnic_basket");
        if (model == null) {
            // 模型缺失时退回扁平图标：至少背包里还能看见它是什么
            this.renderIcon(poseStack, bufferSource, packedLight, packedOverlay);
            return;
        }
        poseStack.pushPose();
        // 只把模型底面放到物品空间的原点、水平居中：
        // 上游那两句 translate(0.5,1.5,0.5) + ZN(180) 是给「原版 ModelPart 空间」的旧模型用的
        // （那个空间 Y 向下、y=24 是地面）。本移植版的基岩管线在换算时已经处理过方向，
        // 模型出来就是 Y 向上、底面在 minY（实测野餐垫是 -0.013），照抄那两句会把它倒扣并抬高。
        // 于是这里按包围盒自己摆，换任何尺寸的篮子都不会错位。
        float[] bounds = BedrockBlockModels.bounds("picnic_basket");
        float bottom = bounds == null ? 0.0F : bounds[1];
        poseStack.translate(0.5, -bottom, 0.5);
        GeoBlockRenderer.render(model, TEXTURE, poseStack, bufferSource, packedLight, packedOverlay, MODEL_RENDER_TYPE);
        poseStack.popPose();
    }
}
