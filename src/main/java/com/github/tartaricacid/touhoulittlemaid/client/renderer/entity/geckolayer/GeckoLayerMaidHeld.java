package com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.geckolayer;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.processor.ILocationBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 女仆手持物品渲染层。
 *
 * <p>数值全部来自上游：基岩模型是 {@code translate(0, -0.0625, -0.1) + rotX(-90)}，
 * 旧版模型是 {@code rotX(-90) + rotY(180) + translate((左?-1:1)/16, 0.125, -0.525)}。
 * 本移植版的 GeckoLib 空间与上游一致，所以<b>两个分支最后都落在 {@code rotX(-90)}</b>：
 * 把符号改成 +90、或把 {@code translate.x} 取反，会让剑拿反 / 盾转向身体 / 弩朝下
 * （那两步合起来等于给整件物品再加一个 180° 翻转）。</p>
 *
 * <p>盾 / 弓 / 弩不需要特判：它们走原版 {@code ItemInHandRenderer}，显示变换由 JSON 提供。</p>
 */


public class GeckoLayerMaidHeld<T extends Mob, R extends IGeoEntityRenderer<T>> extends GeoLayerRenderer<T, R> {
    /**
     * 旧版模型的右手/左手骨骼名（改名后）。
     *
     * <p>上游 {@code BedrockModel#getArm} 里是原名字面量 {@code "armLeft"} / {@code "armRight"}；
     * 我们加载时已改名，所以这里用 {@code LeftArm} / {@code RightArm}。</p>
     */
    private static final String LEGACY_LEFT_ARM = "LeftArm";
    private static final String LEGACY_RIGHT_ARM = "RightArm";


    private final ItemInHandRenderer itemInHandRenderer;

    public GeckoLayerMaidHeld(R entityRendererIn) {
        super(entityRendererIn);
        this.itemInHandRenderer = Minecraft.getInstance().getItemInHandRenderer();
    }

    @Override
    public GeoLayerRenderer<T, R> copy(R entityRendererIn) {
        return new GeckoLayerMaidHeld<>(entityRendererIn);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       T entity, float limbSwing, float limbSwingAmount, float partialTicks,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack offhandItem = entity.getOffhandItem();
        ItemStack mainHandItem = entity.getMainHandItem();
        if (offhandItem.isEmpty() && mainHandItem.isEmpty()) {
            return;
        }
        ILocationModel geoModel = getLocationModel(entity);
        if (geoModel == null) {
            return;
        }

        poseStack.pushPose();
        List<? extends ILocationBone> rightHand = rightHandHierarchy(geoModel);
        if (!rightHand.isEmpty()) {
            this.renderArmWithItem(entity, mainHandItem, geoModel, rightHand,
                    ItemTransforms.TransformType.THIRD_PERSON_RIGHT_HAND, HumanoidArm.RIGHT,
                    poseStack, bufferSource, packedLight);
        }
        List<? extends ILocationBone> leftHand = leftHandHierarchy(geoModel);
        if (!leftHand.isEmpty()) {
            this.renderArmWithItem(entity, offhandItem, geoModel, leftHand,
                    ItemTransforms.TransformType.THIRD_PERSON_LEFT_HAND, HumanoidArm.LEFT,
                    poseStack, bufferSource, packedLight);
        }
        poseStack.popPose();
    }

    /**
     * 右手定位链。
     *
     * <p>优先用基岩模型的 {@code RightHandLocator}；旧版模型没有这个骨骼，
     * 退到 {@code RightArm}（见类注释）。</p>
     */
    private List<? extends ILocationBone> rightHandHierarchy(ILocationModel geoModel) {
        List<? extends ILocationBone> locator = geoModel.rightHandBones();
        if (!locator.isEmpty()) {
            return locator;
        }
        return geoModel.boneHierarchy(LEGACY_RIGHT_ARM);
    }

    private List<? extends ILocationBone> leftHandHierarchy(ILocationModel geoModel) {
        List<? extends ILocationBone> locator = geoModel.leftHandBones();
        if (!locator.isEmpty()) {
            return locator;
        }
        return geoModel.boneHierarchy(LEGACY_LEFT_ARM);
    }

    /**
     * 是否走基岩（Gecko）分支 —— 判据就是「有没有 {@code *HandLocator}」，
     * 因为旧版模型不可能有它（骨骼表已核实）。
     */
    private boolean isLocatorBased(ILocationModel geoModel, HumanoidArm arm) {
        return arm == HumanoidArm.LEFT ? !geoModel.leftHandBones().isEmpty() : !geoModel.rightHandBones().isEmpty();
    }

    protected void renderArmWithItem(T entity, ItemStack itemStack, ILocationModel geoModel,
                                     List<? extends ILocationBone> hierarchy,
                                     ItemTransforms.TransformType displayContext, HumanoidArm arm,
                                     PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        if (itemStack.isEmpty()) {
            return;
        }
        boolean isLeftHand = arm == HumanoidArm.LEFT;

        poseStack.pushPose();
        boolean scaleIsZero = RenderUtils.prepMatrixForLocator(poseStack, hierarchy);
        // 缩放为 0 说明该骨骼被隐藏了（上游注释：「缩放不为 0 才会渲染」）
        if (!scaleIsZero) {
            if (isLocatorBased(geoModel, arm)) {
                // 基岩分支 —— 上游 GeckoLayerMaidHeld#renderArmWithItem（1.18.2 第 72~75 行 /
                // 1.20.1 第 72~74 行）原样照抄。上游与本移植版跑的是同一份 geckolib3 代码，
                // 手部定位骨骼所在的坐标系完全一致，因此这里一个数都不能改：
                //     translate(0, -0.0625, -0.1);
                //     mulPose(rotX(-90));
                poseStack.translate(0, -0.0625, -0.1);
                poseStack.mulPose(Vector3f.XP.rotationDegrees(-90.0F));
            } else {
                // 旧版分支 —— 上游 LayerMaidHeldItem#renderArmWithItem（1.18.2 第 56~58 行 /
                // 1.20.1 第 59~61 行）的三行常量，前面套一层坐标基底变换
                //     C = diag(-1, -1, 1) = rotZ(180)
                // （本移植版把旧版模型放进了 GeckoLib 的 Y 向上空间，见类注释）。
                // 保留 C 与上游三行并列的写法，方便逐行对照；化简结果就是 rotX(-90) + translate。
                poseStack.mulPose(Vector3f.ZP.rotationDegrees(180.0F));   // C
                poseStack.mulPose(Vector3f.XP.rotationDegrees(-90.0F));   // 上游：rotX(-90)
                poseStack.mulPose(Vector3f.YP.rotationDegrees(180.0F));   // 上游：rotY(180)
                poseStack.translate((isLeftHand ? -1 : 1) / 16.0, 0.125, -0.525); // 上游：同上
            }

            this.itemInHandRenderer.renderItem(entity, itemStack, displayContext, isLeftHand,
                    poseStack, bufferSource, light);
        }
        poseStack.popPose();
    }
}

