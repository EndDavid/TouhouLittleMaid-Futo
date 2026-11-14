package com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * 把 {@link AnimatedGeoModel} 当静态模型画出来的最小渲染器。
 *
 * <p>{@link IGeoRenderer} 的方法几乎全是 {@code default}，这里只需要提供
 * 「当前 buffer」和「贴图」两个查询接口，其余（逐骨骼矩阵、逐面顶点、
 * 法线与光照打包）全部复用实体那条已经调好的实现 —— 这一点很关键，
 * 那些面法线的处理在实体渲染上已经被反复验证过（见
 * {@code IGeoRenderer#renderCube} 的注释），重写一遍只会引入新 bug。</p>
 *
 * <p>「动画对象」这个类型参数在这里就是纹理本身：没有实体、没有状态，
 * 渲染器只需要知道用哪张贴图。</p>
 *
 * <h2>{@link #measureBounds} 为什么必须存在</h2>
 *
 * <p>基岩方块模型的坐标原点<b>不是</b>统一的：神社的原点在模型底面，
 * 而祭坛的 cubes 里有一批带大角度旋转的骨骼（例如 {@code rotation: [90, 87.5, 90]}），
 * 它们的 {@code origin} 落在很歪的地方，只有把骨骼变换算进去才知道真正的包围盒。
 * 直接读 JSON 里的 {@code origin} 会得到完全错误的上下范围。</p>
 *
 * <p>所以这里不猜：用一个只记录坐标的 {@link VertexConsumer} 走一遍
 * <b>真正的渲染路径</b>（复用 {@code renderRecursively}，同一套骨骼矩阵），
 * 得到的就是模型在方块空间里的真实包围盒。
 * 有了它，「把模型底面摆在方块底面」就是一句 {@code translate(0, -minY, 0)}。</p>
 */
public final class GeoBlockRenderer implements IGeoRenderer<ResourceLocation> {
    private static final GeoBlockRenderer INSTANCE = new GeoBlockRenderer();

    private GeoBlockRenderer() {
    }

    /**
     * 渲染一个静态方块模型。
     *
     * @param model     模型，{@code null} 时直接返回（模型缺失不应让渲染循环崩掉）
     * @param texture   贴图
     * @param poseStack 已经由调用方摆好位置的矩阵栈
     * @param type      渲染类型，如 {@code entityTranslucent} / {@code entityCutoutNoCull}
     */
    public static void render(@Nullable AnimatedGeoModel model, ResourceLocation texture,
                              PoseStack poseStack, MultiBufferSource bufferSource,
                              int packedLight, int packedOverlay, RenderType type) {
        if (model == null) {
            return;
        }
        INSTANCE.render(model, texture, 0, type, poseStack, bufferSource, null,
                packedLight, packedOverlay, 1, 1, 1, 1);
    }

    /**
     * 只渲染一个命名骨骼（含其子树）。
     *
     * <p>棋盘棋子就是这么画的：一整个 {@code cchess_pieces} 模型里放了 15 个互不相干的
     * 骨骼（{@code ShuaiRed} / {@code JuBlack} … {@code Selected}），每格挑一个画。
     * 拆成 15 个独立模型文件也行，但那要多维护 14 份几何与贴图坐标。</p>
     *
     * @return 骨骼不存在时返回 {@code false}（调用方可以直接跳过这一格）
     */
    public static boolean renderBone(@Nullable AnimatedGeoModel model, String boneName, ResourceLocation texture,
                                     PoseStack poseStack, MultiBufferSource bufferSource,
                                     int packedLight, int packedOverlay, RenderType type) {
        if (model == null) {
            return false;
        }
        AnimatedGeoBone bone = model.bones().get(boneName);
        if (bone == null) {
            return false;
        }
        INSTANCE.renderRecursively(bone, poseStack, bufferSource.getBuffer(type),
                packedLight, packedOverlay, 1, 1, 1, 1);
        return true;
    }

    /**
     * 量出模型在方块空间里的包围盒（单位：格）。
     *
     * @return {@code [minX, minY, minZ, maxX, maxY, maxZ]}；模型为空时返回全 0
     */
    public static float[] measureBounds(AnimatedGeoModel model) {
        BoundsConsumer consumer = new BoundsConsumer();
        PoseStack poseStack = new PoseStack();
        for (AnimatedGeoBone bone : model.topLevelBones()) {
            INSTANCE.renderRecursively(bone, poseStack, consumer, 0, 0, 1, 1, 1, 1);
        }
        return consumer.toArray();
    }

    @Override
    public MultiBufferSource getCurrentRTB() {
        // 默认的 render() 先 setCurrentRTB(bufferSource)，再从 bufferSource 取 buffer，
        // 全程不读这个方法，所以给 null 就够。
        return null;
    }

    @Override
    public ResourceLocation getTextureLocation(ResourceLocation texture) {
        return texture;
    }

    /**
     * 只记录顶点范围、不产出任何几何的 {@link VertexConsumer}。
     *
     * <p>各个 {@code uv} / {@code color} / {@code normal} 方法一律空实现：
     * 我们只关心 {@code vertex(x, y, z)}，而 {@code IGeoRenderer#createVerticesOfQuad}
     * 调用的那个多参重载最终一定会落到它上面。</p>
     */
    private static final class BoundsConsumer implements VertexConsumer {
        private float minX = Float.MAX_VALUE;
        private float minY = Float.MAX_VALUE;
        private float minZ = Float.MAX_VALUE;
        private float maxX = -Float.MAX_VALUE;
        private float maxY = -Float.MAX_VALUE;
        private float maxZ = -Float.MAX_VALUE;
        private boolean any = false;

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            this.any = true;
            this.minX = (float) Math.min(this.minX, x);
            this.minY = (float) Math.min(this.minY, y);
            this.minZ = (float) Math.min(this.minZ, z);
            this.maxX = (float) Math.max(this.maxX, x);
            this.maxY = (float) Math.max(this.maxY, y);
            this.maxZ = (float) Math.max(this.maxZ, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unsetDefaultColor() {
        }

        float[] toArray() {
            if (!this.any) {
                return new float[]{0, 0, 0, 0, 0, 0};
            }
            return new float[]{this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ};
        }
    }
}

