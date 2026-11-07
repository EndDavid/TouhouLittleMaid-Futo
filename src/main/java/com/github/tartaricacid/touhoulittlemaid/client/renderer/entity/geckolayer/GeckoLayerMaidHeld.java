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
 * <p>本类的变换全部来自上游源码，没有一处是估的。三条依据：</p>
 *
 * <ol>
 *     <li><b>基岩（Gecko）模型。</b>上游 1.18.2 {@code GeckoLayerMaidHeld#renderArmWithItem}
 *         （第 72~75 行）与 1.20.1 同名类（第 72~74 行）都是：
 *         <pre>translate(0, -0.0625, -0.1);  mulPose(rotX(-90))</pre></li>
 *     <li><b>旧版（1.10 / 1.12）模型。</b>上游 1.18.2 {@code LayerMaidHeldItem#renderArmWithItem}
 *         （第 56~58 行）与 1.20.1 同名类（第 59~61 行）都是：
 *         <pre>rotX(-90);  rotY(180);  translate((isLeft ? -1 : 1) / 16, 0.125, -0.525)</pre>
 *         这三行就是原版 {@code ItemInHandLayer#renderArmWithItem} 的数值 —— 1.18.2 的 jar 反编译
 *         核对过，原版是 {@code rotX(-90)} / {@code rotY(180)} /
 *         {@code translate((flag ? -1 : 1) / 16f, 0.125, -0.625)}，上游只把末项改成 -0.525。</li>
 *     <li><b>盾 / 弓 / 弩不需要任何特判。</b>它们最终都走
 *         {@code ItemInHandRenderer#renderItem(..., THIRD_PERSON_RIGHT/LEFT_HAND)}，和玩家手持
 *         完全是同一条原版路径；「弩斜着摆开」「弓居中且弦朝身体」「盾面向外」这些姿态由物品模型
 *         JSON 里的 {@code thirdperson_*} 显示变换提供（原版 {@code ItemInHandLayer} 也不认识
 *         盾 / 弓 / 弩）。所以只要手部基准位姿和玩家一致，这三类物品自然就和玩家一致 ——
 *         反过来，只要基准位姿错了，它们就会表现得比普通物品更显眼。</li>
 * </ol>
 *
 * <h2>基岩分支为什么不能做「X 镜像共轭」</h2>
 *
 * <p>本移植版的 {@code geckolib3} 是从上游原样搬过来的：{@code util/RenderUtils.java}、
 * {@code geo/render/GeoBuilder.java} 与上游 1.18.2 逐字节相同，{@code geo/render/built/GeoCube.java}
 * 只差一个空行。也就是说手部定位骨骼链 {@code Root → … → RightHandLocator} 的整套变换
 * （{@code GeoBuilder} 对 pivot.x 取反、对绕 X/Y 的旋转取反，{@code GeoCube} 对 cube 的 x 取反）
 * 在两边是<b>同一个空间</b>。上游在这个空间里直接写 {@code rotX(-90)}，这里就必须照抄，
 * 不需要也不允许再乘任何补偿矩阵。</p>
 *
 * <p>把 {@code rotX(-90)} 改成 {@code rotX(+90)}，等价于在物品自己的局部坐标系里再乘一个
 * {@code rotX(180)}，于是：剑变成「拿反」（刃朝身后）、盾的外侧面转向身体（里外面反）、
 * 弩被翻成朝下（看上去「张开的幅度不够」）。<b>这三个症状同源，不是三个独立的问题。</b></p>
 *
 * <h2>旧版分支为什么要多一层 {@code rotZ(180)}</h2>
 *
 * <p>上游旧版模型用的是 {@code simplebedrockmodel}，它的模型空间是「Java 版」的 Y 轴向下空间：
 * {@code AbstractBedrockModel#convertPivot} 用 {@code 24 - pivot.y}（子骨骼用
 * {@code parent.pivot.y - pivot.y}），{@code convertOrigin} 用
 * {@code pivot.y - origin.y - size.y}。上游随后由原版 {@code LivingEntityRenderer#render} 里的
 * {@code poseStack.scale(-1, -1, 1)}（1.18.2 jar 反编译确认，紧接着还有
 * {@code translate(0, -1.501, 0)}）把它转进世界空间，这个 {@code scale} 就是 {@code rotZ(180)}。</p>
 *
 * <p>本移植版把旧版模型也直接喂进了 GeckoLib 管线（Y 轴向上，且 {@code GeoBuilder} /
 * {@code GeoCube} 做了 X 镜像）。两条管线因此相差一个<b>坐标基底</b>变换（不是镜像，行列式为 +1）：</p>
 * <pre>
 * C = diag(-1, -1, 1) = rotZ(180)        端口空间 = C · 上游模型空间
 * </pre>
 *
 * <p>同一个骨骼在端口里的局部坐标系相对上游被旋转了 C，所以要把物品放到世界上同样的位置，
 * 物品变换必须写成 {@code C · T_up}（{@code C} 自逆）：</p>
 * <pre>
 * C · rotX(-90) · rotY(180) ≡ rotX(-90)      ← 可直接验算，rotY(180) 正好被 C 吸收
 * C · translate(v)          ≡ translate(v)
 * </pre>
 *
 * <p>下面代码保留「{@code C} + 上游三行原样」的写法，方便一行一行对照上游；它的化简结果就是
 * {@code rotX(-90) + translate}，所以两个分支最后都落在 {@code rotX(-90)} 上，只有平移量不同。
 * 这也解释了此前把 {@code rotX} 改成 {@code +90}、把 {@code translate.x} 取反为什么会同时
 * 打坏普通物品：那两步合起来是给整件物品又加了一个 180° 翻转。</p>
 *
 * <p>未移植的上游分支：{@code SlashBladeCompat}（拔刀剑）、{@code RenderFixer.isCarryOnRender}
 * （CarryOn）、{@code TacCompat.addItemTranslate}（TACZ）、{@code extraLeft/RightHandBones}
 * （同一物品在额外手部骨骼上重绘）、{@code armLeft/RightPositioningBone}（现有 189 个模型里
 * 没有一个用到这个字段）。</p>
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

