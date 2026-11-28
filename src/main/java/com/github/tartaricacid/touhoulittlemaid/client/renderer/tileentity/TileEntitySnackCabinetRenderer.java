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
 * <p>几何直接读 {@code models/bedrock/block/snack_cabinet.json}、贴图是
 * {@code textures/bedrock/block/snack_cabinet.png}，走本移植版已有的
 * {@link BedrockBlockModels} 管线（与祭坛 / 神社 / 三张棋盘 / 野餐垫同一条路）。
 * 那条管线已经把 texel→格、X 轴翻转、负 inflate、零厚度面、骨骼层级都处理过了，
 * 所以这里既不需要再手抄几百行 {@code addBox}，也不需要 {@code ZN(180)} 之类的手工姿态修正。</p>
 *
 * <p><b>朝向</b>：只按 {@code FACING} 绕 Y 轴转 —— 基岩几何的原点就在方块底面中心
 * （与 {@code models/block/snack_cabinet.json} 里那个只有 particle 的空模型对应），
 * 所以不需要额外的平移。</p>
 *
 * <h2>为什么要按 TYPE 隐藏骨骼</h2>
 *
 * <p>模型里有三个骨骼：{@code root}（柜体本身，恰好 1 格高）、
 * {@code full}（高玻璃罩，顶到 2.11 格）与 {@code half}（矮玻璃罩，顶到 1.94 格）。
 * 它们是<b>互斥</b>的三个外观，取决于柜顶上摆了什么（见
 * {@code BlockSnackCabinet#TYPE}）。
 * 上游在 {@code render} 里直接改 {@code BedrockPart.visible}：
 * {@code TYPE == 1} 只显示 {@code full}、{@code TYPE == 2} 只显示 {@code half}、
 * 其余两个都不显示（反编译 1.20.1 的字节码可见）。</p>
 *
 * <p>本移植版最初少了这一步，于是三个骨骼全画了 —— 表现就是
 * 「空柜子也竖着两格高的玻璃罩」。</p>
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
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Vector3f.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(-0.5D, 0.0D, -0.5D);
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

