package com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.client.resource.BedrockBlockModels;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityAltar;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import static net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GROUND;

public class TileEntityAltarRenderer implements BlockEntityRenderer<TileEntityAltar> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/bedrock/block/altar.png");

    public TileEntityAltarRenderer(BlockEntityRendererProvider.Context render) {
        // 模型是懒加载的（BedrockBlockModels#get），构造函数里不碰资源管理器：
        // 方块实体渲染器在资源重载之前就会被创建，那时还读不到文件。
    }

    @Override
    public void render(TileEntityAltar te, float partialTicks, PoseStack poseStack, MultiBufferSource bufferIn, int combinedLightIn, int combinedOverlayIn) {
        if (te.isRender()) {
            poseStack.pushPose();
            this.setTranslateAndPose(te, poseStack);
            AnimatedGeoModel model = BedrockBlockModels.get("altar");
            poseStack.translate(0, BedrockBlockModels.floorOffset("altar"), 0);
            GeoBlockRenderer.render(model, TEXTURE, poseStack, bufferIn, combinedLightIn, combinedOverlayIn,
                    RenderType.entityTranslucent(TEXTURE));
            poseStack.popPose();
        }

        if (te.isCanPlaceItem() && !te.handler.getStackInSlot(0).isEmpty()) {
            ItemStack stack = te.handler.getStackInSlot(0);
            poseStack.pushPose();
            // 悬空 + 自转。逐字来自上游 1.20.1：
            //   time ∈ [0, 3599)，sin(time / 1800 · π) 的周期是 3600 ms（3.6 秒一个来回），振幅 0.1 格；
            //   Y 轴转速 time / 10 度 → 3.6 秒转满 360°（约 100°/秒）。
            // 把 asLong() 加进时间基准，是为了让每个祭坛的相位各不相同，
            // 否则一整排祭坛上的物品会齐步上下、齐步自转，看着很假。
            double time = (System.currentTimeMillis() + te.getBlockPos().asLong()) % 3600;
            poseStack.translate(0.5, 1.25 + Math.sin(time / 1800 * Math.PI) * 0.1, 0.5);
            poseStack.mulPose(Vector3f.YP.rotationDegrees((float) time / 10));
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, GROUND, combinedLightIn, combinedOverlayIn, poseStack, bufferIn, 0);
            poseStack.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(TileEntityAltar te) {
        return true;
    }

    /**
     * 把模型原点的世界坐标摆到祭坛结构的最底层（{@code posStart}）。
     *
     * <p>Y 之所以是 -3：{@code MultiBlockAltar#getCenterPos} 四个方向返回的 Y 分量全是 -3，
     * 而负责渲染的方块实体是 {@code posStart.subtract(getCenterPos(direction))}，
     * 也就是锚点比结构最底层<b>高 3 格</b>（贡品原木挂在 {@code y == 2} 也是同一套坐标）。
     * 基岩模型的原点就在结构底面，直接画在锚点上会整体高 3 格，所以要减掉锚点自身的高度。</p>
     *
     * <p>x/z 沿用旧值：水平方向实测是对的，它们只决定绕哪个点旋转、朝哪一面。</p>
     */

    private void setTranslateAndPose(TileEntityAltar te, PoseStack poseStack) {
        switch (te.getDirection()) {
            case SOUTH:
                poseStack.translate(1, -3, -3);
                poseStack.mulPose(Vector3f.YP.rotationDegrees(180));
                break;
            case EAST:
                poseStack.translate(-3, -3, 0);
                poseStack.mulPose(Vector3f.YP.rotationDegrees(270));
                break;
            case WEST:
                poseStack.translate(4, -3, 1);
                poseStack.mulPose(Vector3f.YP.rotationDegrees(90));
                break;
            case NORTH:
            default:
                poseStack.translate(0, -3, 4);
        }
    }
}
