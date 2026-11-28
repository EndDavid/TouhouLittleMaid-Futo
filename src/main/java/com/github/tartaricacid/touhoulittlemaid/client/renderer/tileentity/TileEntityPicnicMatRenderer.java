package com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.block.BlockPicnicMat;
import com.github.tartaricacid.touhoulittlemaid.client.resource.BedrockBlockModels;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityPicnicMat;
import com.mojang.blaze3d.vertex.PoseStack;
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
 * 野餐垫的渲染器，对应上游 1.20.1 的 {@code PicnicMatRender}。
 */
public class TileEntityPicnicMatRenderer implements BlockEntityRenderer<TileEntityPicnicMat> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/bedrock/block/picnic_mat.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(TEXTURE);
    /**
     * 六个摆盘位。数字来自上游那六次 {@code renderFood}，但 <b>z 换成了 {@code 1.5 - z}</b>：
     * 上游是在「{@code translate(0.5,1.5,0.5)} 之后、再 {@code ZN(180)}」的空间里给的坐标，
     * 而本移植版的基岩几何已经是 Y 向上、底面在 y=0（实测 bbox y[-0.013..0.625]，
     * 见启动日志），所以既不需要那 1.5 也不需要翻转 —— 那个 1.5 正好是翻转时被减掉的部分。
     */
    private static final float[][] FOOD_POS = {
            {-0.6f, -1.5f, 0.0875f},
            {0.15f, -1.2f, 0.3f},
            {0.55f, -1.6f, -0.1f},
            {-0.5f, 1.65f, -0.15f},
            {0.375f, 1.575f, -0.075f},
            {-0.05f, 1.2f, 0.25f},
    };
    public TileEntityPicnicMatRenderer(BlockEntityRendererProvider.Context context) {
        // 模型懒加载（BedrockBlockModels#get）：构造函数早于资源管理器就绪，那时读不到文件
    }
    @Override
    public void render(TileEntityPicnicMat picnicMat, float partialTick, PoseStack poseStack, MultiBufferSource bufferIn,
                       int combinedLightIn, int combinedOverlayIn) {
        BlockState blockState = picnicMat.getBlockState();
        if (!(blockState.getBlock() instanceof BlockPicnicMat)
                || !blockState.getValue(BlockPicnicMat.PART).isCenter()) {
            return;
        }
        AnimatedGeoModel model = BedrockBlockModels.get("picnic_mat");
        if (model == null) {
            return;
        }
        Direction facing = blockState.getValue(BlockPicnicMat.FACING);
        poseStack.pushPose();
        // 模型已经 Y 向上、底面在 y=0、水平居中（启动日志实测 bbox
        // x[-2.231..2.313] y[-0.013..0.625] z[-2.251..2.251]），
        // 所以摆放只需要「挪到方块中心 + 按朝向转」，与三张棋盘完全一样。
        // floorOffset 只压不抬，这里量出来是 -0.000，写了也不影响。
        poseStack.translate(0.5, BedrockBlockModels.floorOffset("picnic_mat"), 0.5);
        // 朝向角与上游等价：上游是 YN(180 - 2D*90)，在 Y 向上的同一空间里
        // 就是 YP(toYRot + 180)。不要照抄 BoardRenderUtil#applyFacing ——
        // 那个多带了一句「Z 轴朝向再转半圈」，是给按点击坐标对齐的棋盘用的。
        poseStack.mulPose(Vector3f.YP.rotationDegrees(facing.toYRot() + 180));
        // ① 装饰件：真放了东西就藏起来
        setHidden(model, "basketHide", picnicMat.isEmpty(0));
        setHidden(model, "breadHide", picnicMat.isEmpty(1));
        setHidden(model, "cakeHide", picnicMat.isEmpty(2));
        // ② 摆盘（同一姿态，坐标与模型同空间）
        for (int i = 0; i < FOOD_POS.length; i++) {
            this.renderFood(picnicMat, 3 + i, FOOD_POS[i], poseStack, bufferIn, combinedLightIn, combinedOverlayIn);
        }
        // ③ 垫子
        GeoBlockRenderer.render(model, TEXTURE, poseStack, bufferIn, combinedLightIn, combinedOverlayIn, RENDER_TYPE);
        poseStack.popPose();
    }
    /**
     * 骨骼存在才切换可见性：模型若改名 / 缺块，顶多装饰件不隐藏，不该抛异常。
     */
    private static void setHidden(AnimatedGeoModel model, String boneName, boolean hidden) {
        AnimatedGeoBone bone = model.bones().get(boneName);
        if (bone != null) {
            bone.setHidden(hidden);
        }
    }
    /**
     * 画一格食物。数量 ≥ 10 时额外叠几份（{@code count / 10}），每份按
     * {@code sin(i)} / {@code cos(i)} 抖一点位置与角度 —— 上游原样。
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
