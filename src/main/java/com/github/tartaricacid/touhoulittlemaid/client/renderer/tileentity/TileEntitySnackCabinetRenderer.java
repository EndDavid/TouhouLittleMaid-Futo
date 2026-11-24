package com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.block.BlockSnackCabinet;
import com.github.tartaricacid.touhoulittlemaid.client.resource.BedrockBlockModels;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntitySnackCabinet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * 零食柜的方块实体渲染器（上游 1.20.1 的 {@code TileEntitySnackCabinetRenderer}）。
 *
 * <p>几何直接读 {@code models/bedrock/block/snack_cabinet.json}、贴图是
 * {@code textures/bedrock/block/snack_cabinet.png}，走本移植版已有的
 * {@link BedrockBlockModels} 管线（与祭坛 / 神社 / 三张棋盘 / 野餐垫同一条路）。
 * 那条管线已经把 texel→格、X 轴翻转、负 inflate、零厚度面、骨骼层级都处理过了，
 * 所以这里既不需要再手抄几百行 {@code addBox}，也不需要 {@code ZN(180)} 之类的手工姿态修正。</p>
 *
 * <p><b>朝向</b>：只按 {@code FACING} 绕 Y 轴转 —— 基岩几何的原点就在方块底面中心
 * （与 {@code models/block/snack_cabinet.json} 里那个只有 particle 的空模型对应），
 * 所以不需要额外的平移。</p>
 */
public class TileEntitySnackCabinetRenderer implements BlockEntityRenderer<TileEntitySnackCabinet> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/bedrock/block/snack_cabinet.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(TEXTURE);
    /** 对应 {@code assets/touhou_little_maid/models/bedrock/block/snack_cabinet.json}。 */
    private static final String MODEL_NAME = "snack_cabinet";

    public TileEntitySnackCabinetRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TileEntitySnackCabinet cabinet, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        AnimatedGeoModel model = BedrockBlockModels.get(MODEL_NAME);
        if (model == null) {
            // 模型缺失/解析失败：直接不画。硬抛异常会让整个世界的方块实体渲染循环挂掉。
            return;
        }
        Direction facing = cabinet.getBlockState().getValue(BlockSnackCabinet.FACING);
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Vector3f.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        GeoBlockRenderer.render(model, TEXTURE, poseStack, buffer, packedLight, packedOverlay, RENDER_TYPE);
        poseStack.popPose();
    }
}

