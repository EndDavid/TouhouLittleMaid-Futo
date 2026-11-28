package com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.block.BlockSnackCabinet;
import com.github.tartaricacid.touhoulittlemaid.client.resource.BedrockBlockModels;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
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
import net.minecraft.world.level.block.state.BlockState;

/**
 * 零食柜的方块实体渲染器（上游 1.20.1 的 {@code TileEntitySnackCabinetRenderer}）。
 *
 * <p>几何与贴图走 {@link BedrockBlockModels} 管线。姿态与神社 / 野餐垫同一套：
 * 基岩几何 Y 向上、底面 y=0、水平居中，所以只要 {@code translate(0.5, floorOffset, 0.5)}
 * 再按朝向导转 —— <b>不要再补 {@code ZN(180)}</b>，也不要写 {@code T(0.5) R T(-0.5)}
 * （那是「绕点 (0.5,0,0.5) 旋转」，会让除朝南外的三个朝向整体偏半格）。</p>
 *
 * <p>{@code root} / {@code full} / {@code half} 三个骨骼互斥，每帧按
 * {@code BlockSnackCabinet#TYPE} 设置 {@code hidden}。</p>
 */
public class TileEntitySnackCabinetRenderer implements BlockEntityRenderer<TileEntitySnackCabinet> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TouhouLittleMaid.MOD_ID, "textures/bedrock/block/snack_cabinet.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(TEXTURE);
    /** 对应 {@code assets/touhou_little_maid/models/bedrock/block/snack_cabinet.json}。 */
    private static final String MODEL_NAME = "snack_cabinet";
    /** 空柜子顶上摆了「满」的食物时的高玻璃罩（TYPE == 1）。 */
    private static final String BONE_FULL = "full";
    /** 顶上摆了「半」的食物时的矮玻璃罩（TYPE == 2）。 */
    private static final String BONE_HALF = "half";

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
        BlockState state = cabinet.getBlockState();
        Direction facing = state.getValue(BlockSnackCabinet.FACING);
        // 三个骨骼互斥：只让与 TYPE 对应的那个参与渲染。
        // setHidden 会连子骨骼一起隐藏，而这三个都是叶子，所以等价于「画不画自己」。
        int type = state.getValue(BlockSnackCabinet.TYPE);
        setBoneVisible(model, BONE_FULL, type == BlockSnackCabinet.TYPE_FULL);
        setBoneVisible(model, BONE_HALF, type == BlockSnackCabinet.TYPE_HALF);
        poseStack.pushPose();
        // 姿态与神社 / 野餐垫完全一致。
        // 【不要在转完之后再 translate(-0.5, 0, -0.5)】
        // T(0.5) R T(-0.5) 是「绕点 (0.5, 0, 0.5) 旋转」，而模型自己已经居中在原点
        // —— 于是转 180° 时整个模型会被推出半格（见下面的算式）。
        poseStack.translate(0.5D, BedrockBlockModels.floorOffset(MODEL_NAME), 0.5D);
        poseStack.mulPose(Vector3f.YP.rotationDegrees(facing.toYRot() + 180.0F));
        GeoBlockRenderer.render(model, TEXTURE, poseStack, buffer, packedLight, packedOverlay, RENDER_TYPE);
        poseStack.popPose();
    }

    /**
     * 把一个命名骨骼设成可见 / 不可见。
     *
     * <p>模型实例是共享的（{@code BedrockBlockModels} 按名字缓存），而世界里可以同时
     * 存在一个空柜子与一个顶着蛋糕的柜子，所以这个可见性必须
     * <b>每一帧都重新设一遍</b>（不能只在初始化时设）。</p>
     */
    private static void setBoneVisible(AnimatedGeoModel model, String name, boolean visible) {
        AnimatedGeoBone bone = model.bones().get(name);
        if (bone != null && bone.isHidden() == visible) {
            bone.setHidden(!visible);
        }
    }
}

