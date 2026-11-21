package com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.block.BlockPicnicMat;
import com.github.tartaricacid.touhoulittlemaid.client.model.PicnicMatModel;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityPicnicMat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 野餐垫的渲染器，移植自上游 1.18.2 {@code client/renderer/tileentity/PicnicMatRender}。
 *
 * <p><b>只画中心格</b>（{@code PART == CENTER}）：整张垫子是一个模型，
 * 挂在边缘格上会画出 25 份互相重叠的垫子。边缘格的方块实体存在的唯一理由
 * 是「拆掉任意一格时能找回中心」（见 {@code BlockPicnicMat#handlePicnicMatRemove}）。</p>
 *
 * <h2>三层内容</h2>
 * <ol>
 *     <li><b>装饰件</b>：模型里的 {@code basketHide / breadHide / cakeHide} 三组，
 *         对应格子里<b>真的</b>有东西时隐藏 —— 否则会与下面那层物品模型叠在一起；</li>
 *     <li><b>物品</b>：{@code 3..8} 六格按上游的固定坐标摆盘，超过 10 个时叠一小摞；</li>
 *     <li><b>垫子本身</b>：最后画，避免半透明边缘把物品盖掉。</li>
 * </ol>
 */
public class TileEntityPicnicMatRenderer implements BlockEntityRenderer<TileEntityPicnicMat> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/entity/picnic_mat.png");

    /**
     * 六个摆盘位的坐标，与 {@code PicnicMatModel} 里那 6 个空位一一对应。
     * 前三个是 {@code z<0} 那侧，后三个是另一侧；最后一个（8 号）稍微高一点、靠里一点。
     */
    private static final float[][] FOOD_POS = {
            {-0.6f, -1.5f, 1.4125f},
            {0.15f, -1.2f, 1.4125f},
            {0.55f, -1.6f, 1.4125f},
            {-0.5f, 1.65f, 1.4125f},
            {0.375f, 1.575f, 1.4125f},
            {-0.05f, 1.2f, 1.25f},
    };

    private final PicnicMatModel model;

    public TileEntityPicnicMatRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new PicnicMatModel(context.bakeLayer(PicnicMatModel.LAYER));
    }

    @Override
    public void render(TileEntityPicnicMat picnicMat, float partialTick, PoseStack poseStack, MultiBufferSource bufferIn,
                       int combinedLightIn, int combinedOverlayIn) {
        BlockState blockState = picnicMat.getBlockState();
        if (!(blockState.getBlock() instanceof BlockPicnicMat) || !blockState.getValue(BlockPicnicMat.PART).isCenter()) {
            return;
        }
        Direction facing = blockState.getValue(BlockPicnicMat.FACING);
        poseStack.pushPose();
        poseStack.translate(0.5, 1.5, 0.5);
        poseStack.mulPose(Vector3f.ZN.rotationDegrees(180));
        poseStack.mulPose(Vector3f.YN.rotationDegrees(180 - facing.get2DDataValue() * 90));

        // ① 装饰件：格子里有真食物就藏起来
        this.model.getHideModel().getChild("basketHide").visible = !picnicMat.isEmpty(0);
        this.model.getHideModel().getChild("breadHide").visible = !picnicMat.isEmpty(1);
        this.model.getHideModel().getChild("cakeHide").visible = !picnicMat.isEmpty(2);

        // ② 摆盘
        for (int i = 0; i < FOOD_POS.length; i++) {
            this.renderFood(picnicMat, 3 + i, FOOD_POS[i], poseStack, bufferIn, combinedLightIn, combinedOverlayIn);
        }

        // ③ 垫子
        VertexConsumer buffer = bufferIn.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.model.renderToBuffer(poseStack, buffer, combinedLightIn, combinedOverlayIn, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }

    /**
     * 画一格食物。
     *
     * <p>数量 ≥ 10 时额外叠几份模型（{@code count / 10} 份），每份按
     * {@code sin(i)} / {@code cos(i)} 抖一点位置与角度 —— 上游原样，看起来像「一摞」。</p>
     */
    private void renderFood(TileEntityPicnicMat picnicMat, int slotId, float[] pos, PoseStack poseStack,
                            MultiBufferSource bufferIn, int combinedLightIn, int combinedOverlayIn) {
        ItemStack storageItem = picnicMat.getStorageItem(slotId);
        if (storageItem.isEmpty()) {
            return;
        }
        int count = storageItem.getCount();
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        poseStack.pushPose();
        poseStack.mulPose(Vector3f.XN.rotationDegrees(90));
        poseStack.translate(pos[0], pos[1], pos[2]);
        poseStack.scale(0.4f, 0.4f, 0.4f);
        itemRenderer.renderStatic(storageItem, ItemTransforms.TransformType.FIXED,
                combinedLightIn, combinedOverlayIn, poseStack, bufferIn, 0);
        if (count >= 10) {
            int stackCount = count / 10;
            for (int i = 0; i < stackCount; i++) {
                poseStack.translate(Math.sin(i) * 0.05, Math.cos(i) * 0.03, -0.07);
                poseStack.mulPose(Vector3f.ZN.rotationDegrees((float) Math.cos(i) * 60));
                itemRenderer.renderStatic(storageItem, ItemTransforms.TransformType.FIXED,
                        combinedLightIn, combinedOverlayIn, poseStack, bufferIn, 0);
            }
        }
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(TileEntityPicnicMat te) {
        return true;
    }
}

