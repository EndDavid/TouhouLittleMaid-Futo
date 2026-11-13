package com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * 把 {@link AnimatedGeoModel} 当静态模型画出来的最小渲染器。
 *
 * <p>{@link IGeoRenderer} 的方法几乎全是 {@code default}，这里只需要提供
 * 「当前 buffer」和「纹理」两个查询接口，其余（逐骨骼矩阵、逐面顶点、
 * 法线与光照打包）全部复用实体那条已经调好的实现 —— 这一点很关键，
 * 那些面法线的处理在实体渲染上已经被反复验证过（见
 * {@code IGeoRenderer#renderCube} 的注释），重写一遍只会引入新 bug。</p>
 *
 * <p>「动画对象」这个类型参数在这里就是纹理本身：没有实体、没有状态，
 * 渲染器只需要知道用哪张贴图。</p>
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
}
